package com.pyide.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object PythonHighlighter : VisualTransformation {
    private const val KW = "False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|" +
        "except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|" +
        "with|yield|match|case"
    private const val BI = "print|len|range|int|str|float|list|dict|set|tuple|open|input|enumerate|zip|map|" +
        "filter|sum|min|max|abs|sorted|isinstance|super|type|self|bool|bytes|round|reversed|any|all"

    private val TOKEN = Regex(
        """("{3}[\s\S]*?"{3}|'{3}[\s\S]*?'{3}|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')""" +
            """|(#[^\n]*)""" +
            """|(\b(?:0[xX][0-9a-fA-F]+|\d+\.?\d*(?:[eE][+-]?\d+)?)\b)""" +
            "|\\b($KW)\\b" +
            "|\\b($BI)\\b" +
            """|(@\w+)""" +
            """|((?<=def )\w+|(?<=class )\w+)"""
    )

    private val STYLES = listOf(
        SpanStyle(color = Color(0xFFC3E88D)),                                  // string
        SpanStyle(color = Color(0xFF6B7394), fontStyle = FontStyle.Italic),    // comment
        SpanStyle(color = Color(0xFFF78C6C)),                                  // number
        SpanStyle(color = Color(0xFFC792EA)),                                  // keyword
        SpanStyle(color = Color(0xFF82AAFF)),                                  // builtin
        SpanStyle(color = Color(0xFFFFCB6B)),                                  // decorator
        SpanStyle(color = Color(0xFF22D3EE)),                                  // def/class name
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val b = AnnotatedString.Builder(src)
        for (m in TOKEN.findAll(src)) {
            val g = (1..7).firstOrNull { m.groups[it] != null } ?: continue
            b.addStyle(STYLES[g - 1], m.range.first, m.range.last + 1)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}
