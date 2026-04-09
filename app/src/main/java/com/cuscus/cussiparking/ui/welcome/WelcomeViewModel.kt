package com.cuscus.cussiparking.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cuscus.cussiparking.data.SettingsManager

class WelcomeViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    fun isOnboardingDone(): Boolean = settingsManager.isOnboardingCompleted()

    fun markOnboardingDone() = settingsManager.setOnboardingCompleted(true)

    class Factory(private val sm: SettingsManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WelcomeViewModel(sm) as T
    }
}
