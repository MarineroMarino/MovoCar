package com.example.telemetryphone

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Dentro de tu onCreate() en la Activity principal:

        val et1 = findViewById<EditText>(R.id.etDigit1)
        val et2 = findViewById<EditText>(R.id.etDigit2)
        val et3 = findViewById<EditText>(R.id.etDigit3)
        val et4 = findViewById<EditText>(R.id.etDigit4)

        // Pasamos: (Caja actual, Caja siguiente, Caja anterior)
        setupOtpFocus(et1, next = et2, previous = null) // La 1ª no tiene anterior
        setupOtpFocus(et2, next = et3, previous = et1)
        setupOtpFocus(et3, next = et4, previous = et2)
        setupOtpFocus(et4, next = null, previous = et3) // La 4ª no tiene siguiente

        // El último dígito: saltar automáticamente al terminar
        et4.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length == 1) {
                    // Recogemos el PIN y vamos a la siguiente Activity
                    val pin = "${et1.text}${et2.text}${et3.text}${et4.text}"
                    irALaSiguienteActivity(pin)
                }
            }
        })



    }

    private fun irALaSiguienteActivity(pin: String) {
        // 1. Creamos el Intent dirigido a tu nueva Activity (ej. SiguienteActivity)
        val intent = Intent(this, TelemetryActivity::class.java)

        // 2. Metemos el PIN en el Intent usando una clave ("EXTRA_PIN")
        intent.putExtra("EXTRA_PIN", pin)

        // 3. Iniciamos la nueva Activity
        startActivity(intent)

        // Opcional: Si no quieres que el usuario pueda volver a la pantalla
        // del PIN pulsando el botón "Atrás" de su móvil, cierra esta Activity:
        // finish()
    }

    private fun setupOtpFocus(current: EditText, next: EditText?, previous: EditText?) {

        // 1. Lógica para AVANZAR (cuando se escribe un número)
        current.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length == 1) {
                    next?.requestFocus()
                }
            }
        })

        // 2. Lógica para RETROCEDER (cuando se pulsa el botón de borrar)
        current.setOnKeyListener { _, keyCode, event ->
            // Si se pulsa una tecla hacia abajo y es la tecla de borrar (Backspace)
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {

                // Si la caja actual está vacía y se pulsa borrar, saltamos a la anterior
                if (current.text.isEmpty()) {
                    previous?.requestFocus()

                    // Opcional: Descomenta la siguiente línea si quieres que al saltar
                    // atrás se borre automáticamente el número que había en esa caja.
                     previous?.text?.clear()

                    return@setOnKeyListener true // Indicamos que hemos manejado el evento
                }
            }
            false
        }
    }
}