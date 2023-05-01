package com.tomer.tomershare.modal

import android.graphics.drawable.Drawable
import java.io.File

data class FileModal(var name: String, val file: File, var drawable: Drawable){ var visi : Byte = 8 }