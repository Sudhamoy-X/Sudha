package com.sudha.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME_URL = "https://www.google.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SudhaBrowser()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SudhaBrowser() {
    val context = LocalContext.current
    var address by remember { mutableStateOf(HOME_URL) }
    var browserView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("SUDHA") }

    fun navigate(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        val target = when {
            input.startsWith("https://", ignoreCase = true) ||
                input.startsWith("http://", ignoreCase = true) -> input
            input.contains(" ") || !input.contains(".") ->
                "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(input, Charsets.UTF_8.name())
            else -> "https://$input"
        }

        address = target
        browserView?.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        browserView?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF102A24)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sudha),
                    contentDescription = "SUDHA logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(7.dp)
                )
            }

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Search or enter address") },
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate(address) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF176B55),
                    unfocusedIndicatorColor = Color(0xFFE0E7E3)
                )
            )

            IconButton(onClick = { navigate(address) }) {
                Icon(Icons.Filled.TravelExplore, contentDescription = "Go")
            }
        }

        if (loadingProgress in 1..99) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF176B55),
                trackColor = Color(0xFFE1EAE5)
            )
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            super.onPageStarted(view, url, favicon)
                            url?.let { address = it }
                            loadingProgress = 5
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { address = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            loadingProgress = 100
                            pageTitle = view?.title ?: "SUDHA"
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: "SUDHA"
                        }
                    }

                    browserView = this
                    loadUrl(HOME_URL)
                }
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 5.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { browserView?.loadUrl(HOME_URL) }) {
                    Icon(Icons.Filled.Home, contentDescription = "Home")
                }
                IconButton(
                    onClick = { browserView?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { browserView?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { browserView?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                IconButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Tab manager is planned for the next development phase.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(Icons.Filled.Tab, contentDescription = "Tabs")
                }
            }
        }
    }
}
