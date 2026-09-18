package dev.vimal.utl

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vimal.utl.core.data.repository.MediaNormalizerRepository
import dev.vimal.utl.core.domain.usecase.GetVideoInfoUseCase
import dev.vimal.utl.core.domain.usecase.NormalizeVideoUseCase
import dev.vimal.utl.core.ui.theme.ViMalTheme
import dev.vimal.utl.feature.normalizer.NormalizerScreen
import dev.vimal.utl.feature.normalizer.NormalizerViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }  // Dark mode default per PRD
            var currentLanguage by rememberSaveable { mutableStateOf("id") } // Indonesian default per PRD

            val locale = remember(currentLanguage) { Locale(currentLanguage) }
            val baseConfig = LocalConfiguration.current
            // Override LocalConfiguration only — triggers Compose recomposition with correct locale.
            // NOTE: LocalContext must NOT be overridden here; it is a StaticCompositionLocal and
            // overriding it via CompositionLocalProvider causes an immediate startup crash.
            // Actual locale switching is handled by LocaleManager (API 33+) below.
            val localizedConfig = remember(baseConfig, locale) {
                Configuration(baseConfig).apply {
                    setLocale(locale)
                }
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
            ) {
                ViMalTheme(darkTheme = isDarkTheme) {
                    val repository = remember { MediaNormalizerRepository() }
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
                        currentLanguage = currentLanguage,
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                        onToggleLanguage = {
                            val nextLang = if (currentLanguage == "id") "en" else "id"
                            currentLanguage = nextLang
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                getSystemService(LocaleManager::class.java).applicationLocales =
                                    LocaleList.forLanguageTags(nextLang)
                            }
                        },
                    )
                }
            }
        }
    }
}
