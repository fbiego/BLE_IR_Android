package com.fbiego.bleir.app

interface ConnectionListener {
    fun onConnectionChanged(state: Boolean)
}