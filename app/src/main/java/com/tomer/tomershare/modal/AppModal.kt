package com.tomer.tomershare.modal

import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

data class AppModal(val name: String, val size: String, val file: File, val drawable: Drawable) {
    var visi: Byte = 8
}