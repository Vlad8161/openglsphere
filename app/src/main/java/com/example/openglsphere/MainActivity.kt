package com.example.openglsphere

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var sphereView: SphereView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val images = assets.list("")
                .filter { it.endsWith("png") }
        sphereView = SphereView(this, images)
        setContentView(sphereView)
    }

    override fun onPause() {
        super.onPause()
        sphereView.onPause()
    }

    override fun onResume() {
        super.onResume()
        sphereView.onResume()
    }
}
