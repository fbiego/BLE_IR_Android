package com.fbiego.bleir.data

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import com.fbiego.bleir.R
import java.util.*
import kotlin.collections.ArrayList


class GridAdapter(private val activity: Activity, private var irCodes: ArrayList<IRCode>, private val listener: (IRCode) -> Unit) : BaseAdapter() {

    override fun getCount(): Int {
        return irCodes.size
    }

    override fun getItem(i: Int): Any? {
        return null
    }

    override fun getItemId(i: Int): Long {
        return 0
    }

    fun update(items: ArrayList<IRCode>){
        this.irCodes = items
        notifyDataSetChanged()

    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val inflater = activity.layoutInflater
        val mView = inflater.inflate(R.layout.grid_item, null, true) // inflate the layout
        val mButton = mView.findViewById<Button>(R.id.buttonText)// get the reference of ImageView
        mButton.text = irCodes[i].name
        mButton.setOnClickListener {
            listener(irCodes[i])
        }
        return mView
    }

}