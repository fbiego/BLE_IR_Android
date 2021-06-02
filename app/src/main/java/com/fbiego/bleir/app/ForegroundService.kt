package com.fbiego.bleir.app

import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fbiego.bleir.BuildConfig
import com.fbiego.bleir.R
import com.fbiego.bleir.ble.LEManager
import com.fbiego.bleir.ble.LeManagerCallbacks
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import com.fbiego.bleir.app.MainApplication as MN

class ForegroundService : Service(), DataListener {

    private lateinit var context: Context
    private var startID = 0

    companion object{
        var bleManager: BleManager<LeManagerCallbacks>? = null
        const val NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID
        const val SERVICE_ID = 9062
        var deviceName = ""
        var parts = 0
    }

    override fun onCreate() {
        super.onCreate()

        notificationChannel(this)
        context = this
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val remoteMacAddress = pref.getString(MN.PREF_ADDRESS, MN.DEFAULT_ADDRESS)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress)

        bleManager = LEManager(this)
        (bleManager as LEManager).setGattCallbacks(bleManagerCallback)
        (bleManager as LEManager).connect(leDevice).enqueue()

        DataReceiver.bindListener(this)

        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }


    fun sendData(data: ByteArray): Boolean{
        return if (bleManager != null) {
            (bleManager as LEManager).writeBytes(data)
        } else {
            false
        }
    }



    @TargetApi(Build.VERSION_CODES.O)
    private fun notificationChannel(context: Context): NotificationManager {

        val notificationMgr = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationChannel = NotificationChannel(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID, NotificationManager.IMPORTANCE_DEFAULT)
            notificationChannel.description = "Clock service"
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.enableLights(true)
            notificationChannel.enableVibration(true)
            notificationMgr.createNotificationChannel(notificationChannel)
        }
        return notificationMgr
    }

    /**
     * Create/Update the notification
     */
    fun notify(text: String, priority: Boolean, id: Int): Notification {
        // Launch the MainAcivity when user taps on the Notification
        Timber.w("Context ${context.packageName}")
        val intent = Intent(context, MN::class.java)

        val pendingIntent = PendingIntent.getActivity(context, 456,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        val notBuild = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)

        notBuild.setSmallIcon(R.drawable.ic_bt)

        notBuild.color = ContextCompat.getColor(this, R.color.purple_500)

        notBuild.setContentIntent(pendingIntent)
        //notBuild.setContentTitle(contentText)
        notBuild.setContentText(text)
        notBuild.priority = NotificationCompat.PRIORITY_DEFAULT
        //notBuild.setSound(Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/"+R.raw.notification))
        notBuild.setShowWhen(priority)

        notBuild.setOnlyAlertOnce(true)
        val notification= notBuild.build()
        notificationChannel(context).notify(id, notification)
        return notification
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.w("onStartCommand {intent=${intent == null},flags=$flags,startId=$startId}")

        //val calendar = Calendar.getInstance(Locale.getDefault())
        //Timber.e("Service started with startId: ${this.startID} at ${calendar.time}")
        if (intent == null || this.startID != 0) {
            //service restarted
            Timber.w("onStartCommand - already running")
        } else {
            //started by intent or pending intent
            this.startID = startId
            val notification = notify("Scanning...", false,  SERVICE_ID)
            startForeground(SERVICE_ID, notification)

        }

        return START_STICKY
    }

    override fun onDestroy() {
        bleManager?.close()
        ConnectionReceiver().notifyStatus(false)
        unregisterReceiver(bluetoothReceiver)

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationMgr.deleteNotificationChannel(NOTIFICATION_CHANNEL)
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private val bleManagerCallback: LeManagerCallbacks = object : LeManagerCallbacks(){
        override fun onDeviceConnected(device: BluetoothDevice) {
            super.onDeviceConnected(device)
            Timber.d("onDeviceConnected ${device.name}")
            notify("Connected to ${device.name}", false, SERVICE_ID)
            deviceName = device.name
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            super.onDeviceReady(device)
            ConnectionReceiver().notifyStatus(true)
        }

        override fun onDeviceConnecting(device: BluetoothDevice) {
            super.onDeviceConnecting(device)
            notify("Connecting to ${device.name}", false, SERVICE_ID)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            super.onDeviceDisconnected(device)
            notify("Disconnected ${device.name}", false, SERVICE_ID)
            ConnectionReceiver().notifyStatus(false)
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            super.onDeviceDisconnecting(device)
            notify("Disconnecting to ${device.name}", false, SERVICE_ID)
        }

        override fun onLinkLossOccurred(device: BluetoothDevice) {
            super.onLinkLossOccurred(device)
            notify("Lost link to ${device.name}", true, SERVICE_ID)
            ConnectionReceiver().notifyStatus(false)
        }

        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            super.onError(device, message, errorCode)
            Timber.w("Error: $errorCode, Device:${device.name}, Message: $message")
            ConnectionReceiver().notifyStatus(false)
            stopSelf(startID)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action?.equals(BluetoothAdapter.ACTION_STATE_CHANGED) == true) {
                Timber.d("Bluetooth adapter changed in receiver")
                Timber.d("BT adapter state: ${intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)}")
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> {
                        // 2018/01/03 connect to remote
                        val remoteMacAddress = PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(MN.PREF_ADDRESS, MN.DEFAULT_ADDRESS)
                        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress)

                        bleManager = LEManager(context)
                        bleManager?.setGattCallbacks(bleManagerCallback)
                        bleManager?.connect(leDevice)?.enqueue()

                        Timber.d("Bluetooth STATE ON")
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        bleManager?.disconnect()
                        bleManager?.close()
                        Timber.d("Bluetooth TURNING OFF")


                    }
                }
            }
        }
    }



    override fun onDataReceived(data: Data) {


    }

}