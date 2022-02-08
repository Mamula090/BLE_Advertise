package com.adria.ble_advertise;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class AdvertiserService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID_SERVICE = "com.adria.AdvertiserService";
    public static final String NOTIFICATION_CHANNEL_ID_INFO = "com.package.download_info";

    private static final String TAG = AdvertiserService.class.getSimpleName();

    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    public static boolean running = false;

    public static final String ADVERTISING_FAILED ="advertising_failed";
    public static final String ADVERTISING_FAILED_EXTRA_CODE = "failureCode";
    public static final int ADVERTISING_TIMED_OUT = 6;

    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseCallback mAdvertiseCallback;
    private Handler mHandler;
    private Runnable timeoutRunnable;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer server;

    private long TIMEOUT = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    @Override
    public void onCreate() {
        running = true;
        initialize();
        startAdvertising();
        setTimeout();
        super.onCreate();
        Log.i(TAG,"manufacturer specific data(room number):"+ Arrays.toString(Constants.manufacturerSpecificData));
    }

    @Override
    public void onDestroy() {
        running = false;
        stopAdvertising();
        mHandler.removeCallbacks(timeoutRunnable);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    private void initialize() {
        if (mBluetoothLeAdvertiser == null) {
            BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                server = mBluetoothManager.openGattServer(this, serverCallback);
                initServer();
                mBluetoothAdapter = mBluetoothManager.getAdapter();
                if (mBluetoothAdapter != null) {
                    mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                } else {
                    Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.bt_null), Toast.LENGTH_LONG).show();
            }
        }
    }

    //service definition
    @SuppressLint("MissingPermission")
    private void initServer(){

        //creating service
        BluetoothGattService bluetoothGattService = new BluetoothGattService(Constants.SERVICEUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //creating characteristics
        BluetoothGattCharacteristic dataCharacteristic = new BluetoothGattCharacteristic(Constants.DATACHARUUID,
                BluetoothGattCharacteristic.PROPERTY_READ &
                        BluetoothGattCharacteristic.PROPERTY_WRITE &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY &
                        BluetoothGattCharacteristic.PROPERTY_INDICATE &
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ &
                        BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattCharacteristic commCharacteristic = new BluetoothGattCharacteristic(Constants.COMMCHARUUID,
                BluetoothGattCharacteristic.PROPERTY_READ &
                        BluetoothGattCharacteristic.PROPERTY_WRITE &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY &
                        BluetoothGattCharacteristic.PROPERTY_INDICATE &
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ &
                        BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        //adding characteristics to the service
        bluetoothGattService.addCharacteristic(dataCharacteristic);
        bluetoothGattService.addCharacteristic(commCharacteristic);
        //server.addService(bluetoothGattService);

        @SuppressLint("MissingPermission")
        boolean serviceadded = server.addService(bluetoothGattService);
        sendNotification("WAS SERVICE ACTUALLY ADDED: " + serviceadded);

    }

    private void setTimeout(){
        mHandler = new Handler();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "AdvertiserService has reached timeout of "+TIMEOUT+" milliseconds, stopping advertising.");
                sendFailureIntent(ADVERTISING_TIMED_OUT);
                stopSelf();
            }
        };
        mHandler.postDelayed(timeoutRunnable, TIMEOUT);
    }

    @SuppressLint("MissingPermission")
    private void startAdvertising() {

        goForeground();

        Log.d(TAG, "Service: Starting Advertising");

        if (mAdvertiseCallback == null) {
            AdvertiseSettings settings = buildAdvertiseSettings();
            AdvertiseData data = buildAdvertiseData();
            AdvertiseData scandata = buildScanResponseData();

            // debug
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append("ParcelUUID: " + Constants.Service_UUID);
            java.util.Map<android.os.ParcelUuid, byte[]> serviceData = data.getServiceData();
            for(java.util.Map.Entry<android.os.ParcelUuid, byte[]> entry : serviceData.entrySet()) {
                strBuilder.append("\n").append(entry.getKey() + ": " + new String(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8)).append("\n");
            }
            sendNotification(strBuilder.toString());
            // end debug

            mAdvertiseCallback = new SampleAdvertiseCallback();

            if (mBluetoothLeAdvertiser != null) {
                mBluetoothLeAdvertiser.startAdvertising(settings, data, scandata,
                        mAdvertiseCallback);
            }
        }
    }

    private void goForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        Notification n = new Notification.Builder(this)
                .setContentTitle("Advertising device via Bluetooth")
                .setContentText("This device is discoverable to others nearby.")
                .setSmallIcon(R.drawable.ic_launcherr)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, "App Service", NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID_INFO, "Download Info", NotificationManager.IMPORTANCE_DEFAULT));
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, n);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        Log.d(TAG, "Service: Stopping Advertising");
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdvertiseCallback = null;
        }
    }

    @SuppressLint("MissingPermission")
    private AdvertiseData buildAdvertiseData() {
        mBluetoothAdapter.setName("THE_PERIPHERAL");
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(false);
        dataBuilder.setIncludeTxPowerLevel(true);
        dataBuilder.addServiceUuid(Constants.Service_UUID);
        dataBuilder.addServiceData(Constants.Service_UUID, "Data".getBytes(StandardCharsets.UTF_8));
        dataBuilder.addManufacturerData(44718, Constants.manufacturerSpecificData);  //Adria-Lock - Manufacturer data (Bluetooth Core 4.1): Company: Reserved ID <0xAEAE> 0x010001
                                           //             Manufacturer data: 0xAEAE010001
                                           // hex: 0xAEAE -> decimal: 44718
        return dataBuilder.build();
    }

    private AdvertiseData buildScanResponseData(){
        AdvertiseData.Builder scanResponseData = new AdvertiseData.Builder();
        scanResponseData.addServiceUuid(Constants.Service_UUID);
        scanResponseData.setIncludeTxPowerLevel(true);
        return scanResponseData.build();
    }

    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        settingsBuilder.setConnectable(true);
        settingsBuilder.setTimeout(30000);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        return settingsBuilder.build();
    }

    private class SampleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);

            Log.d(TAG, "Advertising failed reason:" + errorCode);
            sendFailureIntent(errorCode);
            stopSelf();

        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising successfully started");
        }
    }

    private void sendFailureIntent(int errorCode){
        Intent failureIntent = new Intent();
        failureIntent.setAction(ADVERTISING_FAILED);
        failureIntent.putExtra(ADVERTISING_FAILED_EXTRA_CODE, errorCode);
        sendBroadcast(failureIntent);
    }

    private BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                sendNotification("Client connected" + device.getName());

            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                sendNotification("client disconnected");
                // Restart Advertisements
                startAdvertising();
            }

            byte value[] = {0x03, 0x04, 0x52, 0x69, 0x63, 0x68, 0x61, 0x72, 0x64};
            super.onConnectionStateChange(device, status, newState);

            sendNotification("onConnectionStateChange device " + device.getAddress());
            // mNewAlert.setValue(value);
            // server.notifyCharacteristicChanged(device, mNewAlert, true);

            sendNotification("onServerConnectionState() - status=" + status
                    + " newState=" + newState + " device=" + device.getAddress());
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            sendNotification("WAS THE ONSERVICEADDED Function Called? " + status);
            sendNotification( service.getIncludedServices().toArray().toString());

            //mBluetoothGatt = mBluetoothDevice.connectGatt(getApplicationContext(), false, mGattCallback);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            byte[] bytes = value;
            String message = new String(bytes);
            sendNotification(message);
            server.sendResponse(device, requestId, 0, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            sendNotification("Descriptor write: " + descriptor.toString());
            if(responseNeeded){
                server.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, 0, value);
            }
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
        }
    };

    private void sendNotification(String message){
        Log.d(TAG, message);
    }

}
