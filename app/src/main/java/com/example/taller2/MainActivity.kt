package com.example.taller2

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val btnContactos: ImageButton = findViewById(R.id.btnContactos)
        btnContactos.setOnClickListener {
            val intent = Intent(this, ContactosActivity::class.java)
            startActivity(intent)
        }

        val btnCamara = findViewById<ImageButton>(R.id.btnCamara)
        btnCamara.setOnClickListener {
            val intent = Intent(this, CamaraGaleriaActivity::class.java)
            startActivity(intent)
        }

        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        btnMapa.setOnClickListener {
            val intent = Intent(this, MapaActivity::class.java)
            startActivity(intent)
        }

    }
}
