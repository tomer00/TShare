package com.tomer.tomershare.adap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tomer.tomershare.R
import com.tomer.tomershare.databinding.TransRowBinding
import com.tomer.tomershare.modal.TransferModal

class AdaptMsg(private val context: Context, private val onCancel: () -> Unit) : ListAdapter<TransferModal, AdaptMsg.MsgHolder>(MsgDIffUtils()) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.trans_row, parent, false)
        return MsgHolder(TransRowBinding.bind(v), onCancel)
    }

    override fun onBindViewHolder(holder: MsgHolder, position: Int) {
        val mod = getItem(position)
        holder.b.fileName.text = mod.fileName


        if (mod.isTrans) {
            holder.b.imgStatus.visibility = View.VISIBLE
            holder.b.clickHandler.visibility = View.VISIBLE
            val anim = AnimationUtils.loadAnimation(this.context, R.anim.scale_anim)
            holder.b.root.clearAnimation()
            holder.b.root.startAnimation(anim)
        } else {
            holder.b.imgStatus.visibility = View.GONE
            holder.b.clickHandler.visibility = View.GONE
            val anim = AnimationUtils.loadAnimation(this.context, R.anim.rev_scale)
            holder.b.root.clearAnimation()
            holder.b.root.startAnimation(anim)
        }
    }

    inner class MsgHolder(val b: TransRowBinding, private val onCancel: () -> Unit) : RecyclerView.ViewHolder(b.root) {
        init {
            b.clickHandler.setOnClickListener {
                onCancel()
            }
        }
    }

    class MsgDIffUtils : DiffUtil.ItemCallback<TransferModal>() {
        override fun areItemsTheSame(oldItem: TransferModal, newItem: TransferModal) = oldItem.fileName == newItem.fileName
        override fun areContentsTheSame(oldItem: TransferModal, newItem: TransferModal) = oldItem.isTrans == newItem.isTrans
    }

}

