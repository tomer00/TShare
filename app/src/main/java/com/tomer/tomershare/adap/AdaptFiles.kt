package com.tomer.tomershare.adap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.FileRowBinding
import com.tomer.tomershare.modal.FileModal

class AdaptFiles(private val FL: FIleClickLis) : ListAdapter<FileModal, AdaptFiles.FileHolder>(FileUtils()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.file_row, parent, false)
        return FileHolder(FileRowBinding.bind(v), FL)
    }

    override fun onBindViewHolder(holder: FileHolder, position: Int) {
        val mo = getItem(position)
        holder.b.apply {
            img.setImageDrawable(mo.drawable)
            fileNme.text = mo.name
            fileINdi.visibility = mo.visi.toInt()
        }

    }

    interface FIleClickLis {
        fun onFileClick(position: Int, indic: View, thumb: ImageView)
        fun onFileLongClick(position: Int)
    }

    inner class FileHolder(val b: FileRowBinding, fl: FIleClickLis) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener { fl.onFileClick(adapterPosition, b.fileINdi,b.img) }
            b.root.setOnLongClickListener {
                fl.onFileLongClick(adapterPosition)
                return@setOnLongClickListener true
            }
        }
    }

    class FileUtils : DiffUtil.ItemCallback<FileModal>() {
        override fun areItemsTheSame(oldItem: FileModal, newItem: FileModal) =  oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: FileModal, newItem: FileModal) = oldItem.visi == newItem.visi
    }
}


