package com.example.ui.publisher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Channel
import com.example.data.model.ChannelCategory
import com.example.data.model.ChannelServer
import com.example.data.model.XtremeAccount
import com.example.data.util.ChannelClusteringEngine
import com.example.data.util.M3uParser
import com.example.data.xtreme.XtremeApiClient
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BrightLavender
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ElectricGold
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LiveRed
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PitchGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet
import kotlinx.coroutines.launch

private enum class CategorySourceMode(val label: String) {
    NONE("بدون مصدر (يدوي)"),
    M3U("رابط قائمة M3U"),
    XTREME("حساب Xtream Codes")
}

/**
 * Tab 1: Classic, clean developer control panel tab for managing and publishing Categories (الأقسام).
 * Supports manual categories, M3U playlists, and Xtream accounts with classifications & quality merge options.
 */
@Composable
fun CategoryPublisherTab(
    categories: List<ChannelCategory>,
    xtremeAccounts: List<XtremeAccount> = emptyList(),
    onSaveCategory: (ChannelCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveChannel: (Channel) -> Unit = {},
    onSaveBatchChannels: (List<Channel>, String) -> Unit = { _, _ -> },
    onPlayChannelPreview: (Channel, ChannelServer?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val xtremeClient = remember { XtremeApiClient() }

    var editingId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var orderText by remember { mutableStateOf("0") }
    var sourceMode by remember { mutableStateOf(CategorySourceMode.XTREME) }

    // M3U Source
    var m3uUrl by remember { mutableStateOf("") }

    // Xtreme Source credentials
    var xtremeHost by remember { mutableStateOf("") }
    var xtremeUsername by remember { mutableStateOf("") }
    var xtremePassword by remember { mutableStateOf("") }
    var xtremeExtension by remember { mutableStateOf("m3u8") }

    // Xtreme Classifications / Categories fetched from the Xtreme server
    var xtremeCategoriesList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedXtremeCategoryId by remember { mutableStateOf("") }
    var selectedXtremeCategoryName by remember { mutableStateOf("") }
    var isFetchingCategories by remember { mutableStateOf(false) }
    var categoriesFetchError by remember { mutableStateOf<String?>(null) }

    // Quality Merging Choice: (دمج الجودات كسيرفرات في الأعلى) vs (فقط قناة من دون دمج)
    var isMergedQualitiesMode by remember { mutableStateOf(true) }

    // Import / Fetch status feedback
    var isImportingChannels by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    // Loads classifications/categories from the Xtreme account
    fun loadXtremeCategories(host: String, user: String, pass: String) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            categoriesFetchError = "يرجى إدخال بيانات السيرفر واسم المستخدم وكلمة المرور أولاً"
            return
        }
        coroutineScope.launch {
            isFetchingCategories = true
            categoriesFetchError = null
            try {
                val tempAcc = XtremeAccount(
                    name = "حساب Xtreme",
                    host = host.trim(),
                    username = user.trim(),
                    password = pass.trim()
                )
                val list = xtremeClient.fetchLiveCategoriesList(tempAcc)
                xtremeCategoriesList = list
                if (list.isEmpty()) {
                    categoriesFetchError = "لم يتم العثور على تصنيفات أو تعذر الاتصال بالسيرفر. تأكد من البيانات."
                }
            } catch (e: Exception) {
                categoriesFetchError = "فشل جلب التصنيفات: ${e.localizedMessage ?: "تأكد من الاتصال"}"
            } finally {
                isFetchingCategories = false
            }
        }
    }

    // Fetches and publishes channels directly for the selected category & merge mode
    fun fetchAndPublishChannelsForCategory() {
        val targetCategoryName = name.trim().ifBlank { selectedXtremeCategoryName }.ifBlank { "عام" }
        coroutineScope.launch {
            isImportingChannels = true
            importStatusMessage = null
            importErrorMessage = null
            try {
                when (sourceMode) {
                    CategorySourceMode.XTREME -> {
                        if (xtremeHost.isBlank() || xtremeUsername.isBlank() || xtremePassword.isBlank()) {
                            importErrorMessage = "يرجى إدخال بيانات حساب Xtream أولاً"
                        } else {
                            val tempAcc = XtremeAccount(
                                name = targetCategoryName,
                                host = xtremeHost.trim(),
                                username = xtremeUsername.trim(),
                                password = xtremePassword.trim(),
                                streamExtension = xtremeExtension.ifBlank { "m3u8" }
                            )
                            val liveItems = xtremeClient.fetchLiveStreams(tempAcc, selectedXtremeCategoryId)
                            if (liveItems.isEmpty()) {
                                importErrorMessage = "لم يتم العثور على قنوات في هذا التصنيف"
                            } else {
                                val channelsToSave = if (isMergedQualitiesMode) {
                                    ChannelClusteringEngine.clusterXtremeLiveItems(liveItems, targetCategoryName)
                                } else {
                                    ChannelClusteringEngine.toUnmergedChannels(liveItems, targetCategoryName)
                                }
                                onSaveBatchChannels(channelsToSave, targetCategoryName)
                                importStatusMessage = "تم بنجاح جلب ونشر (${channelsToSave.size} قناة) في قسم ($targetCategoryName) أونلاين!"
                            }
                        }
                    }
                    CategorySourceMode.M3U -> {
                        if (m3uUrl.isBlank()) {
                            importErrorMessage = "يرجى إدخال رابط M3U أولاً"
                        } else {
                            val parsed = M3uParser.parse(m3uUrl.trim(), targetCategoryName)
                            if (parsed.isEmpty()) {
                                importErrorMessage = "لم يتم العثور على قنوات في رابط M3U"
                            } else {
                                val channelsToSave = if (isMergedQualitiesMode) {
                                    parsed
                                } else {
                                    parsed.flatMap { ch ->
                                        if (ch.servers.size <= 1) listOf(ch)
                                        else ch.servers.mapIndexed { idx, srv ->
                                            ch.copy(
                                                id = "${ch.id}_s_${idx + 1}",
                                                name = "${ch.name} (${srv.name})",
                                                servers = listOf(srv)
                                            )
                                        }
                                    }
                                }
                                onSaveBatchChannels(channelsToSave, targetCategoryName)
                                importStatusMessage = "تم بنجاح جلب ونشر (${channelsToSave.size} قناة) من رابط M3U أونلاين!"
                            }
                        }
                    }
                    CategorySourceMode.NONE -> {
                        importErrorMessage = "القسم اليدوي لا يحتوي على رابط لجلب القنوات، يمكنك إضافة القنوات إليه من تبويب القنوات"
                    }
                }
            } catch (e: Exception) {
                importErrorMessage = "خطأ أثناء جلب القنوات: ${e.localizedMessage ?: "تأكد من الاتصال"}"
            } finally {
                isImportingChannels = false
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // =========================================================================
        // 1. CATEGORY FORM CARD (اضافة / تعديل القسم)
        // =========================================================================
        item {
            Surface(
                color = CardSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PitchGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingId.isBlank()) "إضافة قسم جديد في التطبيق" else "تعديل القسم: $name",
                            color = ElectricGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Surface(
                            color = PitchGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, PitchGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "إدارة الأقسام",
                                color = PitchGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم القسم (مثال: beIN SPORTS أو قنوات وثائقية)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PitchGreen,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("category_name_input")
                        )

                        OutlinedTextField(
                            value = orderText,
                            onValueChange = { orderText = it },
                            label = { Text("الترتيب") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SOURCE MODE SELECTOR
                    Text(
                        text = "مصدر القنوات لهذا القسم:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategorySourceMode.values().forEach { mode ->
                            val isSelected = sourceMode == mode
                            val icon = when (mode) {
                                CategorySourceMode.NONE -> Icons.Default.Category
                                CategorySourceMode.M3U -> Icons.Default.Link
                                CategorySourceMode.XTREME -> Icons.Default.Dns
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) PitchGreen.copy(alpha = 0.22f) else CardSurface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PitchGreen else TextMuted.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { sourceMode = mode }
                                    .testTag("category_source_mode_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) PitchGreen else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // SOURCE MODE 1: NONE (MANUAL)
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.NONE) {
                        Surface(
                            color = CardSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = "سيتم إنشاء هذا القسم يدوياً بدون رابط، ويمكنك إضافة وتعديل القنوات التابعة له مباشرة من تبويب القنوات.",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // SOURCE MODE 2: M3U PLAYLIST URL
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.M3U) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = m3uUrl,
                                onValueChange = { m3uUrl = it },
                                label = { Text("رابط قائمة M3U للقسم") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_m3u_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quality Merging Toggle for M3U
                            Text(
                                text = "خيارات جودات القنوات والسيرفرات:",
                                color = ElectricGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isMergedQualitiesMode) PitchGreen.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (isMergedQualitiesMode) PitchGreen else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { isMergedQualitiesMode = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = if (isMergedQualitiesMode) PitchGreen else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "دمج الجودات كسيرفرات في الأعلى",
                                            color = if (isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isMergedQualitiesMode) AccentCyan.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (!isMergedQualitiesMode) AccentCyan else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { isMergedQualitiesMode = false }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = if (!isMergedQualitiesMode) AccentCyan else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "فقط قناة من دون دمج (منفصلة)",
                                            color = if (!isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { fetchAndPublishChannelsForCategory() },
                                border = BorderStroke(1.dp, AccentCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isImportingChannels) {
                                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("جاري جلب القنوات...", color = AccentCyan)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isMergedQualitiesMode) "جلب قنوات M3U ودمج الجودات كسيرفرات ونشرها" else "جلب قنوات M3U بدون دمج ونشرها",
                                        color = AccentCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // SOURCE MODE 3: XTREME CODES ACCOUNT & CLASSIFICATIONS
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.XTREME) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Quick fill from existing Xtreme accounts
                            if (xtremeAccounts.isNotEmpty()) {
                                Text(
                                    text = "اختيار سريع من الحسابات المحفوظة:",
                                    color = BrightLavender,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(xtremeAccounts, key = { it.id }) { acc ->
                                        val isCurrentAcc = xtremeHost == acc.host && xtremeUsername == acc.username
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrentAcc) PitchGreen.copy(alpha = 0.22f) else CardSurface,
                                            border = BorderStroke(1.dp, if (isCurrentAcc) PitchGreen else TextMuted.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    xtremeHost = acc.host
                                                    xtremeUsername = acc.username
                                                    xtremePassword = acc.password
                                                    xtremeExtension = acc.streamExtension.ifBlank { "m3u8" }
                                                    if (name.isBlank()) name = acc.name
                                                    loadXtremeCategories(acc.host, acc.username, acc.password)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Dns, contentDescription = null, tint = ElectricGold, modifier = Modifier.size(13.dp))
                                                Text(acc.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedTextField(
                                value = xtremeHost,
                                onValueChange = { xtremeHost = it },
                                label = { Text("عنوان سيرفر Xtreme (مثال: http://server.com:8080)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricGold,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_xtreme_host_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xtremeUsername,
                                    onValueChange = { xtremeUsername = it },
                                    label = { Text("اسم المستخدم (Username)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricGold,
                                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("category_xtreme_user_input")
                                )

                                OutlinedTextField(
                                    value = xtremePassword,
                                    onValueChange = { xtremePassword = it },
                                    label = { Text("كلمة المرور (Password)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricGold,
                                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("category_xtreme_pass_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("امتداد البث:", color = TextSecondary, fontSize = 11.5.sp)
                                listOf("m3u8", "ts").forEach { ext ->
                                    val selected = xtremeExtension.equals(ext, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) AccentCyan.copy(alpha = 0.22f) else CardSurface,
                                        border = BorderStroke(1.dp, if (selected) AccentCyan else TextMuted.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable { xtremeExtension = ext }
                                    ) {
                                        Text(
                                            text = ext.uppercase(),
                                            color = if (selected) AccentCyan else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. BUTTON TO FETCH CLASSIFICATIONS/CATEGORIES FROM XTREME
                            OutlinedButton(
                                onClick = {
                                    loadXtremeCategories(xtremeHost, xtremeUsername, xtremePassword)
                                },
                                border = BorderStroke(1.dp, ElectricGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_xtreme_categories_button")
                            ) {
                                if (isFetchingCategories) {
                                    CircularProgressIndicator(color = ElectricGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("جاري جلب التصنيفات من السيرفر...", color = ElectricGold, fontSize = 11.5.sp)
                                } else {
                                    Icon(Icons.Default.Category, contentDescription = null, tint = ElectricGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (xtremeCategoriesList.isNotEmpty()) "تحديث تصنيفات Xtreme (${xtremeCategoriesList.size})" else "جلب أقسام وتصنيفات حساب Xtreme",
                                        color = ElectricGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            if (!categoriesFetchError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = categoriesFetchError ?: "",
                                    color = LiveRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 2. DISPLAY FETCHED XTREME CATEGORIES / CLASSIFICATIONS
                            if (xtremeCategoriesList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "اختر التصنيف / القسم المراد ربطه ونشره (${xtremeCategoriesList.size} تصنيف):",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        val isAllSelected = selectedXtremeCategoryId.isBlank()
                                        FilterChip(
                                            selected = isAllSelected,
                                            onClick = {
                                                selectedXtremeCategoryId = ""
                                                selectedXtremeCategoryName = "جميع التصنيفات"
                                            },
                                            label = {
                                                Text(
                                                    text = "جميع التصنيفات",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isAllSelected) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (isAllSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ElectricGold.copy(alpha = 0.22f),
                                                selectedLabelColor = ElectricGold,
                                                selectedLeadingIconColor = ElectricGold
                                            )
                                        )
                                    }

                                    items(xtremeCategoriesList, key = { it.first }) { (catId, catTitle) ->
                                        val isSelected = selectedXtremeCategoryId == catId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedXtremeCategoryId = catId
                                                selectedXtremeCategoryName = catTitle
                                                if (name.isBlank() || xtremeCategoriesList.any { it.second == name.trim() }) {
                                                    name = catTitle
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = catTitle,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PitchGreen.copy(alpha = 0.22f),
                                                selectedLabelColor = PitchGreen,
                                                selectedLeadingIconColor = PitchGreen
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 3. QUALITY MERGING TOGGLE
                            Text(
                                text = "خيارات جودات القنوات والسيرفرات عند النشر:",
                                color = ElectricGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isMergedQualitiesMode) PitchGreen.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (isMergedQualitiesMode) PitchGreen else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { isMergedQualitiesMode = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = if (isMergedQualitiesMode) PitchGreen else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "دمج الجودات كسيرفرات في الأعلى",
                                            color = if (isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isMergedQualitiesMode) AccentCyan.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (!isMergedQualitiesMode) AccentCyan else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { isMergedQualitiesMode = false }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = if (!isMergedQualitiesMode) AccentCyan else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "فقط قناة من دون دمج (منفصلة)",
                                            color = if (!isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4. FETCH & PUBLISH CHANNELS FOR SELECTED CATEGORY BUTTON
                            Button(
                                onClick = { fetchAndPublishChannelsForCategory() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VividViolet,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_xtreme_category_channels_button")
                            ) {
                                if (isImportingChannels) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("جاري جلب ونشر القنوات أونلاين...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val catTitle = if (selectedXtremeCategoryName.isNotBlank()) "تصنيف ($selectedXtremeCategoryName)" else "كامل الحساب"
                                    Text(
                                        text = if (isMergedQualitiesMode) "جلب ونشر قنوات $catTitle مع دمج الجودات كسيرفرات" else "جلب ونشر قنوات $catTitle بدون دمج",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // FEEDBACK MESSAGES
                    if (!importStatusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PitchGreen.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, PitchGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PitchGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = importStatusMessage ?: "",
                                    color = PitchGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (!importErrorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LiveRed.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, LiveRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = importErrorMessage ?: "",
                                color = LiveRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SAVE / CANCEL CATEGORY BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingId.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    editingId = ""
                                    name = ""
                                    orderText = "0"
                                    sourceMode = CategorySourceMode.XTREME
                                    m3uUrl = ""
                                    xtremeHost = ""
                                    xtremeUsername = ""
                                    xtremePassword = ""
                                    xtremeExtension = "m3u8"
                                    selectedXtremeCategoryId = ""
                                    selectedXtremeCategoryName = ""
                                    importStatusMessage = null
                                    importErrorMessage = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء التعديل", color = TextSecondary)
                            }
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    val finalM3u = if (sourceMode == CategorySourceMode.M3U) m3uUrl.trim() else ""
                                    val finalHost = if (sourceMode == CategorySourceMode.XTREME) xtremeHost.trim() else ""
                                    val finalUser = if (sourceMode == CategorySourceMode.XTREME) xtremeUsername.trim() else ""
                                    val finalPass = if (sourceMode == CategorySourceMode.XTREME) xtremePassword.trim() else ""

                                    val cat = ChannelCategory(
                                        id = editingId,
                                        name = name.trim(),
                                        order = orderText.toIntOrNull() ?: 0,
                                        m3uUrl = finalM3u,
                                        xtremeHost = finalHost,
                                        xtremeUsername = finalUser,
                                        xtremePassword = finalPass,
                                        xtremeExtension = xtremeExtension.ifBlank { "m3u8" },
                                        xtremeCategoryId = selectedXtremeCategoryId,
                                        xtremeCategoryName = selectedXtremeCategoryName
                                    )
                                    onSaveCategory(cat)
                                    editingId = ""
                                    name = ""
                                    orderText = "0"
                                    importStatusMessage = "تم حفظ ونشر القسم (${cat.name}) في قاعدة البيانات بنجاح"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PitchGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_category_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (editingId.isBlank()) "حفظ ونشر القسم في التطبيق" else "تحديث القسم",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. EXISTING PUBLISHED CATEGORIES LIST
        // =========================================================================
        item {
            Text(
                text = "الأقسام المنشورة حالياً في التطبيق (${categories.size})",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }

        items(categories, key = { it.id.ifBlank { it.name } }) { cat ->
            val sourceBadgeText = when {
                cat.hasXtremeSource -> {
                    val catLabel = if (cat.xtremeCategoryName.isNotBlank()) " | ${cat.xtremeCategoryName}" else ""
                    "حساب Xtreme (${cat.xtremeUsername})$catLabel"
                }
                cat.hasM3uSource -> "رابط قائمة M3U"
                else -> "قسم يدوي (بدون رابط)"
            }
            val sourceBadgeColor = when {
                cat.hasXtremeSource -> ElectricGold
                cat.hasM3uSource -> AccentCyan
                else -> TextSecondary
            }

            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when {
                                cat.hasXtremeSource -> Icons.Default.Dns
                                cat.hasM3uSource -> Icons.Default.Link
                                else -> Icons.Default.Category
                            },
                            contentDescription = null,
                            tint = sourceBadgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = cat.name,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "الترتيب: ${cat.order}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "• $sourceBadgeText",
                                    color = sourceBadgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                editingId = cat.id
                                name = cat.name
                                orderText = cat.order.toString()
                                m3uUrl = cat.m3uUrl
                                xtremeHost = cat.xtremeHost
                                xtremeUsername = cat.xtremeUsername
                                xtremePassword = cat.xtremePassword
                                xtremeExtension = cat.xtremeExtension.ifBlank { "m3u8" }
                                selectedXtremeCategoryId = cat.xtremeCategoryId
                                selectedXtremeCategoryName = cat.xtremeCategoryName
                                sourceMode = when {
                                    cat.hasXtremeSource -> CategorySourceMode.XTREME
                                    cat.hasM3uSource -> CategorySourceMode.M3U
                                    else -> CategorySourceMode.NONE
                                }
                                if (cat.hasXtremeSource) {
                                    loadXtremeCategories(cat.xtremeHost, cat.xtremeUsername, cat.xtremePassword)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل القسم",
                                tint = ElectricGold
                            )
                        }
                        IconButton(
                            onClick = { onDeleteCategory(cat.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف القسم",
                                tint = LiveRed
                            )
                        }
                    }
                }
            }
        }
    }
}
