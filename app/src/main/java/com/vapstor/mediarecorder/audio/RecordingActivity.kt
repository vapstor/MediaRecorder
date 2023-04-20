package com.vapstor.mediarecorder.audio

import android.os.Bundle

import android.Manifest.permission.RECORD_AUDIO
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val RECORD_AUDIO_PERMISSION: Int = 0

class RecordingActivity : AppCompatActivity(), View.OnClickListener {
    private var mTimerTextView: TextView? = null
    private var mCancelButton: Button? = null
    private var mStopButton: Button? = null
    private var mRecorder: MediaRecorder? = null
    private var mStartTime: Long = 0
    private val amplitudes = IntArray(100)
    private var i = 0
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private val mTickExecutor: Runnable = object : Runnable {
        override fun run() {
            tick()
            mHandler.postDelayed(this, 100)
        }
    }
    private var mOutputFile: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_recorder)
        mTimerTextView = findViewById<View>(R.id.media_recorder_timer) as TextView
        mCancelButton = findViewById<View>(R.id.media_recorder_cancel_button) as Button
        mCancelButton?.setOnClickListener(this)
        mStopButton = findViewById<View>(R.id.media_recorder_share_button) as Button
        mStopButton?.setOnClickListener(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        if (mRecorder != null) {
            stopRecording(false)
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        val permissions: Array<String> = arrayOf(RECORD_AUDIO)
        requestPermissions(this, permissions, RECORD_AUDIO_PERMISSION)
    }

    private fun startRecording() {
        mRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
        mRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mRecorder?.setAudioChannels(1)
        mRecorder?.setAudioEncodingBitRate(96000)
        mRecorder?.setAudioSamplingRate(16000)
        mOutputFile = outputFile
        mOutputFile?.parentFile?.mkdirs()
        mRecorder?.setOutputFile(mOutputFile?.absolutePath)

        try {
            mRecorder?.prepare()
            mRecorder?.start()
            mStartTime = SystemClock.elapsedRealtime()
            mHandler.postDelayed(mTickExecutor, 100)
            Log.d("Voice Recorder", "started recording to " + mOutputFile?.absolutePath)
        } catch (e: Throwable) {
            Log.e("Voice Recorder", "prepare() failed " + e.message)
        }
    }

    protected fun stopRecording(saveFile: Boolean) {
        try {
            mRecorder?.stop()
            mRecorder?.release()
            mRecorder = null
            mStartTime = 0
            mHandler.removeCallbacks(mTickExecutor)
            if (!saveFile && mOutputFile != null) {
                mOutputFile?.delete()
            }
        } catch (e: Throwable) {
            Log.e("Voice Recorder", "stop() or release() failed " + e.message)
        }
    }

    private val outputFile: File
        get() {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
            return File(
                getExternalFilesDir("media")?.absolutePath.toString()
                        + "/Voice_Recorder/RECORDING_"
                        + dateFormat.format(Date())
                        + ".m4a"
            )
        }

    private fun tick() {
        val time = if ((mStartTime < 0)) 0 else (SystemClock.elapsedRealtime() - mStartTime)
        val minutes = (time / 60000).toInt()
        val seconds = (time / 1000).toInt() % 60
        val milliseconds = (time / 100).toInt() % 10
        mTimerTextView?.text =
            minutes.toString() + ":" + (if (seconds < 10) "0$seconds" else seconds) + "." + milliseconds
        if (mRecorder != null) {
            amplitudes[i] = mRecorder?.maxAmplitude ?: 0
            //Log.d("Voice Recorder","amplitude: "+(amplitudes[i] * 100 / 32767));
            if (i >= amplitudes.size - 1) {
                i = 0
            } else {
                ++i
            }
        }
    }

    override fun onClick(view: View) {
        if(!checkPermission()) {
            requestPermission()
        } else {
            when (view.id) {
                R.id.media_recorder_cancel_button -> {
                    if ((view as Button).text.equals("Cancelar")) {
                        stopRecording(false)
                        mStartTime = -1
                        tick()
                        view.text = "Iniciar"
                        findViewById<Button>(R.id.media_recorder_share_button).isEnabled = false
                        findViewById<Button>(R.id.media_recorder_share_button).setTextColor(
                            ContextCompat.getColor(this, R.color.white80)
                        )
                    } else {
                        startRecording()
                        findViewById<Button>(R.id.media_recorder_share_button).isEnabled = true
                        (findViewById<Button>(R.id.media_recorder_share_button)).setTextColor(
                            ContextCompat.getColor(this, R.color.white)
                        )
                        view.text = "Cancelar"
                    }
                }

                R.id.media_recorder_share_button -> {
                    if ((view as Button).text.equals("Compartilhar")) {
                        shareAudio()
                        view.text = "Salvar"
                        view.isEnabled = false
                    } else {
                        view.text = "Compartilhar"
                        stopRecording(true)
                    }
                }
            }
        }
    }

    private fun shareAudio() {
        val filePath = mOutputFile?.absolutePath ?: ""
        val file = File(filePath)
        MediaScannerConnection.scanFile(
            this, arrayOf(file.toString()),
            null, null
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".provider",
            file
        )
        shareIntent.type = "audio/mp4"
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Gravação de Áudio");
        shareIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            startActivity(shareIntent)
        } catch (ex: ActivityNotFoundException) {
            ex.printStackTrace()
        }
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION) {
            if (grantResults[RECORD_AUDIO_PERMISSION] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                // User denied Permission.
            }
        }
    }
}