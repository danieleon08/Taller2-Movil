package com.example.taller2

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.taller2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {


    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root) //La asociacion de Front con Back

        val btnContactos: ImageButton = findViewById(R.id.btnContactos)
        binding.btnContactos.setOnClickListener {
            val intent = Intent(this, ContactosActivity::class.java)
            startActivity(intent)
        }

        val btnCamara = findViewById<ImageButton>(R.id.btnCamara)
        binding.btnCamara.setOnClickListener {
            val intent = Intent(this, CamaraGaleriaActivity::class.java)
            startActivity(intent)
        }

        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        binding.btnMapa.setOnClickListener {
            val intent = Intent(this, MapaActivity::class.java)
            startActivity(intent)
        }

    }
}
