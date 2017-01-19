package com.ble;

import android.app.IntentService;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Created by SunOK on 11/01/2017.
 * BluetoothLeService manages the connection and communication with a GATT server hosted on a Bluetooth LE device
 */


public class BluetoothLeService extends IntentService {

    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private final IBinder mBinder = new LocalBinder();


    private BluetoothManager mBluetoothManager; // conducts overall Bluetooth Management
    private BluetoothAdapter mBluetoothAdapter; // manages fundamental Bluetooth tasks: initiate device discovery, list the devices instantiate a Bluetooth device & start LeScan
    private BluetoothGatt mBluetoothGatt; // enable communication btw Bluetooth devices
    private String mBluetoothDeviceAddress;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // ACTIONS are used in the broadcastUpdate to be passed to Intents
    public final static String ACTION_GATT_CONNECTED = "com.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.ble.EXTRA_DATA";

    private UUID bleUUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");

    public final static UUID UUID_DEVICE_NAME =
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");


    // Initializing the BluetoothAdapter and BluetoothManager -> note BluetoothManager requires min API level to be 18!
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public BluetoothLeService() {
        super("CAN Bluetooth Le Service");
    }

    @Override
    public void onHandleIntent(Intent intent){

    }

    // Required function for extending to Service
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // Required function for extending to Service, when UI is disconnected from the Service, the connection should be closed
    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    // Close function ensures that the resources are released properly
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    // used to implement BluetoothGatt callback, relates to the broadcast of the current state & action

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED){
                mConnectionState = STATE_CONNECTED;

                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

                broadcastUpdate(ACTION_GATT_CONNECTED);

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                mConnectionState = STATE_DISCONNECTED;

                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){

            if (status == BluetoothGatt.GATT_SUCCESS) {

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    // When a callback is triggered, broadcastUpdate must be called with that action
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent); // function of Context: broadcasts the given intent > action to all the BroadcastReceivers within DeviceControlActivity
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // TODO bleUUID may need to be updated to a Direct Message UUID! to retrieve transmitted information
        if (bleUUID.equals(characteristic.getUuid())) {

            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.e(TAG, String.format("extra message is: %s", characteristic.getStringValue(0)));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());

            }
        }
        sendBroadcast(intent);
    }

    // TODO will be used in Device Scan activity
    public boolean connect(final String address) {

        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Try to reconnect to a previously connected device
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");

            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, true, mGattCallback); // The second parameter (autoConnect) true; makes sure to connect to the remote device as soon as it becomes available

        Log.d(TAG, "Trying to create a new connection.");

        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    // TODO will be used in Device Control activity
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    // Get supported GATT Services of the device
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    // Enables or disables notification on a give characteristic.
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // TODO must be updated for Jelle's library: This is specific to Heart Rate Measurement.
        if (UUID_DEVICE_NAME.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }


}
