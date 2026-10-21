package com.inknote.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inknote.app.data.AudioRecording
import com.inknote.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AudioManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repo: Repository,
) {
    // ---- Kayıt Durumu ----
    var isRecording by mutableStateOf(false); private set
    var recordingDurationMs by mutableLongStateOf(0L); private set
    var recordingStartTimeMs by mutableLongStateOf(0L); private set
    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingJob: Job? = null
    private var activeNotebookId: String? = null

    // ---- Oynatma & Senkronize Replay Durumu ----
    var isPlaying by mutableStateOf(false); private set
    var isPaused by mutableStateOf(false); private set
    var isReplayMode by mutableStateOf(false); private set
    var playbackPositionMs by mutableLongStateOf(0L); private set
    var playbackDurationMs by mutableLongStateOf(0L); private set
    var playbackSpeed by mutableFloatStateOf(1.0f); private set
    var activeRecording by mutableStateOf<AudioRecording?>(null); private set

    val recordings = mutableStateListOf<AudioRecording>()
    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun loadRecordings(notebookId: String) {
        activeNotebookId = notebookId
        recordings.clear()
        recordings.addAll(repo.loadAudioRecordings(notebookId))
    }

    /**
     * Ses kaydı esnasında kalemin o an kaçıncı milisaniyede çizildiğini döndürür.
     */
    fun currentAudioOffsetMs(): Long {
        if (!isRecording) return 0L
        return (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(0L)
    }

    /**
     * Mikrofon ile ses kaydını başlatır.
     */
    fun startRecording(notebookId: String): Boolean {
        if (isRecording) return false
        stopPlayback()
        activeNotebookId = notebookId

        val audioDir = File(context.filesDir, "audio_nb_$notebookId").apply { mkdirs() }
        val recId = UUID.randomUUID().toString().take(8)
        val file = File(audioDir, "rec_${recId}.m4a")
        currentRecordingFile = file

        return try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            recorder = rec
            isRecording = true
            recordingStartTimeMs = System.currentTimeMillis()
            recordingDurationMs = 0L

            recordingJob = scope.launch(Dispatchers.Main) {
                while (isActive && isRecording) {
                    recordingDurationMs = System.currentTimeMillis() - recordingStartTimeMs
                    delay(100)
                }
            }
            true
        } catch (_: Exception) {
            cleanUpRecorder()
            false
        }
    }

    /**
     * Ses kaydını durdurur ve deftere kaydeder.
     */
    fun stopRecording(): AudioRecording? {
        if (!isRecording) return null
        recordingJob?.cancel()
        val duration = System.currentTimeMillis() - recordingStartTimeMs
        val file = currentRecordingFile
        val nbId = activeNotebookId ?: ""

        try {
            recorder?.stop()
        } catch (_: Exception) {}
        cleanUpRecorder()

        if (file != null && file.exists() && duration > 500 && nbId.isNotEmpty()) {
            val item = AudioRecording(
                id = UUID.randomUUID().toString().take(8),
                notebookId = nbId,
                title = "Ses Kaydı ${recordings.size + 1}",
                filePath = file.absolutePath,
                createdAt = System.currentTimeMillis(),
                durationMs = duration
            )
            recordings.add(0, item)
            repo.saveAudioRecordings(nbId, recordings.toList())
            activeRecording = item
            isReplayMode = true
            return item
        }
        return null
    }

    private fun cleanUpRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
    }

    // ============================================================ Oynatma & Replay

    fun startReplay(recording: AudioRecording) {
        stopPlayback()
        activeRecording = recording
        isReplayMode = true
        val file = File(recording.filePath)
        if (!file.exists()) return

        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            playbackDurationMs = mp.duration.toLong()
            playbackPositionMs = 0L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mp.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
            }

            mp.setOnCompletionListener {
                isPlaying = false
                isPaused = false
                playbackPositionMs = playbackDurationMs
                playbackJob?.cancel()
            }

            mp.start()
            player = mp
            isPlaying = true
            isPaused = false
            startPlaybackTicker()
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    fun togglePlayPause() {
        val mp = player ?: run {
            activeRecording?.let { startReplay(it) }
            return
        }
        if (isPlaying) {
            mp.pause()
            isPlaying = false
            isPaused = true
            playbackJob?.cancel()
        } else {
            mp.start()
            isPlaying = true
            isPaused = false
            startPlaybackTicker()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, playbackDurationMs.coerceAtLeast(1L))
        playbackPositionMs = target
        player?.seekTo(target.toInt())
    }

    fun skipBy(deltaMs: Long) {
        seekTo(playbackPositionMs + deltaMs)
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            player?.let {
                if (it.isPlaying) {
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                }
            }
        }
    }

    fun closeReplay() {
        stopPlayback()
        isReplayMode = false
        activeRecording = null
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        isPlaying = false
        isPaused = false
        playbackPositionMs = 0L
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Main) {
            while (isActive && isPlaying) {
                player?.let {
                    if (it.isPlaying) {
                        playbackPositionMs = it.currentPosition.toLong()
                    }
                }
                delay(30)
            }
        }
    }

    fun deleteRecording(rec: AudioRecording) {
        if (activeRecording?.id == rec.id) {
            stopPlayback()
            isReplayMode = false
            activeRecording = null
        }
        recordings.remove(rec)
        activeNotebookId?.let { repo.saveAudioRecordings(it, recordings.toList()) }
        runCatching { File(rec.filePath).delete() }
    }
}
