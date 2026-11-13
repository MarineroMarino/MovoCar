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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private var encuestaCompletada = false
    private var checksMarcados = booleanArrayOf(false, false, false, false)

    private fun actualizarEstadoPlay() {
        val todosMarcados = checksMarcados.all { it }
        if (encuestaCompletada && todosMarcados) {
            btnPlay.isEnabled = true
            btnPlay.alpha = 1.0f
        } else {
            btnPlay.isEnabled = false
            btnPlay.alpha = 0.5f
        }
    }

    private val abrirEncuestaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Este bloque se ejecuta cuando la WebView Activity se cierra
        if (result.resultCode == Activity.RESULT_OK) {
            // ¡El formulario se envió! Habilitamos el botón Play si también se marcaron los checks
            encuestaCompletada = true
            btnEncuesta.isEnabled = false
            btnEncuesta.alpha = 0.5f
            actualizarEstadoPlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telemetry_phone)
        configurarBotonContacto()
        val pinRecibido = intent.getStringExtra("EXTRA_PIN")
        val sharedPref = getSharedPreferences("PreferenciasTelemetria", Context.MODE_PRIVATE)

        // 2. Extraer el valor (si no existe aún, devolverá 'false' por defecto)
        val yaCompletada = sharedPref.getString("encuesta_completada_num", "false")
        val checksYaAceptados = sharedPref.getString("instrucciones_aceptadas_num", "false")

        btnTelemetry = findViewById(R.id.btnInstrucciones)
        btnEncuesta = findViewById(R.id.btnEncuesta)
        btnPlay = findViewById(R.id.btnPlay)

        encuestaCompletada = yaCompletada?.contains(pinRecibido.toString()) == true
        if (checksYaAceptados?.contains(pinRecibido.toString()) == true) {
            checksMarcados.fill(true)
        }

        if (encuestaCompletada) {
            btnEncuesta.isEnabled = false
            btnEncuesta.alpha = 0.5f
        }
        actualizarEstadoPlay()

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
            val padding = (24 * resources.displayMetrics.density).toInt()
            val spacing = (12 * resources.displayMetrics.density).toInt()

            val scrollView = ScrollView(this)
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, spacing, padding, spacing)
            }

            val tvMensaje = TextView(this).apply {
                text = "Bienvenido/a al experimento.\n" +
                        "Esta investigación tiene como objetivo estudiar los distintos estilos de conducción para poder mejorar tecnologías emergentes en conducción automatizada. Para ello, tu tarea consistirá en contestar a un cuestionario sobre estilos de conducción (aproximadamente 15 minutos) y, una vez finalizado, en al menos dos o tres ocasiones en las que vayas a conducir, acceder a esta APP y pulsar el botón de START.\n" +
                        "\n" +
                        "Inicia la grabación con el vehículo detenido y no uses el móvil mientras conduces, la aplicación funciona en segundo plano y no es necesario tocarla.\n" +
                        "\n" +
                        "Con ello, registraremos datos técnicos del vehículo mientras conduces (magnetómetro, giroscopio, acelerómetro y la posición en latitud, longitud y altitud). \n" +
                        "\n" +
                        "Trata de contestar a la encuesta y de conducir de la manera más natural posible. \n" +
                        "Cuando finalices los trayectos, tan solo recuerda parar la grabación pulsando el botón de STOP.\n" +
                        "\n" +
                        "Justo después, tendrás la opción, si quieres de dejarnos cualquier comentario sobre algún aspecto que desees añadir acerca del recorrido, pulsando sobre un botón con el micrófono.\n" +
                        "\n" +
                        "Recuerda que los datos son absolutamente confidenciales y serán tratados exclusivamente de manera global junto con las respuestas de otros conductores con fines de investigación. En ningún caso se publicarán tus resultados individuales ni ningún tipo de información que pudiera identificarte siguiendo la ley de protección de datos (RGPD – Reglamento UE 2016/679). No registraremos ni tu nombre, ni tu DNI, ni la matrícula de tu vehículo, ni tu correo electrónico; tan solo usamos los 4 últimos dígitos de tu móvil para enlazar tu respuesta en la encuesta con los datos de conducción.\n" +
                        "\n" +
                        "La participación en este estudio es estrictamente voluntaria y puedes retirarte en cualquier momento. Al finalizar el estudio, el equipo se compromete a explicar los resultados a todos los participantes que estén interesados en conocerlos. Para ello, tan solo hará falta que nos lo indiques a través del botón de contacto de la aplicación. \n" +
                        "\n" +
                        "Esta investigación forma parte del proyecto SEGVAUTO 5Gen. Innovando para la movilidad del futuro (TEC-2024/ECO-277), dentro de Ayudas destinadas a la realización de programas de actividades de I+D entre Grupos de Investigación de la Comunidad de Madrid en Tecnologías.\n" +
                        "\n" +
                        "Confirma lo siguiente y pulsa el botón de continuar.\n"
                textSize = 16f
            }
            container.addView(tvMensaje)

            val opcionesCheck = listOf(
                "Soy mayor de edad y tengo permiso de conducir en vigor.",
                "He recibido suficiente información sobre el estudio.",
                "Comprendo que mi participación es voluntaria.",
                "Comprendo que puedo retirarme del estudio cuando desee."
            )

            opcionesCheck.forEachIndexed { index, texto ->
                CheckBox(this).apply {
                    text = texto
                    isChecked = checksMarcados[index]
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = spacing / 2
                    }
                    setOnCheckedChangeListener { _, isChecked ->
                        checksMarcados[index] = isChecked
                        if (checksMarcados.all { it }) {
                            sharedPref.edit()
                                .putString("instrucciones_aceptadas_num", pinRecibido.toString())
                                .apply()
                        } else {
                            sharedPref.edit()
                                .remove("instrucciones_aceptadas_num")
                                .apply()
                        }
                        actualizarEstadoPlay()
                    }
                    container.addView(this)
                }
            }

            val tvConfirmacion = TextView(this).apply {
                text = "\nAl pulsar \"Continuar\", confirmas que aceptas participar. ¡Muchas gracias por tu ayuda!"
                textSize = 16f
            }
            container.addView(tvConfirmacion)

            scrollView.addView(container)

            val dialog = AlertDialog.Builder(this)
                .setTitle("¡Te damos la bienvenida al estudio!")
                .setView(scrollView)
                .setPositiveButton("Continuar") { d, _ ->
                    actualizarEstadoPlay()
                    d.dismiss()
                }
                .create()

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






