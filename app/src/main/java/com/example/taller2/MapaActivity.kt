package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast

import android.location.Geocoder
import android.os.StrictMode
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
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
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
    lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null

    private var lastLocation: GeoPoint? = null
    private val gson = Gson()
    private val jsonFile = "ubicaciones.json"
    private var modoOscuroActivo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_mapa)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        roadManager = OSRMRoadManager(this, "ANDROID")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        lightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0]
                Log.d("SENSOR_LUZ", "Lux actual: $lux")
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
        val tilesOverlay = map.overlayManager.tilesOverlay

        if (lux < 5 && !modoOscuroActivo) {
            // Activar modo oscuro
            tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            cambiarColorRutas(Color.CYAN)
            modoOscuroActivo = true
            map.invalidate()
        } else if (lux >= 5 && modoOscuroActivo) {
            // Volver a modo claro
            tilesOverlay.setColorFilter(null)
            cambiarColorRutas(Color.BLUE)
            modoOscuroActivo = false
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
            Log.i("OSM_acticity", e.toString())
            //Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
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

        drawRoute(lastLocation!!, hasta)
        /*val distancia = hasta.distanceToAsDouble(lastLocation)  // distancia en metros
        val distanciaFormateada = String.format(Locale.getDefault(), "%.2f", distancia)
        Toast.makeText(this, "Distancia a marcador: $distanciaFormateada metros", Toast.LENGTH_LONG).show()*/
    }

    private fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        val routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)

        val road = roadManager.getRoad(routePoints)
        Log.i("OSM_acticity", "Route length: ${road.mLength} km")
        Log.i("OSM_acticity", "Duration: ${road.mDuration / 60} min")

        val distancia = road.mLength * 1000
        val distanciaFormateada = String.format(Locale.getDefault(), "%.2f", distancia)
        Toast.makeText(this, "Distancia a marcador: $distanciaFormateada metros", Toast.LENGTH_LONG).show()

        roadOverlay?.let { map.overlays.remove(it) }  // eliminar ruta anterior si hay

        roadOverlay = RoadManager.buildRoadOverlay(road)
        map.overlays.add(roadOverlay)
        map.invalidate()
    }

    private fun cambiarColorRutas(nuevoColor: Int) {
        for (overlay in map.overlays) {
            if (overlay is Polyline) {
                overlay.outlinePaint.color = nuevoColor
            }
        }
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
