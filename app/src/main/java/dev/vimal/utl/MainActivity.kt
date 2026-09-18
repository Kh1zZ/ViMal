package dev.vimal.utl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vimal.utl.core.data.repository.MediaNormalizerRepository
import dev.vimal.utl.core.domain.usecase.GetVideoInfoUseCase
import dev.vimal.utl.core.domain.usecase.NormalizeVideoUseCase
import dev.vimal.utl.core.ui.theme.ViMalTheme
import dev.vimal.utl.feature.normalizer.NormalizerScreen
import dev.vimal.utl.feature.normalizer.NormalizerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }  // Dark mode default per PRD
            var isIndonesian by rememberSaveable { mutableStateOf(true) } // Indonesian default per PRD

            ViMalTheme(darkTheme = isDarkTheme) {
                // Manual ViewModel instantiation — no DI framework in MVP
                // (Hilt to be wired later if desired)
                val repository = MediaNormalizerRepository()
                val vmFactory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return NormalizerViewModel(
                            getVideoInfoUseCase = GetVideoInfoUseCase(repository),
                            normalizeVideoUseCase = NormalizeVideoUseCase(repository),
                        ) as T
                    }
                }
                val viewModel: NormalizerViewModel = viewModel(factory = vmFactory)

                NormalizerScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onToggleLanguage = {
                        isIndonesian = !isIndonesian
                        // Full locale switch via AppCompatDelegate is applied at activity level
                        // (full implementation requires AppCompat integration)
                    },
                )
            }
        }
    }
}
