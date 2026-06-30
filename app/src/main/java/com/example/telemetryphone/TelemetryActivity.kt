@file:Suppress("DEPRECATION")

package com.example.telemetryphone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class TelemetryActivity : AppCompatActivity() {


    private lateinit var btnInstrucciones: MaterialButton
    private lateinit var btnEncuesta: MaterialButton
    private lateinit var btnTelemetry: Button


    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        }

    private lateinit var btnPlay: FloatingActionButton


    private val abrirEncuestaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Este bloque se ejecuta cuando la WebView Activity se cierra
        if (result.resultCode == Activity.RESULT_OK) {
            // ¡El formulario se envió! Habilitamos el botón Play
            btnEncuesta.isEnabled = false
            btnEncuesta.alpha = 0.5f
            btnPlay.isEnabled = true
            btnPlay.alpha = 1.0f // Le devolvemos la opacidad total para que se vea activo
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telemetry_phone)
        val pinRecibido = intent.getStringExtra("EXTRA_PIN")
        val sharedPref = getSharedPreferences("PreferenciasTelemetria", Context.MODE_PRIVATE)

        // 2. Extraer el valor (si no existe aún, devolverá 'false' por defecto)
        val yaCompletada = sharedPref.getString("encuesta_completada_num", "false")

        btnTelemetry = findViewById(R.id.btnInstrucciones)
        btnEncuesta = findViewById(R.id.btnEncuesta)
        btnPlay = findViewById(R.id.btnPlay)
        if(yaCompletada?.contains(pinRecibido.toString()) ?: false){
            btnPlay.isEnabled = true
            btnPlay.alpha = 1.0f // Le devolvemos la opacidad total para que se vea activo
            btnEncuesta.isEnabled = false
            btnEncuesta.alpha = 0.5f
        } else {
            btnPlay.isEnabled = false
            btnPlay.alpha = 0.5f
        }





        btnEncuesta.setOnClickListener {
            val intent = Intent(this, WebViewFormActivity::class.java)
            abrirEncuestaLauncher.launch(intent)
        }

        btnPlay.setOnClickListener {
            // Pasamos a la Activity de recogida de datos
            intent.putExtra("EXTRA_PIN", pinRecibido)
            irALaSiguienteActivity(pinRecibido)
        }

        btnTelemetry.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder
                .setMessage("Tu participación es voluntaria, 100% anónima y sólo se usarán datos técnicos del vehículo para fines de investigación.\n" +
                        "\n" +
                        "Para colaborar, sigue estos pasos:\n" +
                        "\n" +
                        "    Contesta el cuestionario inicial sobre estilos de conducción.\n" +
                        "\n" +
                        "    En tus próximos 2 o 3 viajes, abre esta app antes de arrancar y pulsa el botón verde de START. Conduce como lo haces habitualmente.\n" +
                        "\n" +
                        "    Al llegar a tu destino, pulsa el botón azul de STOP para finalizar la grabación.\n" +
                        "\n" +
                        "    (Opcional) Si quieres contarnos algo sobre el viaje, pulsa el botón del micrófono y déjanos un comentario de voz.\n" +
                        "\n" +
                        "\n" +
                        "Al pulsar \"Continuar\", confirmas que aceptas participar. ¡Muchas gracias por tu ayuda!")
                .setTitle("¡Te damos la bienvenida al estudio!")
                .setPositiveButton("Continuar") { dialog, which ->
                    // Do something.
                    dialog.dismiss()
                }

            val dialog: AlertDialog = builder.create()
            dialog.show()

        }
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun runtimePermissionsToRequest(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        return perms.toTypedArray()
    }

    private fun irALaSiguienteActivity(pin: String?) {
        // 1. Creamos el Intent dirigido a tu nueva Activity (ej. SiguienteActivity)
        val intent = Intent(this, RecogidaDatosActivity::class.java)

        // 2. Metemos el PIN en el Intent usando una clave ("EXTRA_PIN")
        intent.putExtra("EXTRA_PIN", pin)

        // 3. Iniciamos la nueva Activity
        startActivity(intent)

        // Opcional: Si no quieres que el usuario pueda volver a la pantalla
        // del PIN pulsando el botón "Atrás" de su móvil, cierra esta Activity:
         finish()
    }


    private fun currentPermissionMap(): Map<String, Boolean> {
        return runtimePermissionsToRequest().associateWith { perm ->
            checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
    }

}






