package com.learnspark.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen

object AuthScreen : Screen {
    private fun readResolve(): Any = AuthScreen

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.h5)
            Text("登录后跨设备同步你的学习进度", style = MaterialTheme.typography.caption)
        }
    }
}
