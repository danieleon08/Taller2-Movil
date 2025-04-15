package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast

import android.location.Geocoder
import android.view.MotionEvent
import android.widget.EditText
import android.view.inputmethod.EditorInfo

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MapaActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var controller: IMapController
    private lateinit var marker: Marker
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var lightListener: SensorEventListener


    private var lastLocation: GeoPoint? = null
    private val gson = Gson()
    private val jsonFile = "ubicaciones.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_mapa)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        lightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0]
                cambiarEstiloMapaSegunLuz(lux)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        controller = map.controller
        controller.setZoom(18.0)

        // Inicializar overlay de ubicación
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        locationOverlay.runOnFirstFix {
            val location = locationOverlay.myLocation
            runOnUiThread {
                location?.let {
                    val point = GeoPoint(it.latitude, it.longitude)
                    colocarMarcador(point)
                    controller.animateTo(point)
                    guardarUbicacion(point)
                    lastLocation = it
                }
            }
        }

        locationOverlay.myLocationProvider.startLocationProvider { loc, _ ->
            val point = GeoPoint(loc.latitude, loc.longitude)
            if (lastLocation == null || point.distanceToAsDouble(lastLocation) > 30) {
                lastLocation = point
                colocarMarcador(point)
                guardarUbicacion(point)
                controller.animateTo(point)
            }
        }

        val eventosMapa = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false  // No hacemos nada con tap normal
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    manejarLongClick(it)
                }
                return true
            }
        }

        val overlayEventos = MapEventsOverlay(eventosMapa)
        map.overlays.add(overlayEventos)


        val textoBuscar = findViewById<EditText>(R.id.texto_buscar)

        textoBuscar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val direccion = v.text.toString()
                if (direccion.isNotBlank()) {
                    buscarDireccion(direccion)
                }
                true
            } else {
                false
            }
        }


    }

    private fun colocarMarcador(geoPoint: GeoPoint) {
        if (::marker.isInitialized) {
            map.overlays.remove(marker)
        }

        marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Mi ubicación"
        map.overlays.add(marker)
    }

    private fun guardarUbicacion(geoPoint: GeoPoint) {
        val file = File(filesDir, jsonFile)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val nueva = mapOf(
            "lat" to geoPoint.latitude,
            "lng" to geoPoint.longitude,
            "fechaHora" to date
        )

        val lista: MutableList<Map<String, Any>> = if (file.exists()) {
            gson.fromJson(file.readText(), object : TypeToken<MutableList<Map<String, Any>>>() {}.type)
        } else {
            mutableListOf()
        }

        lista.add(nueva)
        file.writeText(gson.toJson(lista))
        Log.d("JSON", "Guardado: $nueva")
    }

    private fun cambiarEstiloMapaSegunLuz(lux: Float) {
        // Si luz es baja (< 50 lux), modo oscuro. Si no, modo claro.
        val nuevaFuente = if (lux < 50) {
            TileSourceFactory.USGS_SAT  // estilo oscuro
        } else {
            TileSourceFactory.MAPNIK   // estilo claro
        }

        if (map.tileProvider.tileSource != nuevaFuente) {
            map.setTileSource(nuevaFuente)
            map.invalidate()
        }
    }

    private fun manejarLongClick(punto: GeoPoint) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val direcciones = geocoder.getFromLocation(punto.latitude, punto.longitude, 1)
            val direccion = if (!direcciones.isNullOrEmpty()) {
                direcciones[0].getAddressLine(0)
            } else {
                "Dirección desconocida"
            }

            runOnUiThread {
                colocarMarcador(punto)
                marker.title = direccion
                marker.snippet = "Marcador creado por LongClick"
                map.invalidate()
                mostrarDistanciaDesdeUbicacionActual(punto)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error obteniendo dirección", Toast.LENGTH_SHORT).show()
        }
    }


    private fun buscarDireccion(direccion: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        val resultados = geocoder.getFromLocationName(direccion, 1)

        if (!resultados.isNullOrEmpty()) {
            val resultado = resultados[0]
            val punto = GeoPoint(resultado.latitude, resultado.longitude)

            runOnUiThread {
                colocarMarcador(punto)  // Usa tu función ya existente
                controller.setZoom(18.0)
                controller.animateTo(punto)
                Toast.makeText(this, "Marcador en: $direccion", Toast.LENGTH_SHORT).show()
                mostrarDistanciaDesdeUbicacionActual(punto)
            }
        } else {
            Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDistanciaDesdeUbicacionActual(hasta: GeoPoint) {
        if (lastLocation == null) {
            Toast.makeText(this, "Ubicación actual no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val distancia = hasta.distanceToAsDouble(lastLocation)  // distancia en metros
        val distanciaFormateada = String.format(Locale.getDefault(), "%.2f", distancia)
        Toast.makeText(this, "Distancia a marcador: $distanciaFormateada metros", Toast.LENGTH_LONG).show()
    }



    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(lightListener)
        map.onPause()
    }
}
