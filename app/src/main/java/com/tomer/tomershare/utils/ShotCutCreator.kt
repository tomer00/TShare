package com.tomer.tomershare.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import com.tomer.tomershare.modal.ModalNetwork

class ShotCutCreator {
    companion object {
        fun Context.createShotCut(modalNetwork: ModalNetwork, newList: MutableList<ModalNetwork>) {
            val man = this.getSystemService(ShortcutManager::class.java) as ShortcutManager

            var info :ShortcutInfo

            val list = mutableListOf<ShortcutInfo>()
            if (newList.size == 4) newList.removeAt(0)
            newList.add(ModalNetwork(modalNetwork.address, modalNetwork.name, modalNetwork.isWifi, modalNetwork.icon))

            newList.forEach {
                info = ShortcutInfo.Builder(this, it.name)
                    .setShortLabel(it.name)
                    .setLongLabel("Receive ${it.name}")
                    .setIcon(Icon.createWithResource(this, Repo.getMid(it.icon)))
                    .setIntent(getIntent(it.address))
                    .build()
                list.add(info)
            }
            man.dynamicShortcuts = list
        }

        private fun getIntent(ip: String): Intent {
            return Intent(Intent.ACTION_VIEW, Uri.parse("rec://new")).apply {
                putExtra("ip", ip)

            }
        }
    }
}