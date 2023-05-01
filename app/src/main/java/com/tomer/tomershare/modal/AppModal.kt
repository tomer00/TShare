package com.tomer.tomershare.modal

import android.graphics.drawable.Drawable
import java.io.File

data class AppModal(var name: String, val size: String, var file: File, val drawable: Drawable) {
    var visi: Byte = 8
}