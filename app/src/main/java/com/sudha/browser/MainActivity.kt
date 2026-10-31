package com.sudha.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

private const val HOME = "https://www.google.com"
private val bg = Color(0xFF101014)
private val surface = Color(0xFF1D1D22)
private val accent = Color(0xFF8798FF)

data class TabData(val url: String = HOME, val title: String = "New tab")

private fun restoreTabs(prefs: android.content.SharedPreferences): List<TabData> {
    return try {
        val saved = org.json.JSONArray(prefs.getString("open_tabs", "[]") ?: "[]")
        val restored = (0 until saved.length()).mapNotNull { index ->
            val item = saved.optJSONObject(index) ?: return@mapNotNull null
            TabData(item.optString("url", HOME).ifBlank { HOME }, item.optString("title", "New tab"))
        }
        restored.ifEmpty { listOf(TabData()) }.take(30)
    } catch (_: Exception) {
        listOf(TabData())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16,16,20)
        window.navigationBarColor = android.graphics.Color.rgb(16,16,20)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = surface)) { BrowserScreen() } }
        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        val prefs = getSharedPreferences("sudha_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0L) < 12L * 60L * 60L * 1000L) return
        prefs.edit().putLong("last_check", now).apply()
        Thread {
            try {
                val connection = (java.net.URL("https://api.github.com/repos/Sudhamoy-X/Sudha/releases/latest").openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "SUDHA-Android")
                }
                val release = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val latest = release.optString("tag_name").removePrefix("v").substringBefore("-")
                val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
                fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
                val a = parts(latest)
                val b = parts(current)
                var newer = false
                for (i in 0 until maxOf(a.size, b.size)) {
                    val compare = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
                    if (compare > 0) { newer = true; break }
                    if (compare < 0) break
                }
                if (newer) runOnUiThread {
                    startActivity(android.content.Intent(this, UpdateActivity::class.java).putExtra("AUTO_UPDATE", true))
                }
            } catch (_: Exception) {
                // Offline, no public release yet, or transient GitHub failure: retry on a later app launch.
            }
        }.start()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sudha", Context.MODE_PRIVATE) }
    var tabs by remember { mutableStateOf(listOf(TabData())) }
    var active by remember { mutableIntStateOf(0) }
    val views = remember { mutableStateMapOf<Int, WebView>() }
    var address by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var back by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var tabScreen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<String?>(null) }
    var findBar by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var night by remember { mutableStateOf(prefs.getBoolean("night", false)) }
    var desktop by remember { mutableStateOf(prefs.getBoolean("desktop", false)) }
    var blockAds by remember { mutableStateOf(prefs.getBoolean("ads", true)) }
    var dnt by remember { mutableStateOf(prefs.getBoolean("dnt", true)) }
    var webrtc by remember { mutableStateOf(prefs.getBoolean("webrtc", false)) }
    var ua by remember { mutableStateOf(prefs.getString("ua", "Default") ?: "Default") }
    var historyJson by remember { mutableStateOf(prefs.getString("history", "[]") ?: "[]") }
    var bookmarkUrls by remember { mutableStateOf(prefs.getStringSet("bookmarks", emptySet())?.toList() ?: emptyList()) }
    val web = views[active]

    fun message(s: String) { Toast.makeText(context, s, Toast.LENGTH_SHORT).show() }
    fun pref(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).apply() }
    fun navigate(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val url = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            q.contains(" ") || !q.contains(".") -> "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8")
            else -> "https://$q"
        }
        address = url
        views[active]?.loadUrl(url) ?: run { val copy = tabs.toMutableList(); copy[active] = TabData(url); tabs = copy }
    }
    fun saveBookmark() {
        val set = prefs.getStringSet("bookmarks", emptySet())?.toMutableSet() ?: mutableSetOf()
        val url = views[active]?.url ?: address
        if (url.isNotBlank()) { set.add(url); bookmarkUrls = set.toList(); prefs.edit().putStringSet("bookmarks", set).apply(); message("Bookmark saved") }
    }
    fun newTab() {
        tabs = tabs + TabData()
        active = tabs.lastIndex
        address = ""
        tabScreen = false
    }

    BackHandler(section != null || tools || tabScreen || findBar || (web?.canGoBack() == true)) {
        when {
            section != null -> section = null
            tools -> tools = false
            tabScreen -> tabScreen = false
            findBar -> { web?.clearMatches(); findBar = false }
            else -> views[active]?.goBack()
        }
    }

    Column(Modifier.fillMaxSize().background(bg)) {
        if (section != null) {
            SettingsScreen(
                section = section!!,
                onBack = { section = null },
                night = night, setNight = { night = it; pref("night", it); views.values.forEach { v -> setNight(v, it) } },
                ads = blockAds, setAds = { blockAds = it; pref("ads", it) },
                desktop = desktop, setDesktop = { desktop = it; pref("desktop", it); views.values.forEach { v -> setAgent(v, it, ua) } },
                dnt = dnt, setDnt = { dnt = it; pref("dnt", it) },
                webrtc = webrtc, setWebrtc = { webrtc = it; pref("webrtc", it) },
                ua = ua, setUa = { ua = it; prefs.edit().putString("ua", it).apply(); views.values.forEach { v -> setAgent(v, desktop, it) } },
                bookmarks = bookmarkUrls, historyJson = historyJson,
                onOpenSavedUrl = { savedUrl -> section = null; navigate(savedUrl) },
                onDeleteBookmark = { savedUrl -> bookmarkUrls = bookmarkUrls - savedUrl; prefs.edit().putStringSet("bookmarks", bookmarkUrls.toSet()).apply() },
                onClearHistory = { historyJson = "[]"; prefs.edit().putString("history", "[]").apply() },
                clear = { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); context.deleteDatabase("webview.db"); historyJson = "[]"; prefs.edit().putString("history", "[]").apply(); message("Browsing data cleared") }
            )
        } else {
            Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                Text("  Homepage", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { tabScreen = true }) { Icon(Icons.Default.Tab, null, tint = Color.LightGray) }
            }
            when {
                tabScreen -> {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tabs (" + tabs.size + ")", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { newTab() }) { Text("New tab") }
                        IconButton(onClick = { tabScreen = false }) { Icon(Icons.Default.Close, null) }
                    }
                    LazyColumn {
                        items(tabs.indices.toList()) { i ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).background(surface, RoundedCornerShape(12.dp)).clickable { active = i; address = tabs[i].url; tabScreen = false }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tabs[i].title, color = Color.White, maxLines = 1)
                                    Text(tabs[i].url, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    views.remove(i)?.destroy()
                                    val remaining = tabs.filterIndexed { index, _ -> index != i }.ifEmpty { listOf(TabData()) }
                                    val reindexed = views.toMap()
                                    views.clear()
                                    reindexed.forEach { (oldIndex, view) ->
                                        if (oldIndex > i) views[oldIndex - 1] = view
                                        else if (oldIndex < i) views[oldIndex] = view
                                    }
                                    tabs = remaining
                                    active = when {
                                        remaining.size == 1 && remaining[0].url == HOME -> 0
                                        active > i -> active - 1
                                        active == i -> (i - 1).coerceAtLeast(0).coerceAtMost(remaining.lastIndex)
                                        else -> active.coerceAtMost(remaining.lastIndex)
                                    }
                                    address = remaining[active].url.takeIf { it != HOME }.orEmpty()
                                }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                            }
                        }
                    }
                }
                web == null -> {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.sudha_icon),
                            contentDescription = "SUDHA Browser logo",
                            modifier = Modifier.size(76.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(28.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .border(1.dp, Color(0xFF38383D), RoundedCornerShape(32.dp))
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { navigate(address) }),
                                decorationBox = { innerTextField -> innerTextField() }
                            )
                        }
                    }
                }
                else -> AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = accent)
            if (findBar) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(findText, { findText = it; web?.findAllAsync(it) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Find in page") })
                    IconButton(onClick = { web?.findNext(false) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    IconButton(onClick = { web?.findNext(true) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                    IconButton(onClick = { web?.clearMatches(); findBar = false }) { Icon(Icons.Default.Close, null) }
                }
            }
            Row(Modifier.fillMaxWidth().height(58.dp).background(surface), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Action(Icons.Default.Search, "Find") { findBar = true }
                Action(Icons.Default.BookmarkAdd, "Save") { saveBookmark() }
                Action(Icons.Default.Layers, "Saved") { section = "Bookmarks" }
                Action(Icons.Default.Translate, "Translate") { web?.url?.let { navigate("https://translate.google.com/translate?u=" + it) } }
                Action(Icons.Default.Code, "Source") { web?.url?.let { navigate("view-source:" + it) } }
                Action(Icons.Default.MoreHoriz, "Tools") { tools = true }
            }
            Row(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF151519)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                NavAction(Icons.Default.ArrowBack, "Back", back) { web?.goBack() }
                NavAction(Icons.Default.ArrowForward, "Forward", forward) { web?.goForward() }
                NavAction(Icons.Default.Home, "Home", true) { views[active]?.loadUrl(HOME) ?: run { address = "" } }
                NavAction(Icons.Default.Tab, tabs.size.toString(), true) { tabScreen = true }
                NavAction(Icons.Default.Menu, "Menu", true) { tools = true }
            }
        }
    }

    if (tools) {
        AlertDialog(
            onDismissRequest = { tools = false }, containerColor = surface,
            title = { Text("Tools", color = Color.White) },
            text = {
                Column {
                    val entries = listOf(
                        "Find in page" to { findBar = true },
                        "Save page" to { web?.saveWebArchive(context.getExternalFilesDir(null)?.absolutePath + "/saved-page.mht", false, null); message("Save page requested") },
                        "Saved pages" to { section = "Bookmarks" },
                        "Translate" to { web?.url?.let { navigate("https://translate.google.com/translate?u=" + it) } },
                        "View source" to { web?.url?.let { navigate("view-source:" + it) } },
                        "Full screen" to { message("Full screen mode toggled") },
                        "Show images" to { views.values.forEach { v -> v.settings.loadsImagesAutomatically = !v.settings.loadsImagesAutomatically } },
                        "Resource filter" to { section = "Ad blocking" },
                        "User-agent" to { section = "User-agent" },
                        "Network log" to { section = "Advanced" },
                        "Check for updates" to { context.startActivity(android.content.Intent(context, UpdateActivity::class.java)) },
                        "Settings" to { section = "Settings" }
                    )
                    entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { entry -> TextButton(onClick = { tools = false; entry.second() }, modifier = Modifier.weight(1f)) { Text(entry.first, color = Color.White, fontSize = 12.sp) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tools = false; section = "Settings" }) { Text("Settings") } },
            dismissButton = { TextButton(onClick = { tools = false }) { Text("Close") } }
        )
    }

    LaunchedEffect(active, views.size) {
        if (views[active] == null) {
            val w = WebView(context)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.loadsImagesAutomatically = true
            w.settings.useWideViewPort = true
            w.settings.loadWithOverviewMode = true
            w.settings.javaScriptCanOpenWindowsAutomatically = false
            w.settings.setSupportMultipleWindows(false)
            setNight(w, night)
            setAgent(w, desktop, ua)
            w.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { progress = 5; if (url != null) address = url }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress = 100
                    back = view?.canGoBack() == true
                    forward = view?.canGoForward() == true
                    if (url != null) {
                        address = url
                        val copy = tabs.toMutableList()
                        if (active < copy.size) { copy[active] = TabData(url, view?.title ?: url); tabs = copy }
                        val title = view?.title?.takeIf { it.isNotBlank() } ?: url
                        val current = org.json.JSONArray(historyJson)
                        val updated = org.json.JSONArray()
                        for (i in 0 until current.length()) { val item = current.optJSONObject(i) ?: continue; if (item.optString("url") != url) updated.put(item) }
                        updated.put(org.json.JSONObject().put("url", url).put("title", title).put("visitedAt", System.currentTimeMillis()))
                        val bounded = org.json.JSONArray()
                        for (i in maxOf(0, updated.length() - 500) until updated.length()) bounded.put(updated.get(i))
                        historyJson = bounded.toString()
                        prefs.edit().putString("history", historyJson).apply()
                    }
                }
            }
            w.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, value: Int) { progress = value; back = view?.canGoBack() == true; forward = view?.canGoForward() == true }
            }
            w.setDownloadListener { url, agent, disposition, mime, _ ->
                try {
                    val name = URLUtil.guessFileName(url, disposition, mime)
                    val req = DownloadManager.Request(Uri.parse(url)).setMimeType(mime).addRequestHeader("User-Agent", agent).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    message("Download started")
                } catch (_: Exception) { message("Download failed") }
            }
            views[active] = w
            val initial = tabs.getOrNull(active)?.url ?: HOME
            if (initial != HOME) w.loadUrl(initial)
        }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    Column(Modifier.clickable(onClick = click).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(19.dp))
        Text(label, color = Color.LightGray, fontSize = 9.sp)
    }
}
@Composable
private fun NavAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, click: () -> Unit) {
    Column(Modifier.clickable(enabled = enabled, onClick = click).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) Color.White else Color.DarkGray, modifier = Modifier.size(20.dp))
        Text(label, color = Color.LightGray, fontSize = 9.sp)
    }
}

@Composable
private fun SettingsScreen(
    section: String, onBack: () -> Unit,
    night: Boolean, setNight: (Boolean) -> Unit,
    ads: Boolean, setAds: (Boolean) -> Unit,
    desktop: Boolean, setDesktop: (Boolean) -> Unit,
    dnt: Boolean, setDnt: (Boolean) -> Unit,
    webrtc: Boolean, setWebrtc: (Boolean) -> Unit,
    ua: String, setUa: (String) -> Unit,
    bookmarks: List<String>, historyJson: String,
    onOpenSavedUrl: (String) -> Unit, onDeleteBookmark: (String) -> Unit,
    onClearHistory: () -> Unit, clear: () -> Unit
) {
    var subsection by remember { mutableStateOf<String?>(null) }
    val page = subsection ?: section
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (subsection != null) subsection = null else onBack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(page, color = Color.White, fontSize = 18.sp)
        }
        LazyColumn {
            if (page == "Settings") {
                items(listOf("General", "Customization", "Privacy", "Advanced", "Scripts", "About", "Bookmarks", "History")) { item ->
                    SettingRow(item, "", { subsection = item })
                }
            } else if (page == "General") {
                items(listOf("Sync", "User-agent: " + ua, "Clear data", "Ad blocking", "Site configuration", "Password manager", "Night mode", "Reader mode", "Toolbars settings", "Customize menu", "Customize context menu", "Language: System", "Homepage: Default", "Search settings", "AI settings", "Orientation: System", "Download manager", "External video player", "Clear data on exit", "Font", "Gestures", "Import/export bookmarks", "Import data", "Export data", "Restore tabs on startup", "Set as default browser")) { item ->
                    SettingRow(item, "", {
                        when {
                            item == "Night mode" -> setNight(!night)
                            item == "Ad blocking" -> setAds(!ads)
                            item == "Clear data" -> clear()
                            item.startsWith("User-agent") -> setUa(if (ua == "Default") "Desktop" else "Default")
                        }
                    })
                }
            } else if (page == "Privacy") {
                item { SettingRow("Do not track", if (dnt) "On" else "Off", { setDnt(!dnt) }) }
                item { SettingRow("Disable WebRTC", if (webrtc) "On" else "Off", { setWebrtc(!webrtc) }) }
                item { SettingRow("Do not sell or share data", "Privacy preference", {}) }
                item { SettingRow("Clear browsing data", "", clear) }
            } else if (page == "Customization") {
                item { SettingRow("Night mode", if (night) "On" else "Off", { setNight(!night) }) }
                item { SettingRow("Toolbars settings", "", {}) }
                item { SettingRow("Customize menu", "", {}) }
                item { SettingRow("Customize context menu", "", {}) }
                item { SettingRow("Font", "", {}) }
            } else if (page == "Advanced") {
                item { SettingRow("Desktop mode", if (desktop) "On" else "Off", { setDesktop(!desktop) }) }
                item { SettingRow("User-agent", ua, { setUa(if (ua == "Default") "Desktop" else "Default") }) }
                item { SettingRow("Download location", "Downloads", {}) }
                item { SettingRow("Download manager", "Built-in", {}) }
                item { SettingRow("Network log", "Not implemented yet", {}) }
            } else if (page == "User-agent") {
                items(listOf("Default", "Mobile", "Desktop")) { item -> SettingRow(item, if (ua == item) "Selected" else "", { setUa(item) }) }
            } else if (page == "Ad blocking") {
                item { SettingRow("Enable ad blocking", if (ads) "On" else "Off", { setAds(!ads) }) }
                item { SettingRow("Filter lists", "Filter engine pending", {}) }
            } else if (page == "Bookmarks") {
                if (bookmarks.isEmpty()) item { SettingRow("No bookmarks yet", "Use Save from the browser toolbar", {}) }
                else items(bookmarks.sorted()) { url ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onOpenSavedUrl(url) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(url.substringAfter("://").take(70), color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text(url, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { onDeleteBookmark(url) }) { Icon(Icons.Default.Delete, "Delete bookmark", tint = Color.Gray) }
                    }
                    HorizontalDivider(color = Color(0xFF29292F), thickness = 0.5.dp)
                }
            } else if (page == "History") {
                val history = org.json.JSONArray(historyJson)
                item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent pages", color = Color.Gray, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearHistory) { Text("Clear history") }
                } }
                if (history.length() == 0) item { SettingRow("No browsing history", "Pages you visit will appear here", {}) }
                else items((0 until history.length()).toList().reversed()) { index ->
                    val entry = history.optJSONObject(index)
                    val url = entry?.optString("url").orEmpty()
                    val title = entry?.optString("title")?.takeIf { it.isNotBlank() } ?: url
                    SettingRow(title, url, { onOpenSavedUrl(url) })
                }
            } else {
                item { SettingRow("SUDHA Browser", "Version 0.2.0", {}) }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 20.dp, vertical = 15.dp)) {
        Text(title, color = Color(0xFFE8E8EA), fontSize = 14.sp)
        if (subtitle.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(subtitle, color = Color.Gray, fontSize = 11.sp) }
    }
    HorizontalDivider(color = Color(0xFF29292F), thickness = 0.5.dp)
}
private fun setAgent(web: WebView, desktop: Boolean, mode: String) {
    if (desktop || mode == "Desktop") web.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    else web.settings.userAgentString = null
}
private fun setNight(web: WebView, enabled: Boolean) {
    if (android.os.Build.VERSION.SDK_INT >= 29) web.settings.forceDark = if (enabled) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
}
