package com.lkeducation.lkpractice

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal class ReferencePlaybackController {
    private data class Binding(
        val owner: Any,
        val playFromStart: () -> Unit,
        val pause: () -> Unit,
    )

    private var binding: Binding? = null
    private var playRequested = false
    internal val playbackRequested: Boolean
        get() = playRequested

    internal fun bind(
        owner: Any,
        playFromStart: () -> Unit,
        pause: () -> Unit,
    ) {
        binding = Binding(owner, playFromStart, pause)
        if (playRequested) runCatching(playFromStart)
    }

    internal fun unbind(owner: Any) {
        if (binding?.owner === owner) binding = null
    }

    fun playFromStart() {
        playRequested = true
        binding?.playFromStart?.let { runCatching(it) }
    }

    fun pause() {
        playRequested = false
        binding?.pause?.let { runCatching(it) }
    }
}

@Composable
fun ReferenceVideoPanel(task: PracticeTask, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ReferenceVideoSurface(
                task = task,
                modifier = Modifier.fillMaxWidth().height(210.dp),
            )
            Text(
                "Play only the assigned clip. Complete a repetition after the assigned work is finished.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun ReferenceVideoSurface(
    task: PracticeTask,
    modifier: Modifier = Modifier,
    playbackController: ReferencePlaybackController? = null,
    interactive: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (task.sourceType) {
            SourceType.LOCAL -> LocalVideoPlayer(task, playbackController)
            SourceType.YOUTUBE -> YouTubePlayer(task, playbackController)
            SourceType.NONE -> Text(
                "No reference video for this mission.",
                color = Color.White,
            )
        }
        if (!interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Teacher reference locked while recording"
                    },
            )
        }
    }
}

@Composable
private fun YouTubePlayer(
    task: PracticeTask,
    playbackController: ReferencePlaybackController?,
) {
    val videoId = remember(task.source) { youtubeVideoId(task.source) }
    if (videoId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This YouTube link could not be opened.", color = Color.White)
        }
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember(videoId, task.startSeconds, task.endSeconds) {
        mutableStateOf<YouTubePlayer?>(null)
    }
    var playerError by remember(videoId) { mutableStateOf<String?>(null) }
    val bindingOwner = remember(videoId, task.startSeconds, task.endSeconds) { Any() }

    DisposableEffect(playbackController, player, bindingOwner) {
        val readyPlayer = player
        if (playbackController != null && readyPlayer != null) {
            playbackController.bind(
                owner = bindingOwner,
                playFromStart = {
                    readyPlayer.loadVideo(videoId, task.startSeconds.toFloat())
                },
                pause = {
                    readyPlayer.pause()
                },
            )
        }
        onDispose {
            playbackController?.unbind(bindingOwner)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    contentDescription = "Teacher reference video"
                },
            factory = { context ->
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)
                    val optionsBuilder = IFramePlayerOptions.Builder(context)
                        .controls(1)
                        .autoplay(0)
                        .rel(0)
                        .fullscreen(0)
                        .start(task.startSeconds)
                    task.endSeconds?.let(optionsBuilder::end)
                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                player = youTubePlayer
                                playerError = null
                                youTubePlayer.cueVideo(videoId, task.startSeconds.toFloat())
                                if (playbackController?.playbackRequested == true) {
                                    youTubePlayer.loadVideo(
                                        videoId,
                                        task.startSeconds.toFloat(),
                                    )
                                }
                            }

                            override fun onCurrentSecond(
                                youTubePlayer: YouTubePlayer,
                                second: Float,
                            ) {
                                val end = task.endSeconds ?: return
                                if (second >= end) youTubePlayer.pause()
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError,
                            ) {
                                playerError = youtubePlayerErrorMessage(error)
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState,
                            ) {
                                if (state == PlayerConstants.PlayerState.PLAYING) {
                                    playerError = null
                                }
                            }
                        },
                        true,
                        optionsBuilder.build(),
                        videoId,
                    )
                }
            },
            onRelease = { view ->
                if (player != null) player = null
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            },
        )
        playerError?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message,
                    color = Color.White,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

internal fun youtubePlayerErrorMessage(error: PlayerConstants.PlayerError): String =
    when (error) {
        PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
            "This YouTube video could not be found."
        PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
            "This video does not allow embedded playback."
        PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER ->
            "YouTube rejected the app connection. Please update LK Practice."
        PlayerConstants.PlayerError.HTML_5_PLAYER ->
            "YouTube could not start video playback on this device."
        PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
            "This YouTube assignment link is invalid."
        PlayerConstants.PlayerError.UNKNOWN ->
            "The YouTube reference could not be loaded."
    }

@Composable
private fun LocalVideoPlayer(
    task: PracticeTask,
    playbackController: ReferencePlaybackController?,
) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    val clipKey = "${task.source}|${task.startSeconds}|${task.endSeconds}"
    val bindingOwner = remember(clipKey) { Any() }

    DisposableEffect(playbackController, videoView, bindingOwner) {
        val view = videoView
        if (playbackController != null && view != null) {
            playbackController.bind(
                owner = bindingOwner,
                playFromStart = {
                    view.seekTo(task.startSeconds * 1_000)
                    view.start()
                },
                pause = {
                    if (view.isPlaying) view.pause()
                },
            )
        }
        onDispose {
            playbackController?.unbind(bindingOwner)
        }
    }

    LaunchedEffect(videoView, clipKey) {
        while (isActive) {
            val view = videoView
            val endMillis = task.endSeconds?.times(1_000)
            if (view != null && endMillis != null && view.isPlaying && view.currentPosition >= endMillis) {
                view.pause()
                view.seekTo(task.startSeconds * 1_000)
            }
            delay(100)
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "Teacher reference video"
            },
        factory = { context ->
            VideoView(context).apply {
                tag = clipKey
                setMediaController(MediaController(context))
                setVideoURI(task.source.toUri())
                setOnPreparedListener {
                    seekTo(task.startSeconds * 1_000)
                }
                videoView = this
            }
        },
        update = { view ->
            if (view.tag != clipKey) {
                view.tag = clipKey
                view.setVideoURI(task.source.toUri())
                view.setOnPreparedListener { view.seekTo(task.startSeconds * 1_000) }
            }
            videoView = view
        },
        onRelease = { view ->
            playbackController?.unbind(bindingOwner)
            if (videoView === view) videoView = null
            view.stopPlayback()
        },
    )
}
