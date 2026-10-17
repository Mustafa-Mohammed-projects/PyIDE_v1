package com.pyide.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pyide.app.IdeViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(vm: IdeViewModel) {
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(vm.webUrl) }
    val holder = remember { arrayOfNulls<WebView>(1) }

    fun go() {
        var u = url.trim()
        if (!u.startsWith("http")) u = "http://$u"
        vm.webUrl = u; url = u
        holder[0]?.loadUrl(u)
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Web preview", style = MaterialTheme.typography.titleLarge)
            Text(
                "Run a Flask or http.server script (see web_gui.py), then press refresh.",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, modifier = Modifier.weight(1f),
                singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrect = false),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
            IconButton(onClick = { go() }) { Icon(Icons.Filled.Refresh, "Load", tint = cs.primary) }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(0xFF0E1018.toInt())
                    webViewClient = WebViewClient()
                    holder[0] = this
                    loadUrl(vm.webUrl)
                }
            },
        )
    }
}
