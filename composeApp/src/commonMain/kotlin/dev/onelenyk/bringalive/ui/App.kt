package dev.onelenyk.bringalive.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import dev.onelenyk.bringalive.ui.chat.ChatScreen
import dev.onelenyk.bringalive.ui.theme.BringaliveTheme

@Composable
fun App() {
    BringaliveTheme {
        Navigator(ChatScreen())
    }
}
