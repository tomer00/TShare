package com.tomer.tomershare.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.tomer.tomershare.databinding.ActivityRecivingBinding

class ActivityReceiving : AppCompatActivity() {

    private val b by lazy { ActivityRecivingBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(b.root)
    }
}