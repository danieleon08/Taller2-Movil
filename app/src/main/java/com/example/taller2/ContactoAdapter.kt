package com.example.taller2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.LayoutRes

class ContactoAdapter(
    context: Context,
    @LayoutRes private val layoutRes: Int,
    private val lista: List<Contacto>
) : ArrayAdapter<Contacto>(context, layoutRes, lista) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(layoutRes, parent, false)

        val contacto = lista[position]
        val img = view.findViewById<ImageView>(R.id.imgContacto)
        val nombre = view.findViewById<TextView>(R.id.txtNombre)

        nombre.text = "${position + 1}. ${contacto.nombre}"

        // Imagen Contacto
        img.setImageResource(R.drawable.contactos)

        return view
    }
}
