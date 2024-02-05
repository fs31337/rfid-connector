package nal.com.rfidconnector;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import static androidx.core.app.ActivityCompat.requestPermissions;
import static androidx.core.content.ContextCompat.checkSelfPermission;


public class Utils {

//    public interface BluetoothDeviceSearchingListener {
//        void onFindBluetoothDevice(ScannerItem scannerItem);
//
//        void onStartScan();
//
//        void onStopScan();
//
//        void onErrorBluetoothNotEnable();
//    }


    public static boolean shouldAskPermission() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }
    public static boolean isDeniedPermission(Context context, String permission) {
        return shouldAskPermission() && checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isGrantedAllPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (isDeniedPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isGrantedAllPermissions(Context context,int[] grantResults) {
        int countGranted = 0;
        for (int granted: grantResults) {
            if (granted == PackageManager.PERMISSION_GRANTED) {
                countGranted ++;
            }
        }
        return countGranted == grantResults.length;
    }


    public static boolean checkPermissions(ReactApplicationContext context, String[] permissions, int requestCode) {

        if (isGrantedAllPermissions(context, permissions)) {
            return true;
        } else {
            requestPermissions( context.getCurrentActivity() , permissions, requestCode);
            return false;
        }
    }

    public static void openAppSetting(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void showAskPermissionSetting(final Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle("パーミッションの追加説明")
                .setMessage("リーダーを使用するには低精度の位置情報取得へのパーミッションが必要です")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAppSetting(context);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.create();
        alertDialogBuilder.show();
    }

    public static WritableMap convertParams( String type, WritableMap data){
        data.putString(Constants.BLUETOOTH_EVENT_TYPE, type);
        return data;
    }

    public static boolean isMatchWithPrefix(String[] listPrefix, String deviceName){
        if(listPrefix.length ==0)
            return true;

        boolean isMatch = false;
        for(int i = 0; i < listPrefix.length;i++){
            if(deviceName.startsWith(listPrefix[i])){
                isMatch = true;
                break;
            }
        }
        return isMatch;
    }


}
