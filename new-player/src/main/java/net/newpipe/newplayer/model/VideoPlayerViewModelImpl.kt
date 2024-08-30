/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2024 <code(at)newpipe-ev.de>
 *
 * NewPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.newpipe.newplayer.model

import android.app.Application
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.newpipe.newplayer.Chapter
import net.newpipe.newplayer.utils.VideoSize
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.playerInternals.getPlaylistItemsFromItemList
import net.newpipe.newplayer.ui.ContentScale

val VIDEOPLAYER_UI_STATE = "video_player_ui_state"

private const val TAG = "VideoPlayerViewModel"


@HiltViewModel
class VideoPlayerViewModelImpl @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    application: Application,
) : AndroidViewModel(application), VideoPlayerViewModel {

    // private
    private val mutableUiState = MutableStateFlow(VideoPlayerUIState.DEFAULT)
    private var currentContentRatio = 1F

    private var uiVisibilityJob: Job? = null
    private var progressUpdaterJob: Job? = null

    // this is necesary to restore the embedded view UI configuration when returning from fullscreen
    private var embeddedUiConfig: EmbeddedUiConfig? = null

    private val audioManager =
        getSystemService(application.applicationContext, AudioManager::class.java)!!

    init {
        val soundVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            .toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableUiState.update {
            it.copy(soundVolume = soundVolume)
        }
    }

    //interface
    override var newPlayer: NewPlayer? = null
        set(value) {
            field = value
            installNewPlayer()
        }

    override val uiState = mutableUiState.asStateFlow()

    override val internalPlayer: Player?
        get() = newPlayer?.internalPlayer

    override var minContentRatio: Float = 4F / 3F
        set(value) {
            if (value <= 0 || maxContentRatio < value) Log.e(
                TAG,
                "Ignoring maxContentRatio: It must not be 0 or less and it may not be bigger then mmaxContentRatio. It was Set to: $value"
            )
            else {
                field = value
                mutableUiState.update { it.copy(embeddedUiRatio = getEmbeddedUiRatio()) }
            }
        }


    override var maxContentRatio: Float = 16F / 9F
        set(value) {
            if (value <= 0 || value < minContentRatio) Log.e(
                TAG,
                "Ignoring maxContentRatio: It must not be 0 or less and it may not be smaller then minContentRatio. It was Set to: $value"
            )
            else {
                field = value
                mutableUiState.update { it.copy(embeddedUiRatio = getEmbeddedUiRatio()) }
            }
        }

    override var contentFitMode: ContentScale
        get() = mutableUiState.value.contentFitMode
        set(value) {
            mutableUiState.update {
                it.copy(contentFitMode = value)
            }
        }

    private var mutableEmbeddedPlayerDraggedDownBy = MutableSharedFlow<Float>()
    override val embeddedPlayerDraggedDownBy = mutableEmbeddedPlayerDraggedDownBy.asSharedFlow()

    private var mutableOnBackPressed = MutableSharedFlow<Unit>()
    override val onBackPressed: SharedFlow<Unit> = mutableOnBackPressed.asSharedFlow()

    private fun installNewPlayer() {
        internalPlayer?.let { player ->
            Log.d(TAG, "Install player: ${player.videoSize.width}")

            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    Log.d(TAG, "Playing state changed. Is Playing: $isPlaying")
                    mutableUiState.update {
                        it.copy(playing = isPlaying)
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    updateContentRatio(VideoSize.fromMedia3VideoSize(videoSize))
                }


                // TODO: This is not correctly applicable for loading indicator
                override fun onIsLoadingChanged(isLoading: Boolean) {
                    super.onIsLoadingChanged(isLoading)
                    mutableUiState.update {
                        it.copy(isLoading = isLoading)
                    }
                }

                override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                    super.onPlaylistMetadataChanged(mediaMetadata)
                    updatePlaylist()
                }
            })
        }
        newPlayer?.let { newPlayer ->
            viewModelScope.launch {
                newPlayer.playMode.collect { newMode ->
                    val currentMode = mutableUiState.value.uiMode.toPlayMode()
                    if (currentMode != newMode) {
                        mutableUiState.update {
                            it.copy(
                                uiMode = UIModeState.fromPlayMode(newMode),
                                embeddedUiConfig = embeddedUiConfig
                            )
                        }
                    }
                }
            }
        }
        updatePlaylist()
    }

    fun updateContentRatio(videoSize: VideoSize) {
        val newRatio = videoSize.getRatio()
        val ratio = if (newRatio.isNaN()) currentContentRatio else newRatio
        currentContentRatio = ratio
        Log.d(TAG, "Update Content ratio: $ratio")
        mutableUiState.update {
            it.copy(
                contentRatio = currentContentRatio, embeddedUiRatio = getEmbeddedUiRatio()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "viewmodel cleared")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun initUIState(instanceState: Bundle) {

        val recoveredUiState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) instanceState.getParcelable(
                VIDEOPLAYER_UI_STATE, VideoPlayerUIState::class.java
            )
            else instanceState.getParcelable(VIDEOPLAYER_UI_STATE)

        if (recoveredUiState != null) {
            mutableUiState.update {
                recoveredUiState
            }
        }
    }

    override fun play() {
        hideUi()
        newPlayer?.play()
    }

    override fun pause() {
        uiVisibilityJob?.cancel()
        newPlayer?.pause()

    }

    override fun prevStream() {
        resetHideUiDelayedJob()
        Log.e(TAG, "imeplement prev stream")
    }

    override fun nextStream() {
        resetHideUiDelayedJob()
        Log.e(TAG, "implement next stream")
    }

    override fun showUi() {
        mutableUiState.update {
            it.copy(uiMode = it.uiMode.getControllerUiVisibleState())
        }
        resetHideUiDelayedJob()
        resetProgressUpdatePeriodicallyJob()
    }

    private fun resetHideUiDelayedJob() {
        uiVisibilityJob?.cancel()
        uiVisibilityJob = viewModelScope.launch {
            delay(4000)
            hideUi()
        }
    }

    private fun resetProgressUpdatePeriodicallyJob() {
        progressUpdaterJob?.cancel()
        progressUpdaterJob = viewModelScope.launch {
            while (true) {
                updateProgressOnce()
                delay(1000)
            }
        }

    }

    private fun updateProgressOnce() {
        val progress = internalPlayer?.currentPosition ?: 0
        val duration = internalPlayer?.duration ?: 1
        val bufferedPercentage = (internalPlayer?.bufferedPercentage?.toFloat() ?: 0f) / 100f
        val progressPercentage = progress.toFloat() / duration.toFloat()

        mutableUiState.update {
            it.copy(
                seekerPosition = progressPercentage,
                durationInMs = duration,
                playbackPositionInMs = progress,
                bufferedPercentage = bufferedPercentage
            )
        }
    }

    override fun hideUi() {
        progressUpdaterJob?.cancel()
        uiVisibilityJob?.cancel()
        mutableUiState.update {
            it.copy(uiMode = it.uiMode.getUiHiddenState())
        }
    }

    override fun seekPositionChanged(newValue: Float) {
        uiVisibilityJob?.cancel()
        mutableUiState.update { it.copy(seekerPosition = newValue) }
    }

    override fun seekingFinished() {
        resetHideUiDelayedJob()
        val seekerPosition = mutableUiState.value.seekerPosition
        val seekPositionInMs = (internalPlayer?.duration?.toFloat() ?: 0F) * seekerPosition
        newPlayer?.currentPosition = seekPositionInMs.toLong()
        Log.i(TAG, "Seek to Ms: $seekPositionInMs")
    }

    override fun embeddedDraggedDown(offset: Float) {
        saveTryEmit(mutableEmbeddedPlayerDraggedDownBy, offset)
    }

    override fun fastSeek(count: Int) {
        mutableUiState.update {
            it.copy(
                fastSeekSeconds = count * (newPlayer?.fastSeekAmountSec ?: 10)
            )
        }

        if (mutableUiState.value.uiMode.controllerUiVisible) {
            resetHideUiDelayedJob()
        }
    }

    override fun finishFastSeek() {
        val fastSeekAmount = mutableUiState.value.fastSeekSeconds
        if (fastSeekAmount != 0) {
            Log.d(TAG, "$fastSeekAmount")

            newPlayer?.currentPosition = (newPlayer?.currentPosition ?: 0) + (fastSeekAmount * 1000)
            mutableUiState.update {
                it.copy(fastSeekSeconds = 0)
            }
        }
    }

    override fun brightnessChange(changeRate: Float, systemBrightness: Float) {
        if (mutableUiState.value.uiMode.fullscreen) {
            val currentBrightness = mutableUiState.value.brightness
                ?: if (systemBrightness < 0f) 0.5f else systemBrightness
            Log.d(
                TAG,
                "currentBrightnes: $currentBrightness, sytemBrightness: $systemBrightness, changeRate: $changeRate"
            )

            val newBrightness = (currentBrightness + changeRate * 1.3f).coerceIn(0f, 1f)
            mutableUiState.update {
                it.copy(brightness = newBrightness)
            }
        }
    }

    override fun volumeChange(changeRate: Float) {
        val currentVolume = mutableUiState.value.soundVolume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        // we multiply changeRate by 1.5 so your finger only has to swipe a portion of the whole
        // screen in order to fully enable or disable the volume
        val newVolume = (currentVolume + changeRate * 1.3f).coerceIn(0f, 1f)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC, (newVolume * maxVolume).toInt(), 0
        )
        println("Blub: currentVolume: $currentVolume, changeRate: $changeRate, maxVolume: $maxVolume, newvolume: $newVolume")
        mutableUiState.update {
            it.copy(soundVolume = newVolume)
        }
    }

    override fun openStreamSelection(selectChapter: Boolean, embeddedUiConfig: EmbeddedUiConfig) {
        println("gurken openSelection ${embeddedUiConfig}")
        uiVisibilityJob?.cancel()
        if (!uiState.value.uiMode.fullscreen) {
            this.embeddedUiConfig = embeddedUiConfig
        }
        updateUiMode(
            if (selectChapter) uiState.value.uiMode.getChapterSelectUiState()
            else uiState.value.uiMode.getStreamSelectUiState()
        )
    }

    override fun closeStreamSelection() {
        updateUiMode(uiState.value.uiMode.getUiHiddenState())
    }

    override fun switchToEmbeddedView() {
        uiVisibilityJob?.cancel()
        finishFastSeek()
        updateUiMode(UIModeState.EMBEDDED_VIDEO)
    }

    override fun onBackPressed() {
        val nextMode = uiState.value.uiMode.getNextModeWhenBackPressed()
        if (nextMode != null) {
            updateUiMode(nextMode)
        } else {
            saveTryEmit(mutableOnBackPressed, Unit)
        }
    }

    override fun switchToFullscreen(embeddedUiConfig: EmbeddedUiConfig) {
        uiVisibilityJob?.cancel()
        finishFastSeek()

        this.embeddedUiConfig = embeddedUiConfig
        updateUiMode(UIModeState.FULLSCREEN_VIDEO)
    }

    override fun chapterSelected(chapter: Chapter) {
        println("gurken chapter selectd: $chapter")
    }

    override fun streamSelected(streamId: Int) {
        println("stream selected: $streamId")
    }

    private fun updateUiMode(newState: UIModeState) {
        val newPlayMode = newState.toPlayMode()
        val currentPlayMode = mutableUiState.value.uiMode.toPlayMode()
        if (newPlayMode != currentPlayMode) {
            newPlayer?.setPlayMode(newPlayMode!!)
        } else {
            mutableUiState.update {
                it.copy(uiMode = newState)
            }
        }
    }

    private fun getEmbeddedUiRatio() = internalPlayer?.let { player ->
        val videoRatio = VideoSize.fromMedia3VideoSize(player.videoSize).getRatio()
        return (if (videoRatio.isNaN()) currentContentRatio
        else videoRatio).coerceIn(minContentRatio, maxContentRatio)


    } ?: minContentRatio

    private fun updatePlaylist() {
        newPlayer?.let { newPlayer ->
            viewModelScope.launch {
                val playlist = getPlaylistItemsFromItemList(
                    newPlayer.playlist, newPlayer.repository
                )
                mutableUiState.update {
                    it.copy(playList = playlist)
                }
            }
        }
    }

    private fun <T> saveTryEmit(sharedFlow: MutableSharedFlow<T>, value: T) {
        if(sharedFlow.tryEmit(value)) {
            viewModelScope.launch {
                sharedFlow.emit(value)
            }
        }
    }
}