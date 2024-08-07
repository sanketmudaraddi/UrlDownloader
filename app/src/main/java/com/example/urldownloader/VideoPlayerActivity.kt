package com.example.urldownloader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val videoView: VideoView = findViewById(R.id.videoView)
        val videoUrl = intent.getStringExtra("videoUrl")

        Log.d("VideoPlayerActivity", "Video URL: $videoUrl")

        if (videoUrl != null) {
            val uri = Uri.parse(videoUrl)
            videoView.setVideoURI(uri)
            val mediaController = MediaController(this)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)
            videoView.start()
        } else {
            Log.e("VideoPlayerActivity", "No video URL provided")
        }
    }
}