package com.tomer.tomershare.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.PersistableBundle
import com.tomer.tomershare.modal.ModalNetwork

class ShotCutCreator {
    companion object {
        fun Context.createShotCut(modalNetwork: ModalNetwork) {
            val man = this.getSystemService(ShortcutManager::class.java) as ShortcutManager

            val list = man.dynamicShortcuts
            for (i in list)
                if (i.id == modalNetwork.name)
                    return

            val newList = mutableListOf<ShortcutInfo>()
            if (list.size == 4) list.removeAt(0)
            newList.add(
                ShortcutInfo.Builder(this, modalNetwork.name)
                    .setShortLabel(modalNetwork.name)
                    .setLongLabel("Receive ${modalNetwork.name}")
                    .setIcon(Icon.createWithResource(this, Repo.getMid(modalNetwork.icon)))
                    .setIntent(getIntent(modalNetwork))
                    .build()
            )
            list.forEach {
                val name = it.id
                val icon = it.intent?.getStringExtra("icon").orEmpty()
                newList.add(
                    ShortcutInfo.Builder(this, name)
                        .setShortLabel(name)
                        .setLongLabel("Receive $name")
                        .setIcon(Icon.createWithResource(this, Repo.getMid(icon)))
                        .setIntent(getIntent(modalNetwork))
                        .build()
                )
            }
            man.dynamicShortcuts = newList
        }

        private fun getIntent(ip: ModalNetwork): Intent {
            return Intent(Intent.ACTION_VIEW, Uri.parse("rec://new")).apply {
                putExtra("name", ip.name)
                putExtra("icon", ip.icon)
            }
        }
    }
}