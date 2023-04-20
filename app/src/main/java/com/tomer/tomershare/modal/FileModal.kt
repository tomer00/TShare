package com.tomer.tomershare.modal

import java.io.File
import android.graphics.drawable.Drawable

data class FileModal(var name: String, val file: File, var drawable: Drawable){ var visi : Byte = 8 }