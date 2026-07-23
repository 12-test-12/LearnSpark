package com.learnspark.features.projects

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

object ProjectsScreen : Screen {
    private fun readResolve(): Any = ProjectsScreen

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("学习项目", style = MaterialTheme.typography.h5)
            Text("阶段 1.4 接入项目列表 + 离线 CRUD", style = MaterialTheme.typography.caption)
        }
    }
}
