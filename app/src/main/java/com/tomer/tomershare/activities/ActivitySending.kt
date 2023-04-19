package com.tomer.tomershare.activities

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.tomer.tomershare.databinding.ActivitySendingBinding
import com.tomer.tomershare.utils.PathUtils.Companion.getFilePath
import com.tomer.tomershare.utils.PathUtils.Companion.getImagePath
import com.tomer.tomershare.utils.PathUtils.Companion.getVideoPath
import kotlin.concurrent.thread

class ActivitySending : AppCompatActivity() {

    private val b by lazy { ActivitySendingBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)


        val action = intent.action
        Log.d("TAG--", "onCreate: ${intent.type.toString()}")

        if (Intent.ACTION_SEND == action) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                thread {
                    handleFile(uri, contentResolver)

                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uis = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            thread {
                for (uri in uis!!)
                    handleFile(uri, contentResolver)
            }
        }

    }

    private fun handleFile(uri: Uri, contentResolver: ContentResolver) {

        when (intent.type.toString()) {
            "video/*" -> {
                Log.d("TAG--", "onCreate: ${uri.getVideoPath(this.applicationContext)}")
                Log.d("TAG--", "onCreate: ${uri.getFilePath(this.applicationContext)}")
            }
            "image/*" -> {
                Log.d("TAG--", "onCreate: ${uri.getImagePath(this.applicationContext)}")
            }
            "application/*" -> {
                Log.d("TAG--", "onCreate: ${uri.getFilePath(this.applicationContext)}")
            }
            "text/*" -> {
                finish()
            }
            else->{
                Log.d("TAG--", "onCreate: ${uri.getVideoPath(this.applicationContext)}")
                Log.d("TAG--", "onCreate: ${uri.getImagePath(this.applicationContext)}")
                Log.d("TAG--", "onCreate: ${uri.getFilePath(this.applicationContext)}")
            }
        }

    }


}