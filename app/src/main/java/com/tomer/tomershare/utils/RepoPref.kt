package com.tomer.tomershare.utils

import android.content.Context
import com.tomer.tomershare.modal.ModalNetwork
import java.util.regex.Pattern

class RepoPref(private val context: Context) {
    private val pref by lazy { context.getSharedPreferences("prefInets", Context.MODE_PRIVATE) }


    fun getLast(): ModalNetwork {
        val str = pref.getString("last", "").toString()
        return if (str.isEmpty()) ModalNetwork("", "", true, "1")
        else {
            val s = Pattern.compile(",,").split(str, 4)
            ModalNetwork(s[0], s[1], s[2].toBoolean(), s[3])
        }
    }

    fun saveLast(modalNetwork: ModalNetwork) {
        val str = "${modalNetwork.address},,${modalNetwork.name},,${modalNetwork.isWifi},,${modalNetwork.icon}"
        pref.edit().putString("last", str).apply()
    }

    fun getAllNetwork(): List<ModalNetwork> {
        val list = mutableListOf<ModalNetwork>()
        val str = pref.getString("data", "").toString()

        val sts = Pattern.compile("<").split(str)

        sts.forEach {
            if (it.isNotEmpty()) {
                val s = Pattern.compile(",,").split(it.toString(), 4)
                list.add(ModalNetwork(s[0], s[1], s[2].toBoolean(), s[3]))
            }
        }
        return list
    }

    fun setAllNetwork(list: List<ModalNetwork>) {
        val str = StringBuilder()
        list.forEach {
            str.append("${it.address},,${it.name},,${it.isWifi},,${it.icon}")
            str.append("<")
        }
        if (str.isNotEmpty())
            str.deleteCharAt(str.length - 1)
        pref.edit().putString("data", str.toString()).apply()
    }

    fun saveShotCuts(list: List<ModalNetwork>) {
        val str = StringBuilder()
        list.forEach {
            str.append("${it.address},,${it.name},,${it.isWifi},,${it.icon}")
            str.append("<")
        }
        if (str.isNotEmpty())
            str.deleteCharAt(str.length - 1)
        pref.edit().putString("shot", str.toString()).apply()
    }

    fun getShortcut(): List<ModalNetwork> {
        val list = mutableListOf<ModalNetwork>()
        val str = pref.getString("shot", "").toString()

        val sts = Pattern.compile("<").split(str)

        sts.forEach {
            if (it.isNotEmpty()) {
                val s = Pattern.compile(",,").split(it.toString(), 4)
                list.add(ModalNetwork(s[0], s[1], s[2].toBoolean(), s[3]))
            }
        }
        return list
    }
}