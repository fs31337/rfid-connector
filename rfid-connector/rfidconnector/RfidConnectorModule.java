package nal.com.rfidconnector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;

import jp.co.tss21.uhfrfid.dotr_android.DOTR_Util;
import jp.co.tss21.uhfrfid.dotr_android.EnMaskFlag;
import jp.co.tss21.uhfrfid.dotr_android.EnMemoryBank;
import jp.co.tss21.uhfrfid.dotr_android.OnDotrEventListener;
import jp.co.tss21.uhfrfid.dotr_android.TagAccessParameter;

public class RfidConnectorModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private final ReactApplicationContext reactContext;
    private BluetoothAdapter bluetoothAdapter;
    private DOTR_Util mDotrUtil;
    private boolean mIsNoRepeat = false;
    private EnMemoryBank memoryBank;
    private int offset = 0;
    private int words = 1;

    private boolean isWaitingDisconnectSuccess = false;
    private String deviceAddress;
    private Promise promiseToConnect;

    private Boolean isScanFinish = true;
//    private String[] listBLTFilter  = Constants.listFilterDefault;
    private String[] listBLTFilter  = {};

    public RfidConnectorModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        registerBluetoothStateReceiver();
        reactContext.addActivityEventListener(activityEventListener);
        reactContext.addLifecycleEventListener(this);

    }

    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            super.onActivityResult(activity, requestCode, resultCode, data);
            if (requestCode == Constants.DISCOVERY_REQUEST && resultCode > 0) {
                Utils.checkPermissions(reactContext,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, Constants.REQUEST_ACCESS_COARSE_LOCATION);
            } else {
//            if (listener != null) {
//                listener.onErrorBluetoothNotEnable();
//            }
            }
        }

        @Override
        public void onNewIntent(Intent intent) {
        }

    };

    @Override
    public String getName() {
        return "RfidConnector";
    }

    @ReactMethod
    public void setListFilterBlueToothPrefix(ReadableArray listBLTFilter){
        ReadableNativeArray nativeArray = (ReadableNativeArray)listBLTFilter;
        ArrayList<Object> list = nativeArray.toArrayList();
        this.listBLTFilter = list.toArray(new String[list.size()]);
    }


    @ReactMethod
    public void getCurrentState(Promise promise) {
        try {
            String state = Constants.BLUETOOTH_UNKNOWN;
            if (bluetoothAdapter != null) {
                switch (bluetoothAdapter.getState()) {
                    case BluetoothAdapter.STATE_ON:
                        state = Constants.BLUETOOTH_ON;
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        state = Constants.BLUETOOTH_OFF;
                }
            }

            WritableMap map = Arguments.createMap();
            map.putString(Constants.STATUS, state);


            promise.resolve(Utils.convertParams(Constants.TYPE.BLT_STATE_CHANGE,map));
        } catch (Exception e) {
            promise.reject("NO_BLUETOOTH", e);
        }
    }

    @ReactMethod
    public void setBluetoothOn(Callback callback) {
        if (bluetoothAdapter != null) {
            bluetoothAdapter.enable();
        }
        callback.invoke(null, bluetoothAdapter != null);
    }

    @ReactMethod
    public void setBluetoothOff(Callback callback) {
        if (bluetoothAdapter != null) {
            bluetoothAdapter.disable();
        }
        callback.invoke(null, bluetoothAdapter != null);
    }

    @ReactMethod
    public void startScanBlueTooth(Promise promise){
        try{
            Boolean isStartSuccess = scanBlueTooth();
            if(!isStartSuccess) throw new Exception("Can't Start Scan BlueTooth");
            isScanFinish = false;
            promise.resolve(true);
        }catch (Exception e){
            promise.reject(e);
        }
    }

    @ReactMethod
    public void stopScanBlueTooth(Promise promise){
        try{
            if(!isScanFinish)
            reactContext.unregisterReceiver(bluetoothDiscoveryReceiver);
            isScanFinish = true;
            boolean isStopped =emitEventBltScanFinish();
            promise.resolve(isStopped);
        }catch (Exception e){
            promise.reject(e);
        }

    }

    @ReactMethod
    public void connectToDevice(String deviceAddr,Promise promise){
        deviceAddress = deviceAddr;
        promiseToConnect = promise;
        if(!isBlueToothOn()){
            requestDiscoveryBluetooth();
            promise.reject("ERROR","Bluetooth not enable");
            return;
        }

        if(!isHasPermission()){
            promise.reject("ERROR","Required permission access location");
            return;
        }
        if(mDotrUtil == null){
            mDotrUtil = new DOTR_Util();
            mDotrUtil.setOnDotrEventListener(onDotrEventListener);
        }

        if(mDotrUtil.isConnect() ){
            isWaitingDisconnectSuccess = true;
            mDotrUtil.disconnect();

        }

        if(!isWaitingDisconnectSuccess)
            dispatchDotrConnectDevice(deviceAddr,promise);

    }

    @ReactMethod
    public void disConnectToDevice(Promise promise){
        if(mDotrUtil != null && mDotrUtil.isConnect() ){
             if( mDotrUtil.disconnect()){
                 promise.resolve("Disconnect Success");
             }else{
                 promise.reject("ERROR","Disconnect Fail");
             }

        }else{
            promise.reject("ERROR","Not device connected");
        }


    }

    public void dispatchDotrConnectDevice(String deviceAddr,Promise promise){
        if( mDotrUtil.connect(deviceAddr)){
            mDotrUtil.setDefaultParameter();
            promise.resolve("connect success");
        }
        else {
            promise.reject("ERROR","connect fail");
        }
    }

    public boolean isHasPermission(){
        return Utils.checkPermissions(reactContext,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},Constants.REQUEST_ACCESS_COARSE_LOCATION);
    }


    private boolean isBlueToothOn(){
        if(bluetoothAdapter == null)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter.isEnabled();
    }


    private boolean scanBlueTooth(){
        if(!isBlueToothOn()){
            requestDiscoveryBluetooth();
            return false ;
        }
        if(!isHasPermission())
            return false;

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        reactContext.registerReceiver(bluetoothDiscoveryReceiver, filter);
        if(bluetoothAdapter.isDiscovering())
            return true;
        return bluetoothAdapter.startDiscovery();



    }

    private void requestDiscoveryBluetooth() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, Constants.DISCOVERABLE_DURATION);
        reactContext.startActivityForResult(discoverableIntent, Constants.DISCOVERY_REQUEST,null);
    }

    private boolean emitEventBltScanFinish(){
        WritableMap data = Arguments.createMap();
        emitDeviceEvent(Constants.BLUETOOTH_EVENT,Utils.convertParams(Constants.TYPE.BLT_DISCOVERY_DEVICE_FINISH,data));
       if(!isScanFinish)
        reactContext.unregisterReceiver(bluetoothDiscoveryReceiver);
        isScanFinish = true;
        return bluetoothAdapter.cancelDiscovery();
    }


    private BroadcastReceiver bluetoothDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                if(deviceName == null || TextUtils.isEmpty(deviceName) || !Utils.isMatchWithPrefix(listBLTFilter,deviceName))
                    return;

                WritableMap data = Arguments.createMap();
                data.putString(Constants.DEVICE_NAME,device.getName());
                data.putString(Constants.DEVICE_ADDRESS,device.getAddress());
                WritableMap deviceInfo = Arguments.createMap();
                deviceInfo.putMap(Constants.DEVICE_INFO,data);

                emitDeviceEvent(Constants.BLUETOOTH_EVENT,Utils.convertParams(Constants.TYPE.BLT_DISCOVERY_DEVICE,deviceInfo));

            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                WritableMap data = Arguments.createMap();
                emitDeviceEvent(Constants.BLUETOOTH_EVENT,Utils.convertParams(Constants.TYPE.BLT_DISCOVERY_STARTED,data));
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                emitEventBltScanFinish();
            }
        }
    };



    private void registerBluetoothStateReceiver() {
        if (bluetoothAdapter == null) {
            return;
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        reactContext.registerReceiver(mReceiver, filter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                String stringState = Constants.BLUETOOTH_UNKNOWN;

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        stringState = Constants.BLUETOOTH_OFF;
                        if(mDotrUtil != null && mDotrUtil.isConnect() ){
                            mDotrUtil.disconnect();

                        }
                        mDotrUtil = null;
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        stringState = Constants.BLUETOOTH_TURNING_OF;
                        break;
                    case BluetoothAdapter.STATE_ON:
                        stringState = Constants.BLUETOOTH_ON;
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        stringState = Constants.BLUETOOTH_TURNING_ON;
                        break;
                }

                WritableMap map = Arguments.createMap();
                map.putString(Constants.STATUS, stringState);

                emitDeviceEvent(Constants.BLUETOOTH_EVENT, Utils.convertParams(Constants.TYPE.BLT_STATE_CHANGE,map));

            }
        }
    };

    private void emitDeviceEvent(String eventName, @Nullable WritableMap eventData) {
        // A method for emitting from the native side to JS
        // https://facebook.github.io/react-native/docs/native-modules-android.html#sending-events-to-javascript

        if (reactContext.hasActiveCatalystInstance()) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, eventData);
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == Constants.REQUEST_ACCESS_COARSE_LOCATION) {
            if (Utils.isGrantedAllPermissions(reactContext,grantResults)) {
                scanBlueTooth();
            } else {
                for (int i = 0; i < permissions.length; i++) {
                    final String permission = permissions[i];
                    if (ActivityCompat.shouldShowRequestPermissionRationale(reactContext.getCurrentActivity(), permission) == false) {
                        Utils.showAskPermissionSetting(reactContext);
                        break;
                    }
                }
            }
        }
    }


    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {


    }

    @Override
    public void onHostDestroy() {
        try{
            reactContext.unregisterReceiver(mReceiver);
            reactContext.unregisterReceiver(bluetoothDiscoveryReceiver);
        }catch (Exception e){
            e.printStackTrace(); 
        }

    }

    private void sendDataByEmitDevice(String status,String data){
        WritableMap map = Arguments.createMap();
        map.putString(Constants.STATUS, status);
        map.putString(Constants.DATA, data);
        emitDeviceEvent(Constants.BLUETOOTH_EVENT, Utils.convertParams(Constants.TYPE.DOTR_EVENT,map));
    }

    private OnDotrEventListener onDotrEventListener = new OnDotrEventListener() {
        @Override
        public void onConnected() {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_CONNECT,null);
        }

        @Override
        public void onDisconnected() {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_DISCONNECT,null);
            if(isWaitingDisconnectSuccess){
                isWaitingDisconnectSuccess = false;
                if(deviceAddress != null && promiseToConnect != null)
                    dispatchDotrConnectDevice(deviceAddress,promiseToConnect);
            }

        }

        @Override
        public void onLinkLost() {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_LINKLOST,null);

        }

        @Override
        public void onTriggerChaned(boolean b) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_TRIGGER_CHANGED,b?"true":"false");
            if (b) {
                if (!mIsNoRepeat)
                    mDotrUtil.clearAccessEPCList();

                TagAccessParameter param = new TagAccessParameter();

                memoryBank = EnMemoryBank.valueOf("RESERVED");
                param.setMemoryBank(memoryBank);
                param.setWordOffset(offset);
                param.setWordCount(words);

                mDotrUtil.readTag(param, false, EnMaskFlag.None, 0);
            } else {
                //トリガを離したら読取りをストップ
                mDotrUtil.stop();
            }

        }

        @Override
        public void onInventoryEPC(String s) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_IVENTORY_EPC,s);

        }

        @Override
        public void onReadTagData(String s, String s1) {
            WritableMap map = Arguments.createMap();
            map.putString(Constants.STATUS, Constants.DOTR_TYPE.ON_READ_TAG_DATA);
            WritableMap data = Arguments.createMap();
            data.putString("tagData",s);
            data.putString("epc",s1);

            map.putMap(Constants.DATA, data);
            emitDeviceEvent(Constants.BLUETOOTH_EVENT, Utils.convertParams(Constants.TYPE.DOTR_EVENT,map));

        }

        @Override
        public void onWriteTagData(String s) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_WRITE_TAG_DATA,s);
        }

        @Override
        public void onUploadTagData(String s) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_UPLOAD_TAG_DATA,s);
        }

        @Override
        public void onTagMemoryLocked(String s) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_TAG_MEMORY_LOCKED,s);

        }

        @Override
        public void onScanCode(String s) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_SCAN_CODE,s);
        }

        @Override
        public void onScanTriggerChanged(boolean b) {
            sendDataByEmitDevice(Constants.DOTR_TYPE.ON_SCAN_TRIGGER_CHANGED,b?"true":"false");

        }
    };

}
