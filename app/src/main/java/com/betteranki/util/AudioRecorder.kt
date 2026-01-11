package com.betteranki.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    @Suppress("DEPRECATION")
    fun startRecording(): File? {
        try {
            val fileName = "recording_${System.currentTimeMillis()}.mp3"
            val outputDir = File(context.filesDir, "temp_audio")
            outputDir.mkdirs()
            outputFile = File(outputDir, fileName)
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
            }
            
            return outputFile
        } catch (e: IOException) {
            android.util.Log.e("AudioRecorder", "Failed to start recording", e)
            return null
        }
    }
    
    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            outputFile
        } catch (e: RuntimeException) {
            android.util.Log.e("AudioRecorder", "Failed to stop recording", e)
            outputFile?.delete()
            null
        }
    }
    
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: RuntimeException) {
            android.util.Log.e("AudioRecorder", "Error during cancel", e)
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }
    
    fun isRecording(): Boolean = mediaRecorder != null
}
