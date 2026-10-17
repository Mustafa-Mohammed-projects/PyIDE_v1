package com.pyide.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pyide.app.core.ConsoleBuffer

@Composable
fun KeyChip(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = cs.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
fun FileImage(path: String) {
    val bmp = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp, contentDescription = null,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
fun ConsoleText(buffer: ConsoleBuffer, modifier: Modifier = Modifier, images: List<String> = emptyList()) {
    val cs = MaterialTheme.colorScheme
    val segs by buffer.state.collectAsState()
    val scroll = rememberScrollState()
    val text = remember(segs) {
        buildAnnotatedString {
            for ((t, k) in segs) {
                val color = when (k) {
                    ConsoleBuffer.ERR -> cs.error
                    ConsoleBuffer.INFO -> cs.secondary
                    ConsoleBuffer.INPUT -> cs.tertiary
                    else -> cs.onBackground
                }
                withStyle(SpanStyle(color = color)) { append(t.replace("\r\n", "\n").replace("\r", "")) }
            }
        }
    }
    LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    SelectionContainer {
        Column(modifier.verticalScroll(scroll).padding(12.dp)) {
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
            images.forEach { FileImage(it) }
        }
    }
}

@Composable
fun PromptRow(
    prompt: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = cs.onSurface)
    Row(
        modifier.fillMaxWidth().background(cs.surface).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, color = cs.tertiary, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value, onValueChange,
            Modifier.weight(1f).padding(vertical = 12.dp),
            singleLine = true,
            textStyle = mono,
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit(value) }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    inner()
                }
            },
        )
        IconButton(onClick = { onSubmit(value) }) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = cs.primary)
        }
    }
}
