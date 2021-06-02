package com.fbiego.bleir

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.os.Process.killProcess
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fbiego.bleir.app.ConnectionListener
import com.fbiego.bleir.app.ConnectionReceiver
import com.fbiego.bleir.ble.LEManager
import com.fbiego.bleir.data.*
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import com.fbiego.bleir.app.ForegroundService as FG
import com.fbiego.bleir.app.MainApplication as MN

class MainActivity : AppCompatActivity(), ConnectionListener {
    private var deviceList = ArrayList<BtDevice>()
    private var deviceAdapter = BtListAdapter(
        deviceList,
        deviceAddress,
        this@MainActivity::selectedDevice
    )

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var mScanning: Boolean = false

    lateinit var progress: ProgressBar
    lateinit var button: Button
    lateinit var textView: TextView
    lateinit var prefs: SharedPreferences


    var gAdapter: GridAdapter? = null
    private lateinit var alertDialog: AlertDialog
    var show = false
    private val fileNames = ArrayList<String>()
    private var lastLayout = ""

    companion object {
        lateinit var deviceRecycler: RecyclerView
        private const val FINE_LOCATION_PERMISSION_REQUEST= 1001
        const val BACKGROUND_LOCATION = 67
        const val STORAGE = 20
        const val FILE_PICK = 56
        var deviceAddress = ""

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ConnectionReceiver.bindListener(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        deviceAddress = prefs.getString(MN.PREF_ADDRESS, MN.DEFAULT_ADDRESS).toString()


        loadNames()




        val layout = ArrayList<IRCode>()
        gAdapter = GridAdapter(this, layout, this@MainActivity::onButton)
        gridView.adapter = gAdapter

        loadLayout(lastLayout)


    }

    private fun onButton(irCode: IRCode){
        var code = irCode.code
        val b1 = (code / (256 * 256 * 256)).toInt()
        code %= (256 * 256 * 256)
        val b2 = (code / (256 * 256)).toInt()
        code %= (256 * 256)
        val b3 = (code / 256).toInt()
        val b4 = (code % 256).toInt()

        val irSend = byteArrayOfInts(0x1B, irCode.protocol, b1, b2, b3, b4)

        val byteString = String.format("0x%02X%02X%02X%02X", b1, b2, b3, b4)

        if (!FG().sendData(irSend)){
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            debugInfo.text = "Failed! Not connected"
        } else {
            debugInfo.text = "${irCode.name} : $byteString"
        }
    }

    override fun onStart() {
        super.onStart()
        deviceAddress = prefs.getString(MN.PREF_ADDRESS, MN.DEFAULT_ADDRESS).toString()
        if (bluetoothAdapter.isEnabled && deviceAddress != MN.DEFAULT_ADDRESS){
            startService(Intent(this, FG::class.java))
        }
    }

    private fun loadNames(){
        val directory = this.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val remote = File(directory, "remote")
        if (!remote.exists()) {
            remote.mkdirs()
        }
        val files = remote.listFiles()

        fileNames.clear()
        for( f in files!!){
            fileNames.add(f.nameWithoutExtension)
        }
    }

    override fun onResume() {
        super.onResume()
        deviceAddress = prefs.getString(MN.PREF_ADDRESS, MN.DEFAULT_ADDRESS).toString()
        if (bluetoothAdapter.isEnabled && deviceAddress != MN.DEFAULT_ADDRESS) {
            startService(Intent(this, FG::class.java))
        }

        loadNames()

        lastLayout = prefs.getString(MN.PREF_LAYOUT, "null")!!
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                loadLayout(fileNames[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        loadLayout(lastLayout)
    }

    override fun onPause() {
        super.onPause()
        stopService(Intent(this, FG::class.java))
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            FINE_LOCATION_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    scanLeDevice(true)
                } else {
                    //tvTestNote.text= getString(R.string.allow_location_detection)
                }
                return
            }
            STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "*/*"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, 20)
                } else {
                    //tvTestNote.text= getString(R.string.allow_location_detection)
                }
                return
            }
        }
    }

    fun loadLayout(name: String){
        val directory = this.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val remote = File(directory, "remote")
        if (!remote.exists()) {
            remote.mkdirs()
        }

        val layout = ArrayList<IRCode>()
        val file = File(remote, "$name.txt")
        if (file.exists() && file.canRead()) {
            val read = file.readText()

            val lines = read.split("\n")



            for (l in lines) {
                val dat = l.split(",")
                if (dat.size == 3) {
                    val nme = dat[0]
                    val protocol = dat[1].toIntOrNull()
                    val code = dat[2].replace("0x", "", true).toLongOrNull(radix = 16)
                    if (protocol != null && code != null) {
                        layout.add(IRCode(nme, protocol, code))
                    }
                }
            }
            if (layout.isNotEmpty()){
                prefs.edit().putString(MN.PREF_LAYOUT, name).apply()
            } else {
                Toast.makeText(this, "Layout empty", Toast.LENGTH_SHORT).show()
            }

        } else {
            Toast.makeText(this, "Layout not found", Toast.LENGTH_SHORT).show()
        }
        gAdapter?.update(layout)
    }

    private fun checkLocation(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    private fun checkFINE(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestBackground(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BACKGROUND_LOCATION
        )
    }

    private fun requestLocation(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            FINE_LOCATION_PERMISSION_REQUEST
        )
    }

    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                Handler().postDelayed({
                    mScanning = false
                    progress.isIndeterminate = false
                    button.visibility = View.VISIBLE
                    bluetoothAdapter.bluetoothLeScanner?.stopScan(mLeScanCallback)
                }, 10000)
                mScanning = true
                progress.isIndeterminate = true
                button.visibility = View.GONE
                textView.text = "Searching for devices"
                val filter =
                    ScanFilter.Builder().setServiceUuid(ParcelUuid(LEManager.FLX_SERVICE_UUID))
                        .build()
                val filters = mutableListOf<ScanFilter>(filter)
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    bluetoothAdapter.bluetoothLeScanner?.startScan(
                        mLeScanCallback
                    )
                } else {
                    bluetoothAdapter.bluetoothLeScanner?.startScan(
                        filters,
                        settings,
                        mLeScanCallback
                    )
                }
            }
            else -> {
                mScanning = false
                progress.isIndeterminate = false
                button.visibility = View.VISIBLE
                bluetoothAdapter.bluetoothLeScanner?.stopScan(mLeScanCallback)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                //setDeviceBluetoothDiscoverable()

                stopService(Intent(this, FG::class.java))

                val inflater = layoutInflater
                val layout = inflater.inflate(R.layout.search_dialog, null)
                deviceRecycler = layout.findViewById(R.id.devicesView)
                button = layout.findViewById(R.id.search)
                progress = layout.findViewById(R.id.progressBar)
                textView = layout.findViewById(R.id.textView)
                progress.isIndeterminate = true
                button.setOnClickListener {
                    deviceList.clear()
                    updateDevices()
                    scanLeDevice(true)
                }
                deviceRecycler.layoutManager = LinearLayoutManager(this)
                val div = DividerItemDecoration(
                    deviceRecycler.context,
                    LinearLayoutManager.VERTICAL
                )
                deviceRecycler.addItemDecoration(div)
                deviceRecycler.isNestedScrollingEnabled = false
                deviceRecycler.apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = deviceAdapter
                }
                deviceRecycler.itemAnimator?.changeDuration = 0

                if (!checkLocation()){
                    requestLocation()
                } else {
                    if (!checkFINE()){
                        requestBackground()
                    } else {
                        if (bluetoothAdapter.isEnabled) {
                            scanLeDevice(true) //make sure scan function won't be called several times
                        }
                    }
                }

                val dialog = AlertDialog.Builder(this)

                dialog.setView(layout)

                dialog.setOnDismissListener {
                    Timber.e("Dialog dismissed")
                }

                alertDialog = dialog.create()
                alertDialog.show()
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun updateDevices(){
        textView.text = if (deviceList.isNotEmpty()){
            "Found ${deviceList.size} device${if (deviceList.size > 1) "s" else ""}"
        } else {
            "No device found"
        }
        deviceAdapter.update(deviceList, deviceAddress)
    }

    private fun selectedDevice(device: BtDevice){
        scanLeDevice(false)
        deviceAddress = device.address
        prefs.edit().putString(MN.PREF_ADDRESS, deviceAddress).apply()
        Timber.e("Selected: ${device.name}, address: ${device.address}, device: address")
        alertDialog.dismiss()
        if (!isBonded(device)){
            Timber.e("bonding device")
            createBond(device)
        } else {
            Timber.e("Already bonded")
        }
        Handler().postDelayed({
            startService(Intent(this, FG::class.java))
        }, 5000
        )

    }

    private fun createBond(btDev: BtDevice){
        bluetoothAdapter.getRemoteDevice(btDev.address).createBond()
    }

    private fun isBonded(btDev: BtDevice): Boolean{
        for (dev in bluetoothAdapter.bondedDevices){
            if (dev.address == btDev.address){
                return true
            }
        }
        return false
    }

    private var mLeScanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Timber.e("Callback: $callbackType, Result ${result?.device?.name}")
            val me: BtDevice? = deviceList.singleOrNull {
                it.address == result?.device?.address
            }
            if (me == null && result?.device?.name != null){
                deviceList.add(BtDevice(result.device.name, result.device.address, false))
            }
            updateDevices()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult?>?) {
            super.onBatchScanResults(results)
            Timber.e("Scan results: $results")
            for (result in results!!){
                val me: BtDevice? = deviceList.singleOrNull {
                    it.address == result?.device?.address
                }
                if (me == null && result?.device?.name != null){
                    deviceList.add(BtDevice(result.device?.name!!, result.device.address, false))
                }
            }
            updateDevices()
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("Scan Fail: error $errorCode")
        }

    }

    override fun onConnectionChanged(state: Boolean) {
        runOnUiThread {
            val view = if (state){
                clockName.text = FG.deviceName
                connected.imageTintList = ColorStateList.valueOf(Color.BLUE)
                View.VISIBLE
            } else {
                clockName.text = "Disconnected"
                connected.imageTintList = ColorStateList.valueOf(Color.DKGRAY)
                View.GONE
            }
            show = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.e("RequestCode= $requestCode, ResultCode= $resultCode, Data= ${data != null}")
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && requestCode == FILE_PICK) {

                val selectedFile = data.data
                val filePathColumn = arrayOf(MediaStore.Files.FileColumns.DATA)
                if (selectedFile != null) {
                    val cursor =
                        contentResolver.query(selectedFile, filePathColumn, null, null, null)
                    if (cursor != null) {
                        cursor.moveToFirst()
                        val columnIndex = cursor.getColumnIndex(filePathColumn[0])

                        //Timber.e(filePathColumn.contentDeepToString())
                        //Timber.e("index = $columnIndex")
                        val filePath = cursor.getString(columnIndex)

                        //val f = File(filePath)
                        cursor.close()

                        //saveFile(f)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                            //saveFile(File(filePath), null)
                        } else {
                            //saveFile(null, selectedFile)
                        }

                        //otaInfo()
                    }
                }
            }

        }
    }
}