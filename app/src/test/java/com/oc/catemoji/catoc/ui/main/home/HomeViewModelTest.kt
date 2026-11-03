package com.oc.catemoji.catoc.ui.main.home

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class HomeViewModelTest {
    @Test fun tapKeepsSelectedDestinationAndConsumesItOnce() {
        HomePlayDestination.entries.forEach { destination ->
            val model = HomeViewModel(SavedStateHandle())
            model.showModeSelection(destination)
            assertEquals(destination, model.selectMode())
            assertNull(model.selectMode())
        }
    }

    @Test fun voiceWaitsForGrantAndDenialKeepsModeSelectionOpen() {
        val model = HomeViewModel(SavedStateHandle())
        model.showModeSelection(HomePlayDestination.BATTLE)
        assertTrue(model.requestVoiceMode())
        assertFalse(model.requestVoiceMode())
        assertNull(model.onMicrophonePermissionResult(false))
        assertEquals(HomePlayDestination.BATTLE, model.modeDestination)
        assertTrue(model.requestVoiceMode())
        assertEquals(HomePlayDestination.BATTLE, model.onMicrophonePermissionResult(true))
        assertNull(model.onMicrophonePermissionResult(true))
    }

    @Test fun pendingVoiceDestinationSurvivesRecreation() {
        val state = SavedStateHandle()
        HomeViewModel(state).apply {
            showModeSelection(HomePlayDestination.TRAINING)
            requestVoiceMode()
        }
        val recreated = HomeViewModel(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        assertEquals(HomePlayDestination.TRAINING, recreated.onMicrophonePermissionResult(true))
        assertNull(recreated.modeDestination)
    }

    @Test fun closingOrChoosingTapIgnoresALateMicrophoneCallback() {
        val model = HomeViewModel(SavedStateHandle())
        model.showModeSelection(HomePlayDestination.TRAINING)
        model.requestVoiceMode()
        model.closeModeSelection()
        assertNull(model.onMicrophonePermissionResult(true))
        assertFalse(model.requestVoiceMode())
        model.showModeSelection(HomePlayDestination.BATTLE)
        model.requestVoiceMode()
        assertEquals(HomePlayDestination.BATTLE, model.selectMode())
        assertNull(model.onMicrophonePermissionResult(true))
    }
}
