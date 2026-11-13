package com.example.telemetryphone

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.configurarBotonContacto() {
    val btnContacto = findViewById<View>(R.id.btnContacto)
    btnContacto?.setOnClickListener {
        mostrarDialogoContacto()
    }
}

fun AppCompatActivity.mostrarDialogoContacto() {
    val mensaje = "Este proyecto está dirigido por Elisa Pérez Moreno del Departamento de Psicobiología y Metodología en Ciencias del Comportamiento de la Facultad de Psicología (UCM). \n" +
            "Si tienes alguna duda sobre este estudio, puedes dirigirte a nosotros a través del correo electrónico elisaperez@psi.ucm.es"

    val dialog = AlertDialog.Builder(this)
        .setTitle("Contacto")
        .setMessage(mensaje)
        .setPositiveButton("Cerrar") { d, _ ->
            d.dismiss()
        }
        .create()

    dialog.show()

    dialog.findViewById<TextView>(android.R.id.message)?.apply {
        Linkify.addLinks(this, Linkify.EMAIL_ADDRESSES)
        movementMethod = LinkMovementMethod.getInstance()
    }
}
