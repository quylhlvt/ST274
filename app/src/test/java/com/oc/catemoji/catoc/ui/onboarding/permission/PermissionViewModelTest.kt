package com.oc.catemoji.catoc.ui.onboarding.permission

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionViewModelTest {
    @Test fun firstMicrophoneRequestUsesAndroidPrompt() {
        val model = PermissionViewModel(memoryPreferences())
        assertEquals(MicrophonePermissionAction.REQUEST, model.microphoneAction(false, false))
    }

    @Test fun ordinaryDenialCanBeRequestedAgainAndPermanentDenialOpensSettings() {
        val model = PermissionViewModel(memoryPreferences())
        model.onMicrophoneRequested()
        model.onMicrophonePermissionResult(false)
        assertEquals(MicrophonePermissionAction.REQUEST, model.microphoneAction(false, true))
        assertEquals(MicrophonePermissionAction.SETTINGS, model.microphoneAction(false, false))
    }

    @Test fun blockedPermissionIsRememberedAcrossViewModelInstances() {
        val preferences = memoryPreferences()
        PermissionViewModel(preferences).onMicrophoneRequested()
        assertEquals(MicrophonePermissionAction.SETTINGS,
            PermissionViewModel(preferences).microphoneAction(false, false))
    }

    @Test fun grantAllowsVoiceAndLaterRevocationAllowsANewAndroidPrompt() {
        val model = PermissionViewModel(memoryPreferences())
        model.onMicrophoneRequested()
        assertEquals(MicrophonePermissionAction.GRANTED, model.microphoneAction(true, false))
        model.onMicrophonePermissionResult(true)
        assertEquals(MicrophonePermissionAction.REQUEST, model.microphoneAction(false, false))
    }

    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Boolean>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putBoolean" -> { values[args!![0] as String] = args[1] as Boolean; proxy }
                "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
