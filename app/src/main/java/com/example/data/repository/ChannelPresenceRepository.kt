package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class OnlineUserSession(
    val sessionId: String = "",
    val channelId: String = "",
    val channel: String = "الرئيسية (التطبيق)",
    val timestamp: Long = System.currentTimeMillis(),
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" },
    val appVersion: String = "4.0.1"
)

class ChannelPresenceRepository(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "ChannelPresenceRepo"
        private const val PREFS_NAME = "presence_prefs"
        private const val KEY_SESSION_ID = "device_session_id"
        private const val APP_ONLINE_USERS_NODE = "v4_online_users"
        private const val CHANNEL_PRESENCE_NODE = "v4_channel_presence"
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance(com.example.data.security.AppSecurityGuard.FIREBASE_RTDB_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firebase instance: ${e.message}")
            FirebaseDatabase.getInstance()
        }
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    val sessionId: String by lazy {
        val sp = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = sp?.getString(KEY_SESSION_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").take(12)
            sp?.edit()?.putString(KEY_SESSION_ID, id)?.apply()
        }
        id
    }

    private val deviceModel: String by lazy {
        try {
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim().ifBlank { "Android" }
        } catch (_: Exception) {
            "Android"
        }
    }

    private var activeChannelId: String? = null
    private var activeChannelName: String = "الرئيسية (تصفح التطبيق)"
    private var connectedListener: ValueEventListener? = null

    /**
     * Ensures the device has a valid Firebase Auth session (anonymous or admin)
     * so `auth != null` RTDB rules allow real-time presence writes.
     */
    private suspend fun ensurePresenceAuth() {
        try {
            val auth = firebaseAuth ?: return
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Presence auth notice: ${e.message}")
        }
    }

    /**
     * Starts real-time app-wide presence tracking using Firebase `.info/connected` and `onDisconnect()`.
     * Automatically registers this user when online and removes them the instant they close the app or lose connection.
     */
    fun startAppPresence(appVersion: String = "4.0.1") {
        repoScope.launch {
            ensurePresenceAuth()
            try {
                val connectedRef = database.getReference(".info/connected")
                val userSessionRef = database.getReference(APP_ONLINE_USERS_NODE).child(sessionId)

                connectedListener?.let { connectedRef.removeEventListener(it) }
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            repoScope.launch {
                                ensurePresenceAuth()
                                try {
                                    userSessionRef.onDisconnect().removeValue()
                                    val data = mapOf(
                                        "sessionId" to sessionId,
                                        "channelId" to (activeChannelId ?: ""),
                                        "channel" to activeChannelName,
                                        "timestamp" to ServerValue.TIMESTAMP,
                                        "device" to deviceModel,
                                        "appVersion" to appVersion
                                    )
                                    userSessionRef.setValue(data)

                                    // Also re-register active channel presence if watching a channel
                                    val currentChId = activeChannelId
                                    if (!currentChId.isNullOrBlank()) {
                                        val chRef = database.getReference(CHANNEL_PRESENCE_NODE)
                                            .child(currentChId)
                                            .child(sessionId)
                                        chRef.onDisconnect().removeValue()
                                        chRef.setValue(
                                            mapOf(
                                                "sessionId" to sessionId,
                                                "channel" to activeChannelName,
                                                "timestamp" to ServerValue.TIMESTAMP,
                                                "device" to deviceModel
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed writing app presence: ${e.message}")
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(TAG, "Connected listener cancelled: ${error.message}")
                    }
                }
                connectedListener = listener
                connectedRef.addValueEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error starting app presence: ${e.message}")
            }
        }
    }

    fun startWatchingChannel(channelId: String, channelName: String = "") {
        if (channelId.isBlank()) return
        val previousChannelId = activeChannelId
        activeChannelId = channelId
        activeChannelName = channelName.ifBlank { "مشاهدة قناة مباشرة" }

        repoScope.launch {
            ensurePresenceAuth()
            try {
                if (!previousChannelId.isNullOrBlank() && previousChannelId != channelId) {
                    database.getReference(CHANNEL_PRESENCE_NODE)
                        .child(previousChannelId)
                        .child(sessionId)
                        .removeValue()
                }

                val chRef = database.getReference(CHANNEL_PRESENCE_NODE)
                    .child(channelId)
                    .child(sessionId)
                chRef.onDisconnect().removeValue()
                chRef.setValue(
                    mapOf(
                        "sessionId" to sessionId,
                        "channel" to activeChannelName,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "device" to deviceModel
                    )
                )

                val userSessionRef = database.getReference(APP_ONLINE_USERS_NODE).child(sessionId)
                userSessionRef.onDisconnect().removeValue()
                userSessionRef.updateChildren(
                    mapOf(
                        "sessionId" to sessionId,
                        "channelId" to channelId,
                        "channel" to activeChannelName,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "device" to deviceModel
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating channel presence: ${e.message}")
            }
        }
    }

    fun stopWatchingCurrentChannel() {
        val prevId = activeChannelId
        activeChannelId = null
        activeChannelName = "الرئيسية (تصفح التطبيق)"

        repoScope.launch {
            try {
                if (!prevId.isNullOrBlank()) {
                    database.getReference(CHANNEL_PRESENCE_NODE)
                        .child(prevId)
                        .child(sessionId)
                        .removeValue()
                }
                val userSessionRef = database.getReference(APP_ONLINE_USERS_NODE).child(sessionId)
                userSessionRef.updateChildren(
                    mapOf(
                        "channelId" to "",
                        "channel" to activeChannelName,
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Observes real-time online viewers count for a specific channel.
     */
    fun getOnlineViewersFlow(channelId: String): Flow<Int> = callbackFlow {
        if (channelId.isBlank()) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val channelPresenceRef = database.getReference(CHANNEL_PRESENCE_NODE).child(channelId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val realCount = snapshot.childrenCount.toInt()
                val displayCount = if (activeChannelId == channelId) maxOf(1, realCount) else realCount
                trySend(displayCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled: ${error.message}")
                trySend(1)
            }
        }

        channelPresenceRef.addValueEventListener(listener)

        awaitClose {
            channelPresenceRef.removeEventListener(listener)
        }
    }

    /**
     * Observes all real-time active users currently inside the app (`v4_online_users` + fallback from `v4_channel_presence`).
     */
    fun getGlobalOnlineUsersFlow(): Flow<List<OnlineUserSession>> = callbackFlow {
        val onlineUsersRef = database.getReference(APP_ONLINE_USERS_NODE)
        val channelPresenceRef = database.getReference(CHANNEL_PRESENCE_NODE)

        var globalSessions = listOf<OnlineUserSession>()
        var channelSessions = listOf<OnlineUserSession>()

        fun emitMerged() {
            val map = linkedMapOf<String, OnlineUserSession>()
            for (s in globalSessions) {
                if (s.sessionId.isNotBlank()) {
                    map[s.sessionId] = s
                }
            }
            for (c in channelSessions) {
                if (c.sessionId.isNotBlank()) {
                    val existing = map[c.sessionId]
                    if (existing == null || existing.channelId.isBlank()) {
                        map[c.sessionId] = c
                    }
                }
            }
            // Always ensure the current active device session is included while connected
            if (!map.containsKey(sessionId)) {
                map[sessionId] = OnlineUserSession(
                    sessionId = sessionId,
                    channelId = activeChannelId ?: "",
                    channel = activeChannelName,
                    timestamp = System.currentTimeMillis(),
                    device = deviceModel
                )
            }
            trySend(map.values.sortedByDescending { it.timestamp })
        }

        val globalListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<OnlineUserSession>()
                for (child in snapshot.children) {
                    try {
                        val sid = child.child("sessionId").getValue(String::class.java) ?: child.key ?: ""
                        val chId = child.child("channelId").getValue(String::class.java) ?: ""
                        val chName = child.child("channel").getValue(String::class.java) ?: "الرئيسية (تصفح التطبيق)"
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        val dev = child.child("device").getValue(String::class.java) ?: "Android"
                        val ver = child.child("appVersion").getValue(String::class.java) ?: "4.0.1"
                        if (sid.isNotBlank()) {
                            list.add(
                                OnlineUserSession(
                                    sessionId = sid,
                                    channelId = chId,
                                    channel = chName,
                                    timestamp = ts,
                                    device = dev,
                                    appVersion = ver
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
                globalSessions = list
                emitMerged()
            }

            override fun onCancelled(error: DatabaseError) {
                emitMerged()
            }
        }

        val channelListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<OnlineUserSession>()
                for (chSnapshot in snapshot.children) {
                    val chId = chSnapshot.key ?: ""
                    for (userSnap in chSnapshot.children) {
                        try {
                            val sid = userSnap.child("sessionId").getValue(String::class.java) ?: userSnap.key ?: ""
                            val chName = userSnap.child("channel").getValue(String::class.java) ?: chId
                            val ts = userSnap.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                            val dev = userSnap.child("device").getValue(String::class.java) ?: "Android"
                            if (sid.isNotBlank()) {
                                list.add(
                                    OnlineUserSession(
                                        sessionId = sid,
                                        channelId = chId,
                                        channel = chName,
                                        timestamp = ts,
                                        device = dev
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
                channelSessions = list
                emitMerged()
            }

            override fun onCancelled(error: DatabaseError) {
                emitMerged()
            }
        }

        onlineUsersRef.addValueEventListener(globalListener)
        channelPresenceRef.addValueEventListener(channelListener)

        awaitClose {
            onlineUsersRef.removeEventListener(globalListener)
            channelPresenceRef.removeEventListener(channelListener)
        }
    }
}
