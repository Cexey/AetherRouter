package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.BiometricVaultHelper
import com.example.ui.components.BiometricLockOverlay
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.theme.AetherRouterTheme
import com.example.viewmodel.AppContainer
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ModelPickerViewModel

class MainActivity : FragmentActivity() {

    private lateinit var appContainer: AppContainer
    private var isVaultUnlocked by mutableStateOf(false)
    private var biometricErrorMessage by mutableStateOf<String?>(null)
    private var lastBiometricEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContainer = AppContainer(applicationContext)

        setContent {
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(appContainer),
            )
            val modelPickerViewModel: ModelPickerViewModel = viewModel(
                factory = ModelPickerViewModel.Factory(appContainer),
            )
            val prefs by chatViewModel.preferencesState.collectAsStateWithLifecycle()
            lastBiometricEnabled = prefs.biometricLockEnabled

            val lockActive = prefs.biometricLockEnabled &&
                BiometricVaultHelper.canAuthenticate(this) &&
                !isVaultUnlocked

            LaunchedEffect(lockActive) {
                if (lockActive) {
                    appContainer.clearSensitiveMemoryCaches()
                    chatViewModel.wipeSensitiveMemoryState()
                    promptBiometricUnlock()
                }
            }

            AetherRouterTheme(
                appearance = prefs.appearance,
            ) {
                if (lockActive) {
                    BiometricLockOverlay(
                        errorMessage = biometricErrorMessage,
                        onUnlockClick = { promptBiometricUnlock() },
                    )
                } else {
                    ChatWorkspaceScreen(
                        chatViewModel = chatViewModel,
                        modelPickerViewModel = modelPickerViewModel,
                        animatedImageLoader = appContainer.documentExtractor.animatedImageLoader,
                    )
                }
            }
        }
    }

    private fun promptBiometricUnlock() {
        biometricErrorMessage = null
        BiometricVaultHelper.authenticate(
            activity = this,
            onSuccess = {
                isVaultUnlocked = true
                biometricErrorMessage = null
            },
            onError = { err ->
                biometricErrorMessage = err
            },
        )
    }

    override fun onStart() {
        super.onStart()
        appContainer.liveClockProvider.start()
    }

    override fun onStop() {
        super.onStop()
        appContainer.liveClockProvider.stop()
        if (lastBiometricEnabled) {
            isVaultUnlocked = false
            appContainer.clearSensitiveMemoryCaches()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appContainer.isInitialized) {
            appContainer.voiceEngineManager.release()
        }
    }
}
