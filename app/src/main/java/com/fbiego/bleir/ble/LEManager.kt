package com.fbiego.bleir.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.fbiego.bleir.app.DataReceiver
import com.fbiego.bleir.data.byteArrayOfInts
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber
import java.util.*

/**
 * Implements BLEManager
 */
class LEManager(context: Context) : BleManager<LeManagerCallbacks>(context) {

    var flxRxCharacteristic: BluetoothGattCharacteristic? = null
    var flxTxCharacteristic: BluetoothGattCharacteristic? = null


    companion object {
        const val MTU = 512
        val FLX_SERVICE_UUID:      UUID = UUID.fromString("fb1e4001-54ae-4a28-9f74-dfccb248601d")
        val FLX_TX_CHARACTERISTIC: UUID = UUID.fromString("fb1e4002-54ae-4a28-9f74-dfccb248601d")
        val FLX_RX_CHARACTERISTIC: UUID = UUID.fromString("fb1e4003-54ae-4a28-9f74-dfccb248601d")

    }

    /**
     * This method must return the gatt callback used by the manager.
     * This method must not create a new gatt callback each time it is being invoked, but rather return a single object.
     *
     * @return the gatt callback object
     */
    override fun getGattCallback(): BleManagerGattCallback {
        return callback
    }

    /**
     * Write {@code message} to the remote device's characteristic
     */


    fun transmitData(bytes: ByteArray, progress: Int, context: Context, listener: (Int, Context) -> Unit): Boolean{
        return if (isConnected && isReady && flxTxCharacteristic != null){
            requestMtu(MTU).enqueue()
            writeCharacteristic(flxTxCharacteristic, bytes)
                .with { device, data ->
                    Timber.d("Data sent to ${device.address} Data = ${data.size()}")
                }
                .fail { device, status ->
                    Timber.d("Failed to send data to ${device.name}, status = $status")
                }
                .done {
                    Timber.d("Data sent")
                    listener(progress, context)
                }
                .split()
                .enqueue()
            //Timber.d("Find watch ok")
            true
        } else {
            //Timber.d("Find fail: Connected - ${if (isConnected) "true" else "false"}  Ready - ${if (isReady) "true" else "false"}  Characteristic - ${if (flxTxCharacteristic != null) " ok" else "null"}")
            false
        }
    }


    fun setTime(): Boolean {
        return if (isConnected && isReady && flxTxCharacteristic != null) {
            requestMtu(MTU).enqueue()
            val calendar = Calendar.getInstance(Locale.getDefault())
            val time = byteArrayOfInts(
                0xAB,
                0x00,
                0x0B,
                0xFF,
                0x93,
                0x80,
                0x00,
                calendar.get(Calendar.YEAR) / 256,
                calendar.get(Calendar.YEAR) % 256,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND)
            )
            writeCharacteristic(flxTxCharacteristic, time).split().enqueue()
            Timber.d("Setting time")
            true
        } else {
            Timber.d("Unable to set time: Connected - ${if (isConnected) "true" else "false"}  Ready - ${if (isReady) "true" else "false"}  Characteristic - ${if (flxTxCharacteristic != null) " ok" else "null"}")
            false
        }
    }

    fun writeBytes(bytes: ByteArray): Boolean{
        return if (isConnected && isReady && flxTxCharacteristic != null){
            requestMtu(MTU).enqueue()
            writeCharacteristic(flxTxCharacteristic, bytes).split().enqueue()
            true
        } else {
            false
        }
    }

    /**
     * Returns whether to connect to the remote device just once (false) or to add the address to white list of devices
     * that will be automatically connect as soon as they become available (true). In the latter case, if
     * Bluetooth adapter is enabled, Android scans periodically for devices from the white list and if a advertising packet
     * is received from such, it tries to connect to it. When the connection is lost, the system will keep trying to reconnect
     * to it in. If true is returned, and the connection to the device is lost the [BleManagerCallbacks.onLinklossOccur]
     * callback is called instead of [BleManagerCallbacks.onDeviceDisconnected].
     *
     * This feature works much better on newer Android phone models and many not work on older phones.
     *
     * This method should only be used with bonded devices, as otherwise the device may change it's address.
     * It will however work also with non-bonded devices with private static address. A connection attempt to
     * a device with private resolvable address will fail.
     *
     * The first connection to a device will always be created with autoConnect flag to false
     * (see [BluetoothDevice.connectGatt]). This is to make it quick as the
     * user most probably waits for a quick response. However, if this method returned true during first connection and the link was lost,
     * the manager will try to reconnect to it using [BluetoothGatt.connect] which forces autoConnect to true .
     *
     * @return autoConnect flag value
     */
    override fun shouldAutoConnect(): Boolean {
        return true
    }

    /**
     * Implements GATTCallback methods
     */
    private val callback: BleManagerGattCallback = object : BleManagerGattCallback() {
        /**
         * This method should return `true` when the gatt device supports the required services.
         *
         * @param gatt the gatt device with services discovered
         * @return `true` when the device has the required service
         */
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val gattService: BluetoothGattService? = gatt.getService(FLX_SERVICE_UUID)
            if (flxTxCharacteristic == null) {
                gattService?.getCharacteristic(FLX_TX_CHARACTERISTIC)?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                flxTxCharacteristic = gattService?.getCharacteristic(FLX_TX_CHARACTERISTIC)
            }
            if (flxRxCharacteristic == null) {
                flxRxCharacteristic = gattService?.getCharacteristic(FLX_RX_CHARACTERISTIC)
            }

            Timber.w("Gatt service ${gattService != null}, RX ${flxRxCharacteristic != null}, TX ${flxTxCharacteristic != null}")

            return gattService != null
                    && flxRxCharacteristic != null
                    && flxTxCharacteristic != null
        }


        /**
         * This method should set up the request queue needed to initialize the profile.
         * Enabling Service Change indications for bonded devices is handled before executing this
         * queue. The queue may have requests that are not available, e.g. read an optional
         * service when it is not supported by the connected device. Such call will trigger
         * {@link Request#fail(FailCallback)}.
         * <p>
         * This method is called from the main thread when the services has been discovered and
         * the device is supported (has required service).
         * <p>
         * Remember to call {@link Request#enqueue()} for each request.
         * <p>
         * A sample initialization should look like this:
         * <pre>
         * &#64;Override
         * protected void initialize() {
         *    requestMtu(MTU)
         *       .with((device, mtu) -> {
         *           ...
         *       })
         *       .enqueue();
         *    setNotificationCallback(characteristic)
         *       .with((device, data) -> {
         *           ...
         *       });
         *    enableNotifications(characteristic)
         *       .done(device -> {
         *           ...
         *       })
         *       .fail((device, status) -> {
         *           ...
         *       })
         *       .enqueue();
         * }
         * </pre>
         */
        override fun initialize() {
            Timber.d("Initialising...")




            requestMtu(MTU).enqueue()


            setNotificationCallback(flxRxCharacteristic)
                .with { device, data ->
                    Timber.d("Data received from ${device.address} Data = ${data.size()}")

                    DataReceiver().getData(data)


                }
            enableNotifications(flxRxCharacteristic)
                .done {
                    Timber.d("Successfully enabled FLXRxCharacteristic notifications")
                }
                .fail { _, _ ->
                    Timber.w("Failed to enable FLXRxCharacteristic notifications")
                }
                .enqueue()
//            enableIndications(flxRxCharacteristic)
//                    .done {
//                        Timber.d("Successfully wrote message")
//                    }
//                    .fail { device, status ->
//                        Timber.w("Failed to write message to ${device.address} - status: $status")
//                    }
//                    .enqueue()


        }


        override fun onDeviceDisconnected() {
            flxRxCharacteristic = null
            flxTxCharacteristic = null
        }
    }

}