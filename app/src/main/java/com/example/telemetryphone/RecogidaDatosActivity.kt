package com.example.telemetryphone

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class RecogidaDatosActivity : AppCompatActivity() {

    companion object {
        private const val SAMPLE_INTERVAL_MS = 100L

        // --- CONFIGURACIÓN UPM DRIVE (WEBDAV) ---
        // TODO: Reemplaza con tus datos reales
        private const val UPM_USERNAME = "simca.insia"
        private const val UPM_PASSWORD = "fpLiD-HFKL2-NiBtJ-NDpMc-fLPrx"
        // La URL base. Asegúrate de que la carpeta "Telemetria" ya exista en la raíz de tu UPM Drive.
        private const val UPM_WEBDAV_BASE_URL = "https://drive.upm.es/remote.php/dav/files/simca.insia/datos_app/"
    }

    private lateinit var btnTelemetry: FloatingActionButton

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private val httpClient = OkHttpClient()

    private var isCollecting = false
    private var collectionJob: Job? = null
    private val collectedRecords = mutableListOf<Telemetry>()
    private var currentSessionId: String? = null
    private var grabacionTimer: CountDownTimer? = null
    private var estaGrabando = false
    private var mediaRecorder: MediaRecorder? = null
    private var archivoAudioLocal: File? = null
    private var animacionRespiracion: ObjectAnimator? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startCollectionIfValid()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recogida_datos)
        val pinRecibido = intent.getStringExtra("EXTRA_PIN")
        intent.putExtra("EXTRA_PIN", pinRecibido)

        btnTelemetry = findViewById(R.id.btnStop)
        iniciarAnimacionRespiracion(btnTelemetry)


        lifecycleScope.launch {
            startCollectionIfValid()
        }

        permissionLauncher.launch(runtimePermissionsToRequest())

        btnTelemetry.setOnClickListener {
            detenerAnimacionRespiracion(btnTelemetry)
            stopCollectionAndUpload()
            mostrarPopupAudio()


        }


    }
    private fun mostrarPopupAudio() {
        // 1. Instanciar el BottomSheetDialog
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_popup_audio, null)
        dialog.setContentView(view)

        // 2. Vincular las vistas del XML del popup
        val btnGrabar = view.findViewById<FloatingActionButton>(R.id.btnIniciarGrabacion)
        val btnCerrar = view.findViewById<MaterialButton>(R.id.btnCerrarPopup)
        val tvTimer = view.findViewById<TextView>(R.id.tvTimer)

        // 3. Acción del botón Grabar

        @SuppressLint("ClickableViewAccessibility")
        btnGrabar.setOnTouchListener { view, event ->
            when (event.action) {
                // --- EL DEDO TOCA EL BOTÓN (INICIAR) ---
                MotionEvent.ACTION_DOWN -> {
                    if (!estaGrabando) {
                        estaGrabando = true

                        // Cambiar color a rojo para indicar grabación
                        btnGrabar.backgroundTintList = getColorStateList(android.R.color.holo_red_light)

                        // Iniciar motor de audio
                        comenzarAGrabarAudio()

                        // Iniciar el contador de 30 segundos
                        grabacionTimer = object : CountDownTimer(60000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                val segundosRestantes = millisUntilFinished / 1000
                                tvTimer.text = "Tiempo restante: ${segundosRestantes}s"
                            }

                            override fun onFinish() {
                                // Se cumplieron los 30 segundos, detenemos forzosamente
                                Toast.makeText(this@RecogidaDatosActivity, "Tiempo máximo alcanzado", Toast.LENGTH_SHORT).show()
                                finalizarGrabacionYSubir(dialog) // Llamamos a nuestra función de guardado
                            }
                        }.start()
                    }
                    true // Devuelve true para indicar que hemos consumido el evento
                }

                // --- EL DEDO SE LEVANTA O SE CANCELA EL TOQUE (DETENER) ---
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Solo detenemos si estaba grabando (evita errores si soltamos después de los 30s)
                    if (estaGrabando) {
                        finalizarGrabacionYSubir(dialog) // Llamamos a nuestra función de guardado
                    }
                    view.performClick() // Buena práctica de accesibilidad en Android
                    true
                }
                else -> false
            }
        }

        // 4. Acción del botón Cerrar
        btnCerrar.setOnClickListener {
            if (estaGrabando) {
                grabacionTimer?.cancel()
                // Asegúrate de detener la grabación si cierran el popup a la fuerza
                estaGrabando = false
            }
            val sharedPref = getSharedPreferences("PreferenciasTelemetria", Context.MODE_PRIVATE)

            // 2. Guardar la variable a "true"
            with(sharedPref.edit()) {
                putString("encuesta_completada_num", this@RecogidaDatosActivity.intent.getStringExtra("EXTRA_PIN"))
                apply() // apply() lo guarda en segundo plano
            }
            dialog.dismiss()
            val intent = Intent(this, TelemetryActivity::class.java)
            intent.putExtra("EXTRA_PIN", this@RecogidaDatosActivity.intent.getStringExtra("EXTRA_PIN"))
            startActivity(intent)
            finish()
        }

        // Mostrar el popup en pantalla
        dialog.show()
    }

    private fun finalizarGrabacionYSubir(dialogGrabacionOriginal: BottomSheetDialog) {
        estaGrabando = false
        grabacionTimer?.cancel()

        // Detener el MediaRecorder y obtener el archivo
        val archivo = detenerYGuardarAudio()

        if (archivo != null && archivo.exists()) {
            // Ocultamos el popup de grabar
            dialogGrabacionOriginal.dismiss()

            // Mostramos el popup de revisión
            mostrarPopupRevision(archivo)
        } else {
            Toast.makeText(this, "Error al generar el audio", Toast.LENGTH_SHORT).show()
        }
    }
    private fun mostrarPopupRevision(archivoAudio: File) {
        val dialogRevision = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_popup_revision, null)
        dialogRevision.setContentView(view)

        val btnReproducir = view.findViewById<FloatingActionButton>(R.id.btnReproducirAudio)
        val btnEnviar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEnviar)
        val btnReintentar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReintentar)

        var reproduciendo = false

        // 1. Configurar el reproductor
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(archivoAudio.absolutePath)
            prepare() // Prepara el archivo para ser reproducido
        }

        // Cuando el audio termina por sí solo, devolvemos el botón a "Play"
        mediaPlayer.setOnCompletionListener {
            reproduciendo = false
            btnReproducir.setImageResource(android.R.drawable.ic_media_play)
        }

        // 2. Lógica del botón Play/Pause
        btnReproducir.setOnClickListener {
            if (reproduciendo) {
                mediaPlayer.pause()
                reproduciendo = false
                btnReproducir.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mediaPlayer.start()
                reproduciendo = true
                btnReproducir.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        // 3. Lógica de ENVIAR
        btnEnviar.setOnClickListener {
            mediaPlayer.release() // IMPORTANTE: Liberar memoria del reproductor
            dialogRevision.dismiss()

            Toast.makeText(this, "Gracias popr tu opinión", Toast.LENGTH_SHORT).show()

            // Iniciamos tu subida original en segundo plano
            CoroutineScope(Dispatchers.Main).launch {
                val subidaExitosa = uploadFileToUPMDrive(archivoAudio)

                if (subidaExitosa) {
                    val sharedPref = getSharedPreferences("PreferenciasTelemetria", Context.MODE_PRIVATE)

                    // 2. Guardar la variable a "true"
                    with(sharedPref.edit()) {
                        putString("encuesta_completada_num", this@RecogidaDatosActivity.intent.getStringExtra("EXTRA_PIN"))
                        apply() // apply() lo guarda en segundo plano
                    }
                    archivoAudio.delete()
                    val intent = Intent(this@RecogidaDatosActivity, TelemetryActivity::class.java)
                    intent.putExtra("EXTRA_PIN", this@RecogidaDatosActivity.intent.getStringExtra("EXTRA_PIN"))
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RecogidaDatosActivity, "Error al subir", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Lógica de REINTENTAR (Descartar y volver a grabar)
        btnReintentar.setOnClickListener {
            mediaPlayer.release()
            archivoAudio.delete() // Borramos el archivo malo del móvil
            dialogRevision.dismiss()

            // Volvemos a abrir el popup original de grabación desde cero
            mostrarPopupAudio()
        }

        // Por seguridad, si el usuario arrastra el popup hacia abajo para cerrarlo (sin pulsar botones)
        dialogRevision.setOnDismissListener {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Evitar que lo cierre tocando fuera (opcional, para forzarle a decidir)
        dialogRevision.setCancelable(false)
        dialogRevision.show()
    }
    private fun iniciarAnimacionRespiracion(vista: View) {
        // Si ya hay una animación corriendo, la evitamos duplicar
        if (animacionRespiracion?.isRunning == true) return

        // Configuramos que la vista crezca un 8% (1.08f) en anchura (X) y altura (Y)
        animacionRespiracion = ObjectAnimator.ofPropertyValuesHolder(
            vista,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.08f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.08f)
        ).apply {
            duration = 1000 // Tarda 1 segundo en inflarse (1000 milisegundos)
            repeatCount = ObjectAnimator.INFINITE // Se repite para siempre
            repeatMode = ObjectAnimator.REVERSE // Hace el efecto yoyo (crece, encoge, crece...)
            start()
        }
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun comenzarAGrabarAudio(){
        val nombreArchivo = "telemetry_audio_${intent.getStringExtra("EXTRA_PIN")}_${System.currentTimeMillis()}.mp4"
        archivoAudioLocal = File(externalCacheDir?.absolutePath, nombreArchivo)

        // 2. Instanciar MediaRecorder (con compatibilidad para Android 12+)
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this) // Android 12 o superior necesita el Contexto
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder() // Versiones anteriores
        }

        // 3. Configurar la fuente, formato y códec
        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Formato moderno y ligero
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)    // Buena calidad de voz
            setOutputFile(archivoAudioLocal!!.absolutePath)

            // 4. Preparar y arrancar
            try {
                prepare()
                start()
                Log.d("Audio", "Grabación iniciada en: ${archivoAudioLocal!!.absolutePath}")
            } catch (e: IOException) {
                Log.e("Audio", "Fallo al preparar el MediaRecorder: ${e.message}")
            }
        }

    }

    private fun detenerYGuardarAudio(): File? {
        try {
            mediaRecorder?.apply {
                stop()     // Detiene la grabación
                release()  // Libera el micrófono para que otras apps puedan usarlo
            }
        } catch (e: Exception) {
            // A veces stop() falla si se llama inmediatamente después de start()
            Log.e("Audio", "Error al detener la grabación: ${e.message}")
        } finally {
            mediaRecorder = null

        }



        // Devolvemos el archivo que creamos al inicio
        return archivoAudioLocal
    }
    private fun detenerAnimacionRespiracion(vista: View) {
        animacionRespiracion?.cancel()
        animacionRespiracion = null

        // Devolvemos el botón a su tamaño normal exacto por si la animación
        // se canceló justo cuando estaba "inflado"
        vista.scaleX = 1.0f
        vista.scaleY = 1.0f
    }
    private fun runtimePermissionsToRequest(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
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

    private fun startCollectionIfValid() {

        currentSessionId = intent.getStringExtra("EXTRA_PIN")
        collectedRecords.clear()
        isCollecting = true




        collectionJob = lifecycleScope.launch {
            while (isCollecting) {
                val snapshot = collectTelemetrySnapshot(
                    context = this@RecogidaDatosActivity,
                    grants = currentPermissionMap(),
                    sessionId = currentSessionId ?: ""
                )

                collectedRecords.add(snapshot)


                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun stopCollectionAndUpload() {
        isCollecting = false
        collectionJob?.cancel()
        lifecycleScope.launch {
            val sessionId = currentSessionId
            if (sessionId.isNullOrBlank()) {
                return@launch
            }

            if (collectedRecords.isEmpty()) {

                return@launch
            }



            val batch = TelemetryBatch(
                generatedAt = System.currentTimeMillis(),
                count = collectedRecords.size,
                records = collectedRecords.toList()
            )


            val localFile = saveBatchCsv(batch, sessionId)



            if (!hasInternet()) {

                return@launch
            }

            val success = withContext(Dispatchers.IO) {
                uploadFileToUPMDrive(localFile)
            }

            if (success) {

                collectedRecords.clear()
                localFile.delete()
                uploadPendingFiles()
            }
        }
    }

    // --- NUEVO MÉTODO DE SUBIDA WEBDAV ---
    suspend fun uploadFileToUPMDrive(localFile: File): Boolean {
        // Forzamos que todo este bloque se ejecute en el hilo de red (IO)
        return withContext(Dispatchers.IO) {
            try {
                // Un pequeño seguro por si a la constante le falta la barra final
                val baseUrl = if (UPM_WEBDAV_BASE_URL.endsWith("/")) UPM_WEBDAV_BASE_URL else "$UPM_WEBDAV_BASE_URL/"
                val fileUrl = baseUrl + localFile.name

                val credential = Credentials.basic(UPM_USERNAME, UPM_PASSWORD)

                // 1. Detección dinámica del tipo de archivo en lugar de "application/json"
                val extension = MimeTypeMap.getFileExtensionFromUrl(localFile.absolutePath)
                val mimeTypeString = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                    ?: "application/octet-stream"

                val requestBody = localFile.asRequestBody(mimeTypeString.toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(fileUrl)
                    .header("Authorization", credential)
                    .put(requestBody)
                    .build()

                // 2. Ejecución sincrónica pero segura gracias al Dispatchers.IO
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun uploadPendingFiles() {
        val files = filesDir.listFiles { file ->
            file.name.startsWith("telemetry_")
        } ?: return

        for (file in files) {
            val success = withContext(Dispatchers.IO) {
                uploadFileToUPMDrive(file)
            }

            if (success) {
                file.delete()
            }
        }
    }

    private fun currentPermissionMap(): Map<String, Boolean> {
        return runtimePermissionsToRequest().associateWith { perm ->
            checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun saveBatchCsv(batch: TelemetryBatch, sessionId: String): File {
        val filename = String.format(
            Locale.US,
            "telemetry_%s_%d.csv", // Extensión cambiada a .csv
            sessionId,
            batch.generatedAt
        )

        val file = File(filesDir, filename)

        // 1. Serializamos solo la lista de registros (records) para que cada fila sea una lectura
        val jsonString = json.encodeToString(batch.records)

        // 2. Convertimos el array JSON a texto CSV
        val csvString = convertirJsonACsv(jsonString)

        // 3. Escribimos en el archivo
        file.writeText(csvString)
        return file
    }

    // El motor de conversión
    private fun convertirJsonACsv(jsonString: String): String {
        try {
            val jsonArray = JSONArray(jsonString)
            if (jsonArray.length() == 0) return ""

            val csvBuilder = StringBuilder()

            // Extraer cabeceras del primer objeto
            val primerObjeto = jsonArray.getJSONObject(0)
            val iteradorClaves = primerObjeto.keys()
            val cabeceras = mutableListOf<String>()

            iteradorClaves.forEach { clave ->
                cabeceras.add(clave)
            }

            csvBuilder.append(cabeceras.joinToString(",")).append("\n")

            // Extraer valores fila por fila
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val fila = mutableListOf<String>()

                for (clave in cabeceras) {
                    var valor = jsonObject.optString(clave, "")

                    // Escapar las comillas dobles internas para no romper el formato CSV
                    // (Importante porque tus sensores y location son objetos anidados)
                    valor = valor.replace("\"", "\"\"")

                    // Envolver el valor en comillas para proteger comas internas
                    fila.add("\"$valor\"")
                }
                csvBuilder.append(fila.joinToString(",")).append("\n")
            }

            return csvBuilder.toString()

        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    private suspend fun collectTelemetrySnapshot(
        context: Context,
        grants: Map<String, Boolean>,
        sessionId: String
    ): Telemetry {
        val notes = mutableListOf<String>()

        val device = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "unknown"
        )

        val sensors = readSensorsSnapshot(context)

        val hasLocationPermission =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val location = if (hasLocationPermission) {
            readLocationSnapshot(context)
        } else {
            notes += "location: missing location permission"
            null
        }

        return Telemetry(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            device = device,
            sensors = sensors,
            location = location
        )
    }

    private suspend fun readSensorsSnapshot(context: Context): SensorsInfo = withContext(Dispatchers.Default) {
        val sm = context.getSystemService(SENSOR_SERVICE) as SensorManager
        val all = sm.getSensorList(Sensor.TYPE_ALL)

        val wantedTypes = setOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_RELATIVE_HUMIDITY,
            Sensor.TYPE_AMBIENT_TEMPERATURE
        )

        val wanted = all.filter { it.type in wantedTypes }
        val readings = LinkedHashMap<String, SensorReading>()

        for (sensor in wanted) {
            val key = "${sensor.stringType}:${sensor.name}"
            val reading = readOneSensorEvent(sm, sensor, 100L)
            if (reading != null) readings[key] = reading
        }

        SensorsInfo(
            readings = readings
        )
    }

    private suspend fun readOneSensorEvent(
        sm: SensorManager,
        sensor: Sensor,
        timeoutMs: Long
    ): SensorReading? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this, sensor)
                        if (cont.isActive) {
                            cont.resume(
                                SensorReading(
                                    values = event.values.toList(),
                                    vendor = sensor.vendor,
                                    version = sensor.version,
                                    unit = sensorUnit(sensor.type),
                                    accuracy = event.accuracy,
                                    eventTimestampNs = event.timestamp
                                )
                            )
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

                cont.invokeOnCancellation {
                    sm.unregisterListener(listener, sensor)
                }
            }
        }
    }

    private fun sensorUnit(type: Int): String? = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "m/s²"
        Sensor.TYPE_GYROSCOPE -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD -> "µT"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        else -> null
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLocationSnapshot(context: Context): LocationInfo? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val current = withTimeoutOrNull(1500L) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                val cts = CancellationTokenSource()

                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }

                cont.invokeOnCancellation { cts.cancel() }
            }
        }

        val loc = current ?: withTimeoutOrNull(800L) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                client.lastLocation
                    .addOnSuccessListener { last ->
                        if (cont.isActive) cont.resume(last)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

        return loc?.let {
            LocationInfo(
                provider = it.provider,
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracy,
                altitudeMeters = if (it.hasAltitude()) it.altitude else null,
                speedMps = if (it.hasSpeed()) it.speed else null
            )
        }
    }
}