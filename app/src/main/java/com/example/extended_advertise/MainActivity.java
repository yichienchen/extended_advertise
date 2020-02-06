package com.example.extended_advertise;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import static com.example.extended_advertise.Functions.intToByte;


public class MainActivity extends AppCompatActivity {

    public static final String TAG = "chien";

    /*調整輸入參數*/
    static String Data = "CHENYICHIENCHENYI123456789CHENYICHIENCHENYI123456789CHENYICHIENCHENYI123456789CHENYICHIENCHENYI123456789HENYICHIENCHENYHENCHENYHENYICHIENCHENYHENYICHIENCHENYHENY123456hvjghjghjfgsfgfgdfhdhgx";
    int pdu_size ; //純data，不包含id跟manufacturer specific data的flags及第幾個packet

    static byte[][] adv_packet;
    static int x;
    byte[] id_byte = new byte[4];

    AdvertiseCallback mAdvertiseCallback;
    BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    BluetoothAdapter mBluetoothAdapter;

    private Button btn_on;
    private Button btn_off;

    static Map<Integer, AdvertiseCallback> mAdvertiseCallbacks;
    static Map<Integer, AdvertisingSetCallback> extendedAdvertiseCallbacks;
    static Map<Integer, Long> mAdvertiseStartTimestamp;
    static Map<Integer, PendingIntent> mScheduledPendingIntents;
    static AlarmManager mAlarmManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
        permission();
        issupported();

        //設定用戶id
        id_byte = new byte[] {7,39,116,18};

        btn_on=findViewById(R.id.button_on);
        btn_off=findViewById(R.id.button_off);
        btn_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAdvertising();
            }
        });

        btn_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAdvertising();
            }
        });

        mAdvertiseCallbacks = new TreeMap<>();
        extendedAdvertiseCallbacks = new TreeMap<>();
        mAdvertiseStartTimestamp = new HashMap<>();
        mScheduledPendingIntents = new HashMap<>();
        mAlarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);

        btn_off.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG,"onDestroy() called");
        stopAdvertising();
    }

    private void initialize() {
        if (mBluetoothLeAdvertiser == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter != null) {
                    mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                }
            }
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startAdvertising(){
        Log.e(TAG, "Service: Starting Advertising");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            adv_packet=data_seg(255);
        }else {
            adv_packet = data_seg(31);
        }

        if (mAdvertiseCallback == null) {
            if (mBluetoothLeAdvertiser != null) {
                for (int q=1;q<x;q++){
                    startBroadcast(q);
                }
            }
        }
        btn_on.setVisibility(View.INVISIBLE);
        btn_off.setVisibility(View.VISIBLE);
    }

    public void stopAdvertising(){
        if (mBluetoothLeAdvertiser != null) {
            //mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            for (int q=1;q<x;q++){
                stopBroadcast(q);
            }

            mAdvertiseCallback = null;
        }
        btn_off.setVisibility(View.INVISIBLE);
        btn_on.setVisibility(View.VISIBLE);
    }

    private void startBroadcast(Integer id) {
        String localName =  String.valueOf(id) ;
        BluetoothAdapter.getDefaultAdapter().setName(localName);

        //BLE4.0
        AdvertiseSettings settings =  buildAdvertiseSettings();
        AdvertiseData advertiseData = buildAdvertiseData(id);
        AdvertiseData scanResponse = buildAdvertiseData_scan_response(id);
        //mBluetoothLeAdvertiser.startAdvertising(settings,advertiseData,scanResponse,new MyAdvertiseCallback(id));  //包含 scan response  BLE4.0

        //BLE 5.0
        AdvertiseData advertiseData_extended = buildAdvertiseData_extended();
        AdvertiseData periodicData = buildAdvertiseData_periodicData();
        AdvertisingSetParameters parameters = buildAdvertisingSetParameters();
        PeriodicAdvertisingParameters periodicParameters = buildperiodicParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mBluetoothLeAdvertiser.startAdvertisingSet(parameters,advertiseData_extended,null,null,null,0,0,new ExtendedAdvertiseCallback(id));
            //mBluetoothLeAdvertiser.startAdvertisingSet(parameters,advertiseData,scanResponse,periodicParameters,periodicData,callback);
        }
    }

    private void stopBroadcast(Integer id) {
        final AdvertiseCallback adCallback = mAdvertiseCallbacks.get(id);
        final AdvertisingSetCallback exadCallback = extendedAdvertiseCallbacks.get(id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (exadCallback != null) {
                try {
                    if (mBluetoothLeAdvertiser != null) {
                        //BLE 5.0
                        mBluetoothLeAdvertiser.stopAdvertisingSet(exadCallback);
                    }
                    else {
                        Log.w(TAG,"Not able to stop broadcast; mBtAdvertiser is null");
                    }
                }
                catch(RuntimeException e) { // Can happen if BT adapter is not in ON state
                    Log.w(TAG,"Not able to stop broadcast; BT state: {}");
                }
                removeScheduledUpdate(id);
                mAdvertiseCallbacks.remove(id);
            }
            //Log.e(TAG,id +" Advertising successfully stopped.");
        }else {
            if (adCallback != null) {
                try {
                    if (mBluetoothLeAdvertiser != null) {
                        //BLE 4.0
                        mBluetoothLeAdvertiser.stopAdvertising(adCallback);
                    }
                    else {
                        Log.w(TAG,"Not able to stop broadcast; mBtAdvertiser is null");
                    }
                }
                catch(RuntimeException e) { // Can happen if BT adapter is not in ON state
                    Log.w(TAG,"Not able to stop broadcast; BT state: {}");
                }
                removeScheduledUpdate(id);
                mAdvertiseCallbacks.remove(id);
            }
            Log.e(TAG,id +" Advertising successfully stopped");
        }
    }

    public class MyAdvertiseCallback extends AdvertiseCallback {
        private final Integer _id;
        private MyAdvertiseCallback(Integer id) {
            _id = id;
        }
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Advertising failed errorCode: "+errorCode);
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    Log.e(TAG,"ADVERTISE_FAILED_ALREADY_STARTED");
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    Log.e(TAG,"ADVERTISE_FAILED_DATA_TOO_LARGE");
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG,"ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    Log.e(TAG,"ADVERTISE_FAILED_INTERNAL_ERROR");
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    Log.e(TAG,"ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                    break;
                default:
                    Log.e(TAG,"Unhandled error : "+errorCode);
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.e(TAG, _id +" Advertising successfully started");
            Log.d(TAG,"id:" +_id);
            mAdvertiseCallbacks.put(_id, this);
        }
    }

    public class ExtendedAdvertiseCallback extends AdvertisingSetCallback {
        private final Integer _id;
        private ExtendedAdvertiseCallback(Integer id) {
            _id = id;
        }
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
            //Log.e(TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: " + status );
            if (status==AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED)
                Log.e(TAG, "ADVERTISE_FAILED_ALREADY_STARTED");
            else if (status==AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                Log.e(TAG, "ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
            else if (status==AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
                Log.e(TAG, "ADVERTISE_FAILED_DATA_TOO_LARGE");
            else if (status==AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
                Log.e(TAG, "ADVERTISE_FAILED_INTERNAL_ERROR");
            else if (status==AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                Log.e(TAG, "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
            else if (status==AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                Log.e(TAG, "id: " + _id + " (ADVERTISE_SUCCESS)");
                extendedAdvertiseCallbacks.put(_id,this);
            }
        }
        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            Log.e(TAG, "onAdvertisingSetStopped:" + _id);
        }
    }

    public static AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(0);
        //settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        //settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    public static AdvertisingSetParameters buildAdvertisingSetParameters() {
        AdvertisingSetParameters.Builder parametersBuilder = new AdvertisingSetParameters.Builder()
                .setConnectable(false)
                .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM);
        return parametersBuilder.build();
    }

    public static PeriodicAdvertisingParameters buildperiodicParameters() {
        PeriodicAdvertisingParameters.Builder periodicparametersBuilder = new PeriodicAdvertisingParameters.Builder()
                .setInterval(100);
        return periodicparametersBuilder.build();
    }

    static AdvertiseData buildAdvertiseData(Integer id) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(true);
        dataBuilder.addManufacturerData(0xffff,adv_packet[id]);
        return dataBuilder.build();
    }

    static AdvertiseData buildAdvertiseData_extended() {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(true);
        Log.e(TAG,"data: "+Data.getBytes().length);
        dataBuilder.addManufacturerData(0xffff,Data.getBytes());
        return dataBuilder.build();
    }

    static AdvertiseData buildAdvertiseData_scan_response(Integer id ) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.addManufacturerData(0xffff,adv_packet[id]);
        return dataBuilder.build();
    }

    static AdvertiseData buildAdvertiseData_periodicData() {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        byte[] data = {0x00,0x11,0xf,0x1a};
        dataBuilder.addManufacturerData(0xffff,data);
        return dataBuilder.build();
    }

    static void removeScheduledUpdate(Integer id) {
        PendingIntent pendingIntent = mScheduledPendingIntents.remove(id);
        if (pendingIntent == null) {
            return;
        }
        mAlarmManager.cancel(pendingIntent);
    }


    public byte[][] data_seg(int X) {
        pdu_size = X-3-2-4;
        for(int c=Data.length();(Data.length())%pdu_size!=0;c++){
            Data=Data+"0";
        }
        byte[] byte_data = Data.getBytes();
        int pack_num = 1;
        int coun = 0;
        x =(byte_data.length/pdu_size)+1;
        byte[][] adv_byte = new byte[x][pdu_size+5];
        for (int counter = byte_data.length; counter >0; counter = counter-pdu_size) {
            if (counter>=pdu_size){
                adv_byte[pack_num][0] = intToByte(pack_num);
                System.arraycopy(id_byte,0,adv_byte[pack_num],1,id_byte.length);
                System.arraycopy(byte_data,coun,adv_byte[pack_num],5,pdu_size);
//                Log.e(TAG,"adv_byte: "+byte2HexStr(adv_byte[pack_num])+";  counter: "+counter + ";  length: "+adv_byte[pack_num].length);
//                Log.e(TAG,"coco"+byte_len+" pack_num: "+pack_num);
                pack_num++;
                coun=coun+pdu_size;
            }else {
                adv_byte[pack_num][0] = intToByte(pack_num);
                Log.e(TAG,"pack_num="+pack_num);
                System.arraycopy(id_byte,0,adv_byte[pack_num],1,id_byte.length);
                System.arraycopy(byte_data,coun,adv_byte[pack_num],5,pdu_size);
//                Log.e(TAG,"adv_byte: "+byte2HexStr(adv_byte[pack_num])+";  counter: "+counter + ";  length: "+adv_byte[pack_num].length);
//                Log.e(TAG,"coco"+byte_len+" pack_num: "+pack_num);
            }
        }
        return adv_byte;
    }

    public void issupported(){
        if(!mBluetoothAdapter.isMultipleAdvertisementSupported()){
            Toast.makeText(this,"Advertisement 不支援",Toast.LENGTH_SHORT).show();
        }
        Log.e(TAG,"isMultipleAdvertisementSupported "+mBluetoothAdapter.isMultipleAdvertisementSupported());
        Log.e(TAG,"isEnabled "+mBluetoothAdapter.isEnabled());
        Log.e(TAG,"isDiscovering "+mBluetoothAdapter.isDiscovering());
        Log.e(TAG,"isOffloadedScanBatchingSupported "+mBluetoothAdapter.isOffloadedScanBatchingSupported());
        Log.e(TAG,"isOffloadedFilteringSupported "+mBluetoothAdapter.isOffloadedFilteringSupported());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG,"isLeCodedPhySupported "+mBluetoothAdapter.isLeCodedPhySupported());
            Log.e(TAG,"isLeExtendedAdvertisingSupported "+mBluetoothAdapter.isLeExtendedAdvertisingSupported());
            Log.e(TAG,"isLePeriodicAdvertisingSupported "+mBluetoothAdapter.isLePeriodicAdvertisingSupported());
            Log.e(TAG,"isLe2MPhySupported "+mBluetoothAdapter.isLe2MPhySupported());

            Log.e(TAG,"getLeMaximumAdvertisingDataLength: "+mBluetoothAdapter.getLeMaximumAdvertisingDataLength());
        }


    }

    public void permission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, 1);
        }
    }
}
