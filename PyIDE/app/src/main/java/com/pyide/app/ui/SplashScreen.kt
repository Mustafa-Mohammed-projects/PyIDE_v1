package com.pyide.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pyide.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

/** Shows res/drawable-nodpi/presplash.png for 3 seconds. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(3000), label = "splash")
    LaunchedEffect(Unit) {
        started = true
        delay(3000)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1018)), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = started,
            enter = fadeIn(tween(700)) + scaleIn(initialScale = 0.92f, animationSpec = tween(700)),
        ) {
            Image(
                painter = painterResource(R.drawable.presplash),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.62f),
                contentScale = ContentScale.Fit,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}
