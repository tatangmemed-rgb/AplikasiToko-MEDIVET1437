package com.medivet1437.aplikasitoko.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
private val LightColors = lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF159957), secondary=androidx.compose.ui.graphics.Color(0xFF1565C0))
@Composable fun AppTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=LightColors,content=content)}
