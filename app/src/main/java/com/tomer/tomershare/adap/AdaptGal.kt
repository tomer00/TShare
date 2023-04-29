package com.tomer.tomershare.adap

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.GalRowBinding
import com.tomer.tomershare.modal.GalModal

@SuppressLint("CheckResult")
class AdaptGal(private val GL: GalClickLis, val l: List<GalModal>) : RecyclerView.Adapter<AdaptGal.GalHolder>() {
    private val options = RequestOptions()

    init {
        options.placeholder(R.color.gary_fg)
        options.override(120)
    }


    override fun getItemCount() = l.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.gal_row, parent, false)
        return GalHolder(GalRowBinding.bind(v), GL)
    }

    override fun onBindViewHolder(holder: GalHolder, position: Int) {
        val media: GalModal = l[position]
        holder.b.apply {
            if (media.isVid) galvid.visibility = View.VISIBLE
            else galvid.visibility = View.GONE

            if (media.visi) {
                galSend.scaleX = 1f
                galSend.scaleY = 1f
            } else {
                galSend.scaleX = 0f
                galSend.scaleY = 0f
            }
            Glide.with(this.galThumb).asBitmap().load(media.file).apply(options).into(galThumb)
        }
    }

    interface GalClickLis {
        fun onGalClick(position: Int, img: ImageView)
    }

    inner class GalHolder(val b: GalRowBinding, GL: GalClickLis) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener { GL.onGalClick(adapterPosition, b.galSend) }
        }
    }
}