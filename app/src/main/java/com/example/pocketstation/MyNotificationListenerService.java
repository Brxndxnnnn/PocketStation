package com.example.pocketstation;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Handler;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MyNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "MyNotificationListener";

    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    //    private String targetDeviceName = "BlueNRG"; // Replace with your BLE device name
    private boolean isConnected = false;
    private final Handler connectionHandler = new Handler();

    // UUIDs for the service and characteristic on your BLE device
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    List<String> APP_LIST = Arrays.asList("Whatsapp", "Telegram", "Instagram");

    public MyNotificationListenerService() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startConnectionRetry();
    }

    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss"); // Day/Month, Time with seconds
        Calendar calendar = Calendar.getInstance();
        return sdf.format(calendar.getTime());
    }

    private void startConnectionRetry() {
        connectionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnected) {
                    initializeBLEConnection();
                    connectionHandler.postDelayed(this, 10000); // Retry every 10 seconds
                }
            }
        }, 10000);
    }

    @SuppressLint("MissingPermission")
    private void initializeBLEConnection() {
        bluetoothLeScanner.startScan(scanCallback);
        Log.d(TAG, "Started BLE scan");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.d("Brandon", "Found device: " + device.getName() + ", " + device.getAddress());
            if (device.getAddress().equals("E4:22:9C:C9:20:BA")) {
                bluetoothLeScanner.stopScan(scanCallback);
                bluetoothGatt = device.connectGatt(null, true, gattCallback);
                Log.d("Brandon", "Connecting to device: " + device.getName() + ", " + device.getAddress());
            }
        }
    };

    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                String deviceName = gatt.getDevice().getName(); // Get the connected device name
                isConnected = true;
                sendBLEStatusBroadcast(true, deviceName);
                bluetoothGatt = gatt;
                bluetoothGatt.discoverServices(); // Start service discovery

                // Add a delay (use Handler or Thread for delay)
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        // Once the delay is done, send the date/time
//                        String dateTime = getCurrentDateTime();
//                        sendNotificationDataOverBLE("DT:" + dateTime);
//                    }
//                }, 3000); // Delay of 3 seconds (3000 ms)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                isConnected = false;
                sendBLEStatusBroadcast(false, null);
                bluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    String dateTime = getCurrentDateTime();
                    sendNotificationDataOverBLE("DT:" + dateTime);
                }
            }
        }
    };

    private void sendBLEStatusBroadcast(boolean connected, String deviceName) {
        Intent intent = new Intent("com.example.pocketstation.BLE_STATUS");
        intent.putExtra("connected", connected);
        intent.putExtra("deviceName", deviceName);
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        String title = sbn.getNotification().extras.getString("android.title");
        String text = sbn.getNotification().extras.getString("android.text");

        Log.d(TAG, "Notification from: " + packageName); // Returns App Name
        Log.d(TAG, "Title: " + title); // Return Sender Name
        Log.d(TAG, "Text: " + text); // Return Message Content + sometimes ## new messages

        String app_name = getApplicationName(packageName);

        if (title != null && text != null && filterNotifications(app_name, title, text)) {
            if (Objects.equals(app_name, "Whatsapp")) app_name = "WS";
            if (Objects.equals(app_name, "Telegram")) app_name = "TL";
            if (Objects.equals(app_name, "Instagram")) app_name = "IG";

            title = stripBeforeFirstWhitespace(title);
            title = trimToBytes(title, 7);
            text = trimToBytes(text, 9);

            String message = app_name + ":" + title + ":" + text;
            sendNotificationDataOverBLE(message); // Send the notification data to the BLE device
        }
    }
    @SuppressLint("MissingPermission")
    private void sendNotificationDataOverBLE(String data) {
        if (writeCharacteristic != null && bluetoothGatt != null) {
            writeCharacteristic.setValue(data.getBytes());
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            if (success) {
                Log.d(TAG, "Notification data sent: " + data);
            } else {
                Log.e(TAG, "Failed to send notification data over BLE");
            }
        } else {
            Log.e(TAG, "BLE not connected or characteristic not found");
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "Notification Removed: " + sbn.getPackageName());
    }

    public String getApplicationName(String packageName) {
        for (String appName : APP_LIST) {
            if (packageName.toLowerCase().contains(appName.toLowerCase())) {
                return appName;
            }
        }
        return "INVALID";
    }

    // Return true if notification is OK to send out
    public Boolean filterNotifications(String appName, String sender, String text) {
        if (appName == "INVALID") return false; // NOT registered app, dont send notif
        else if (text == "Checking for new messages") return false; // Some whatsapp checks, dont send
        else if (text.contains("new messages")) return false;
        else return true;
    }

    public static String trimToBytes(String content, int byteLimit) {
        try {
            // Convert the string to bytes (using UTF-8 or another encoding as needed)
            byte[] contentBytes = content.getBytes("UTF-8");

            // If the content is already within the limit, return it as is
            if (contentBytes.length <= byteLimit) {
                return content;
            }

            // Find the maximum length of bytes that form a valid UTF-8 string within the limit
            int validLength = byteLimit;
            while (byteLimit > 0 && (contentBytes[byteLimit] & 0xC0) == 0x80) {
                byteLimit--;  // Move back to avoid cutting off a multi-byte character
            }

            // Convert the valid bytes back to a string
            return new String(contentBytes, 0, validLength, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            // Handle the encoding error if needed
            e.printStackTrace();
            return content;
        }
    }

    public static String stripBeforeFirstWhitespace(String str) {
        int spaceIndex = str.indexOf(' '); // Find the index of the first whitespace
        if (spaceIndex != -1) {
            return str.substring(0, spaceIndex); // Return the substring before the first whitespace
        }
        return str; // If no whitespace is found, return the original string
    }
}