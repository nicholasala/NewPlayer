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

package net.newpipe.newplayer.ui.videoplayer

import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.newpipe.newplayer.R
import net.newpipe.newplayer.ui.theme.VideoPlayerTheme
import net.newpipe.newplayer.ui.videoplayer.gesture_ui.FastSeekVisualFeedback
import net.newpipe.newplayer.ui.videoplayer.gesture_ui.TouchSurface
import net.newpipe.newplayer.ui.videoplayer.gesture_ui.TouchedPosition

private const val TAG = "TouchUi"



const val DELAY_UNTIL_SHOWING_UI_AFTER_TOUCH_IN_MS: Long = 200
const val SEEK_ANIMATION_DURATION_IN_MS = 400
const val FAST_SEEKMODE_DURATION = 500L
const val SEEK_ANIMATION_FADE_IN = 200
const val SEEK_ANIMATION_FADE_OUT = 500

@Composable
fun GestureUI(
    modifier: Modifier,
    hideUi: () -> Unit,
    showUi: () -> Unit,
    uiVissible: Boolean,
    fullscreen: Boolean,
    fastSeekSeconds: Int,
    switchToFullscreen: () -> Unit,
    switchToEmbeddedView: () -> Unit,
    embeddedDraggedDownBy: (Float) -> Unit,
    fastSeekBackward: () -> Unit,
    fastSeekForward: () -> Unit,
) {
    val defaultOnRegularTap = {
        if (uiVissible) {
            hideUi()
        } else {
            showUi()
        }
    }

    var fastSeekModeBackward by remember {
        mutableStateOf(false)
    }

    var fastSeekModeForward by remember {
        mutableStateOf(false)
    }

    val composeScope = rememberCoroutineScope()

    val doForwardSeek = {
        fastSeekModeForward = true
        composeScope.launch {
            delay(FAST_SEEKMODE_DURATION)
            fastSeekModeForward = false
        }
        fastSeekForward()
    }

    var fastSeekModeTimeout: Job? = null
    val resetFastSeekModeEnd = {
        fastSeekModeTimeout?.cancel()
        fastSeekModeTimeout = composeScope.launch {
            delay(FAST_SEEKMODE_DURATION)
            fastSeekModeBackward = false
        }
    }

    val doBackwardSeek = {
        fastSeekModeBackward = true
        resetFastSeekModeEnd()
        fastSeekBackward()
    }

    if (fullscreen) {
        Row(modifier = modifier) {
            TouchSurface(
                modifier = Modifier
                    .weight(1f),
                onRegularTap = defaultOnRegularTap,
                onDoubleTab = doBackwardSeek
            ) {
                FadedAnimationForSeekFeedback(visible = fastSeekModeBackward) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            seconds = fastSeekSeconds,
                            backwards = true,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
            TouchSurface(
                modifier = Modifier
                    .weight(1f),
                onRegularTap = defaultOnRegularTap,
                onMovement = { movement ->
                    if (0 < movement.y) {
                        switchToEmbeddedView()
                    }
                }
            )
            TouchSurface(
                modifier = Modifier
                    .weight(1f),
                onRegularTap = defaultOnRegularTap,
                onDoubleTab = doForwardSeek
            ) {
                FadedAnimationForSeekFeedback(visible = fastSeekModeForward) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            modifier = Modifier.align(Alignment.CenterStart),
                            seconds = fastSeekSeconds,
                            backwards = false
                        )
                    }
                }
            }
        }
    } else { // (!fullscreen)
        val handleDownwardMovement = { movement: TouchedPosition ->
            Log.d(TAG, "${movement.x}:${movement.y}")
            if (0 < movement.y) {
                embeddedDraggedDownBy(movement.y)
            } else {
                switchToFullscreen()
            }
        }

        Row(modifier = modifier) {
            TouchSurface(
                modifier = Modifier
                    .weight(1f),
                onDoubleTab = doBackwardSeek,
                onRegularTap = defaultOnRegularTap,
                onMovement = handleDownwardMovement
            ) {
                FadedAnimationForSeekFeedback(visible = fastSeekModeBackward) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            modifier = Modifier.align(Alignment.Center),
                            seconds = fastSeekSeconds,
                            backwards = true
                        )
                    }
                }
            }
            TouchSurface(
                modifier = Modifier
                    .weight(1f),
                onDoubleTab = doForwardSeek,
                onRegularTap = defaultOnRegularTap,
                onMovement = handleDownwardMovement
            ) {
                FadedAnimationForSeekFeedback(visible = fastSeekModeForward) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            modifier = Modifier.align(Alignment.Center),
                            seconds = fastSeekSeconds,
                            backwards = false
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FadedAnimationForSeekFeedback(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(SEEK_ANIMATION_FADE_IN)),
        exit = fadeOut(animationSpec = tween(SEEK_ANIMATION_FADE_OUT))
    ) {
        content()
    }
}



@Preview(device = "spec:width=1080px,height=600px,dpi=440,orientation=landscape")
@Composable
fun FullscreenGestureUIPreview() {
    VideoPlayerTheme {
        Surface(modifier = Modifier.wrapContentSize(), color = Color.Black) {
            GestureUI(
                modifier = Modifier,
                hideUi = { },
                showUi = { },
                uiVissible = false,
                fullscreen = true,
                fastSeekSeconds = 10,
                switchToFullscreen = { println("switch to fullscreen") },
                switchToEmbeddedView = { println("switch to embedded") },
                embeddedDraggedDownBy = { println("embedded dragged down") },
                fastSeekBackward = { println("fast seek backward") },
                fastSeekForward = { println("fast seek forward") })
        }
    }
}

@Preview(device = "spec:width=600px,height=400px,dpi=440,orientation=landscape")
@Composable
fun EmbeddedGestureUIPreview() {
    VideoPlayerTheme {
        Surface(modifier = Modifier.wrapContentSize(), color = Color.Black) {
            GestureUI(
                modifier = Modifier,
                hideUi = { },
                showUi = { },
                uiVissible = false,
                fullscreen = false,
                fastSeekSeconds = 10,
                switchToFullscreen = { println("switch to fullscreen") },
                switchToEmbeddedView = { println("switch to embedded") },
                embeddedDraggedDownBy = { println("embedded dragged down") },
                fastSeekBackward = { println("fast seek backward") },
                fastSeekForward = { println("fast seek forward") })
        }
    }
}

