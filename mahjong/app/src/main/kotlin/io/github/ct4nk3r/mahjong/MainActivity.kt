package io.github.ct4nk3r.mahjong

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ct4nk3r.games.core.ui.GamesTheme
import io.github.ct4nk3r.mahjong.ui.MahjongApp

// AppCompatActivity is required for the per-app language switch on Android 12 and older.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MahjongViewModel = viewModel()
            val common by vm.common.collectAsStateWithLifecycle()
            GamesTheme(this, common.themeMode) {
                MahjongApp(vm)
            }
        }
    }
}
