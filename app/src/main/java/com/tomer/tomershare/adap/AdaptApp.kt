package com.tomer.tomershare.adap

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.AppRowBinding
import com.tomer.tomershare.modal.AppModal

class AdaptApp(private val AL: AppClickLis) : RecyclerView.Adapter<AdaptApp.AppHolder>() {

    val l = mutableListOf<AppModal>()

    override fun getItemCount() = l.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.app_row, parent, false)
        return AppHolder(AppRowBinding.bind(v), AL)
    }

    override fun onBindViewHolder(holder: AppHolder, position: Int) {
        val media: AppModal = l[position]
        holder.b.apply {
            appTumb.setImageDrawable(media.drawable)
            appIndi.visibility = media.visi.toInt()
            "${media.name}\n${media.size}".also { appNAme.text = it }
        }
    }

    interface AppClickLis {
        fun onAppClick(position: Int, indic:ImageView, thumb:ImageView)
    }

    inner class AppHolder(val b: AppRowBinding, private val AL: AppClickLis) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener {
                AL.onAppClick(adapterPosition,b.appIndi,b.appTumb)
            }
        }
    }
}