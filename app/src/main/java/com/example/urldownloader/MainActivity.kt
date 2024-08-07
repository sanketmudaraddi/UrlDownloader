package com.example.urldownloader

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.urldownloader.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var videoAdapter: VideoAdapter
    private var isGridView = true
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val BASE_URL = "http://93.127.217.210:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupToggleButton()

        binding.downloadButton.setOnClickListener {
            val url = binding.urlEditText.text.toString()
            if (url.isNotEmpty()) {
                if (url.contains("dailymotion.com") || url.contains("dai.ly")) {
                    fetchDailymotionUrl(url)
                } else {
                    downloadVideo(url)
                }
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }

        loadVideos()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(this, emptyList(), isGridView) { video ->
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("videoUrl", BASE_URL + video.url)
            startActivity(intent)
        }
        binding.videoRecyclerView.adapter = videoAdapter
        setLayoutManager()
    }

    private fun setLayoutManager() {
        binding.videoRecyclerView.layoutManager = if (isGridView) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun setupToggleButton() {
        binding.toggleViewButton.setOnClickListener {
            isGridView = !isGridView
            setLayoutManager()
            videoAdapter.setViewType(isGridView)
            binding.toggleViewButton.setImageResource(
                if (isGridView) R.drawable.ic_list_view else R.drawable.ic_grid_view
            )
        }
    }

    private fun loadVideos() {
        coroutineScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    fetchVideos("$BASE_URL/videos")
                }
                videoAdapter.updateVideos(videos)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading videos", e)
                Toast.makeText(this@MainActivity, "Failed to load videos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchVideos(requestUrl: String): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = URL(requestUrl)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream.bufferedReader().use { it.readText() }
                parseVideos(inputStream)
            } else {
                Log.e("FetchVideos", "HTTP error code: $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("FetchVideos", "Exception: ${e.message}")
            emptyList()
        }
    }

    private suspend fun parseVideos(jsonResponse: String): List<Video> = withContext(Dispatchers.Default) {
        try {
            val jsonArray = JSONArray(jsonResponse)
            val videoList = mutableListOf<Video>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val url = jsonObject.getString("url")
                val name = jsonObject.getString("name")
                val thumbnailPath = getCachedThumbnailPath(url) ?: generateAndCacheThumbnail(url)
                videoList.add(Video(url, name, thumbnailPath))
            }

            videoList
        } catch (e: Exception) {
            Log.e("ParseVideos", "Error parsing JSON: ${e.message}")
            emptyList()
        }
    }

    private fun getCachedThumbnailPath(videoUrl: String): String? {
        val fileName = "thumbnail_${videoUrl.hashCode()}.jpg"
        val file = File(cacheDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    private suspend fun generateAndCacheThumbnail(videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource("$BASE_URL$videoUrl", HashMap<String, String>())
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val fileName = "thumbnail_${videoUrl.hashCode()}.jpg"
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("Thumbnail", "Error generating thumbnail", e)
            null
        }
    }

    private fun downloadVideo(videoUrl: String) {
        coroutineScope.launch {
            try {
                val video = withContext(Dispatchers.IO) {
                    downloadVideoFromServer(videoUrl)
                }
                if (video != null) {
                    Toast.makeText(this@MainActivity, "Download complete", Toast.LENGTH_SHORT).show()
                    loadVideos()
                } else {
                    Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DownloadVideo", "Error downloading video", e)
                Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadVideoFromServer(videoUrl: String): Video? = withContext(Dispatchers.IO) {
        try {
            val requestUrl = "$BASE_URL/download"
            val url = URL(requestUrl)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")

            val requestBody = """{"url":"$videoUrl"}"""
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseVideo(response)
            } else {
                Log.e("DownloadVideo", "HTTP error code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e("DownloadVideo", "Exception: ${e.message}")
            null
        }
    }

    private suspend fun parseVideo(jsonResponse: String): Video? {
        return try {
            val jsonObject = org.json.JSONObject(jsonResponse)
            val url = jsonObject.getString("url")
            val name = jsonObject.getString("name")
            val thumbnailPath = generateAndCacheThumbnail(url)
            Video(url, name, thumbnailPath)
        } catch (e: Exception) {
            Log.e("ParseVideo", "Error parsing JSON: ${e.message}")
            null
        }
    }

    private fun fetchDailymotionUrl(dailymotionUrl: String) {
        coroutineScope.launch {
            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    getDailymotionDownloadUrl(dailymotionUrl)
                }
                if (videoUrl != null) {
                    downloadVideo(videoUrl)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to fetch Dailymotion video URL", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FetchDailymotionUrl", "Error fetching Dailymotion video URL", e)
                Toast.makeText(this@MainActivity, "Failed to fetch Dailymotion video URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getDailymotionDownloadUrl(dailymotionUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = """{"url":"$dailymotionUrl"}"""
            val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$BASE_URL/get_dailymotion_url")
                .post(requestBody)
                .build()
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val jsonObject = JSONObject(responseBody)
            jsonObject.getString("video_url")
        } catch (e: Exception) {
            Log.e("GetDailymotionUrl", "Error: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}
