package com.example.urldownloader

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urldownloader.databinding.ItemVideoBinding
import com.example.urldownloader.databinding.ItemVideoListBinding

class VideoAdapter(
    private val context: Context,
    private var videos: List<Video>,
    private var isGridView: Boolean,
    private val onItemClick: (Video) -> Unit,

    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemVideoBinding.inflate(LayoutInflater.from(context), parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemVideoListBinding.inflate(LayoutInflater.from(context), parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val video = videos[position]
        when (holder) {
            is GridViewHolder -> holder.bind(video)
            is ListViewHolder -> holder.bind(video)
        }
    }

    override fun getItemCount(): Int = videos.size

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    fun updateVideos(newVideos: List<Video>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    fun setViewType(isGrid: Boolean) {
        isGridView = isGrid
        notifyDataSetChanged()
    }

    inner class GridViewHolder(private val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: Video) {
            binding.videoTitle.text = video.name

            Glide.with(context)
                .load(video.thumbnailPath ?: R.drawable.placeholder_image)
                .centerCrop()
                .placeholder(R.drawable.placeholder_image)
                .into(binding.videoThumbnail)

            itemView.setOnClickListener { onItemClick(video) }
        }
    }

    inner class ListViewHolder(private val binding: ItemVideoListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: Video) {
            binding.videoTitle.text = video.name


            Glide.with(context)
                .load(video.thumbnailPath ?: R.drawable.placeholder_image)
                .centerCrop()
                .placeholder(R.drawable.placeholder_image)
                .into(binding.videoThumbnail)

            itemView.setOnClickListener { onItemClick(video) }
        }
    }
}