package com.fbiego.bleir.data

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.fbiego.bleir.R

class ListAdapter (private val activity: Activity, private val name: ArrayList<String>, private val listener: (String ) -> Unit)
    : ArrayAdapter<String>(activity, R.layout.list_item, name){

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = activity.layoutInflater
        val rowView = inflater.inflate(R.layout.list_item, null, true)
        val nameR = rowView.findViewById<TextView>(R.id.fileName)
        val iconR = rowView.findViewById<ImageView>(R.id.fileDelete)

        nameR.text = name[position]

        iconR.setOnClickListener {
            listener(name[position])
        }


        return rowView
    }
}