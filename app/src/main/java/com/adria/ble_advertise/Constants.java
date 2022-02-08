package com.adria.ble_advertise;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;

import java.util.Base64;
import java.util.UUID;

public class Constants {

    public static final int REQUEST_ENABLE_BT = 1;

    public static final UUID SERVICEUUID = UUID.fromString("0000AE30-0000-1000-8000-00805F9B34FB");
    public static final ParcelUuid Service_UUID = ParcelUuid.fromString("0000AE30-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID DATACHARUUID = java.util.UUID.fromString("0000AE31-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID COMMCHARUUID = java.util.UUID.fromString("0000AE32-0000-1000-8000-00805F9B34FB");

    public static final byte[] manufacturerSpecificData = android.util.Base64.decode("NDI0", android.util.Base64.DEFAULT);
}
