package com.mobicloud.plugins.backgroundservice;

import static java.security.AccessController.getContext;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;
import com.mobicloud.plugins.MainActivity;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;


public class BackgroundService extends Service {

  public static BackgroundService instance;

  private static final String TAG = "BackgroundService";
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;

  private BluetoothAdapter bluetoothAdapter;
  private BluetoothLeScanner bluetoothLeScanner;
  private BluetoothStateReceiver bluetoothStateReceiver;
  private String macAddress;
  private BluetoothGatt bluetoothGatt;
  private volatile boolean isBleReady = false;
  private String pendingBlePayload = null;

  private String MQTT_URL = "";

  private String MQTT_USERNAME = "";

  private String MQTT_Password = "";

  private boolean isDeviceConnected = false;
  private final AtomicBoolean isScanning = new AtomicBoolean(false);
  private final Handler scanHandler = new Handler();

  // Define persistence
  MqttClientPersistence persistence = new MemoryPersistence();

  // Enable reconnect
  boolean useReconnect = true;

  // Define max inflight messages
  int maxInflight = 10;

  private static String DEVICE_TYPE = "";

  private static String TOPIC_to_Publish = " ";
  private static String BASE_URL = "";
  private static String BASIC_AUTH = "";
  private static String DEVICE_UUID = "";
  private static String API_SUFFIX = "";
  private static String API_PAYLOAD = "";

  private Context context;

  private static String authDlData = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Long lastAuthDlTime = null; // set this when MQTT DL arrives

  private MqttAndroidClient mqttAndroidClient;

  private BluetoothGattCharacteristic txCharacteristic;

  private BluetoothGattCharacteristic rxCharacteristics;

  private final long READ_INTERVAL = 5000;  // 5 seconds

  public BackgroundService() {
    // Default empty constructor - Required by Android OS
  }

  public BackgroundService(Context context) {
    this.context = context.getApplicationContext();  // Store the application context
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "Service created");
//    sendLog("Service created");
    instance = this;

    createNotificationChannel();
    startForegroundServiceCompat();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "Service started");

//    sendLog("Service started");
    setupBluetoothMonitoring();

    if(intent != null)
    {
      BASE_URL = intent.getStringExtra("baseURL");
      BASIC_AUTH = intent.getStringExtra("basicAuth");
      API_SUFFIX = intent.getStringExtra("apiSuffix");
      API_PAYLOAD = intent.getStringExtra("apiPayload");
      DEVICE_UUID = intent.getStringExtra("deviceUUID");
      DEVICE_TYPE = intent.getStringExtra("deviceType");
      macAddress = intent.getStringExtra("macAddress");

      if(Objects.equals(DEVICE_TYPE, "BLE")) {
        checkInitialBluetoothState();

        // System.out.println("Received macAddress: "+macAddress);

        // connectToDevice(macAddress);
      }
    }

    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void sendLog(String message) {
//    Log.d(TAG, message);
//    System.out.println(message);

    Intent intent = new Intent("com.mobicloud.logs");
    intent.putExtra("LOG", message);
    intent.putExtra("TAG", TAG);
    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
  }

  private final Runnable authDlCheckRunnable = new Runnable() {
    @Override
    public void run() {
      long currentTimeSec = System.currentTimeMillis() / 1000;
      long ttlRemaining = (lastAuthDlTime == null) ? 0 : (3600 - (currentTimeSec - lastAuthDlTime));

      if (ttlRemaining == 0) {
        if ((BASE_URL != null) && (BASIC_AUTH != null) && (API_SUFFIX != null)
                && (API_PAYLOAD != null) && (DEVICE_UUID != null)) {

          String finalUrl = BASE_URL + DEVICE_UUID + API_SUFFIX;
          String finalAuthHeader = "Basic " + BASIC_AUTH;

          System.out.println("Final Base Url is: "+finalUrl);
          sendHttpRequest(finalUrl, API_PAYLOAD, finalAuthHeader);

        } else {
          System.out.println("Require All the Details of HTTP request, Missing!!!");
        }
      } else {
//        System.out.println("TTL valid, Auth DL not required. Time left: " + ttlRemaining + "s");

//        sendLog("TTL valid, Auth DL not required. Time left: " + ttlRemaining + "s");
      }

      handler.postDelayed(authDlCheckRunnable, 1000); // check again after 1 sec
    }
  };

  private void sendHttpRequest(String urlString, String jsonPayload, String authHeader) {
    System.out.println(urlString);

    if (urlString == null || urlString.trim().isEmpty()) {
      Log.e("BackgroundService", "URL is empty or null, aborting HTTP request.");
      return;  // Don't start the thread if URL is invalid
    }

    new Thread(() -> {
        try {
            URL url = new URL(urlString);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization",authHeader);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if(responseCode == 200) {
              lastAuthDlTime = System.currentTimeMillis() / 1000;
            }
//            Log.d("BackgroundService", "HTTP Response Code: " + responseCode);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            System.out.println("HTTP Response is: "+response);

            // Parse the response JSON
            JSONObject jsonResponse = new JSONObject(response.toString());

            // Navigate into the nested structure
            JSONArray resultsArray = jsonResponse.getJSONArray("results");
            JSONObject resultObject = resultsArray.getJSONObject(0).getJSONObject("result");

            // Extract values
            String regulatorId = resultObject.getString("Regulatorid ");
            String data = resultObject.getString("Data");
            String sha = resultObject.getString("SHA");

            // Build the string same as JavaScript
            authDlData = String.format("{\"Regulatorid \": \"%s\",\"Data\" : \"%s\",\"SHA\" : \"%s\"}",regulatorId, data, sha);

            // Log or use the string
            Log.d(TAG,"Auth DL Data is: "+authDlData);
            System.out.println("authDLdata: " + authDlData);
//            sendLog("Auth DL Data to write to device is: "+authDlData);
            byte[] payload = authDlData.getBytes(StandardCharsets.UTF_8);

//            Log.d(TAG,"Payload to write to device is: "+authDlData);
//            writeDataToDevice(rxCharacteristics,authDlData);

        } catch (Exception e) {
            Log.e("BackgroundService", "HTTP request failed", e);
            System.out.println("Error Occured While Performing HTTP Request: "+e);
//            sendLog("Error Occured While Performing HTTP Request: "+e);
        }
    }).start();
  }

  private void createNotificationChannel() {
    NotificationChannel serviceChannel = new NotificationChannel(
      CHANNEL_ID,
      "Background Service",
      NotificationManager.IMPORTANCE_LOW
    );
    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.createNotificationChannel(serviceChannel);
    }
  }

  private void startForegroundServiceCompat() {
    Intent notificationIntent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(
      this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
    );

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentText("Service running in background.")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true);

    startForeground(NOTIFICATION_ID, notificationBuilder.build());
  }

  private void  setupBluetoothMonitoring() {
    BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);

//    sendLog("Inside setupBluetoothMonitoring");

    bluetoothStateReceiver = new BluetoothStateReceiver();
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(bluetoothStateReceiver, filter);

    if (bluetoothManager == null) {
      Log.e(TAG, "BluetoothManager is null");
      System.out.println("BluetoothManager is null");

//      sendLog("BluetoothManager is null");
      return;
    }

    bluetoothAdapter = bluetoothManager.getAdapter();

    if (bluetoothAdapter == null) {
      Log.e(TAG, "BluetoothAdapter is null");
      System.out.println("BluetoothAdapter is null");

//      sendLog("BluetoothAdapter is null");

      return;
    }

    if (!bluetoothAdapter.isEnabled()) {
      Log.e(TAG, "Bluetooth is off");
      System.out.println("BluetoothManage is OFF");

//      sendLog("Bluetooth is off");

      return;
    }

    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    if (bluetoothLeScanner == null) {
      Log.e(TAG, "BluetoothLeScanner is null");
      System.out.println("BluetoothLE Scanner is null");

//      sendLog("BluetoothLE Scanner is null");

    }

    Log.d(TAG, "Bluetooth monitoring initialized.");
    System.out.println("Bluetooth monitoring initialized");

//    sendLog("Bluetooth monitoring initialized");
//    scanHandler.postDelayed(scanRunnable, 5000);

  }


  private void checkInitialBluetoothState() {
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
      updateNotification("Service is running in the background.");
    } else {
      sendBluetoothNotification();
    }
  }

  private void sendBluetoothNotification() {
    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
    PendingIntent pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE
    );

    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentText("Bluetooth is Disabled. Tap to Enable.")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true);

    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  public void connectToDevice(String address) {
    System.out.println("Mac Address is: " + address);
//    sendLog("Received MAC: " + address);

    try {
      macAddress = address;

      if (bluetoothAdapter == null) {
        Log.w(TAG, "BluetoothAdapter is null — attempting fallback init");
        BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
          bluetoothAdapter = bluetoothManager.getAdapter();
        }
      }

      if (bluetoothAdapter == null) {
        Log.e(TAG, "Failed to get BluetoothAdapter.");
//        sendLog("BluetoothAdapter is still null.");
        return;
      }

      if (macAddress == null || macAddress.isEmpty()) {
        Log.e(TAG, "Invalid MAC address.");
//        sendLog("Invalid MAC address.");
        return;
      }

      // Check BLUETOOTH_CONNECT permission
      if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
//        sendLog("BLUETOOTH_CONNECT permission missing.");
        return;
      }

      // Check bonded devices first to avoid re-pairing
      BluetoothDevice bondedDevice = null;
      Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
      for (BluetoothDevice device : bondedDevices) {
        if (device.getAddress().equalsIgnoreCase(macAddress)) {
          bondedDevice = device;
          break;
        }
      }

      BluetoothDevice deviceToConnect;
      if (bondedDevice != null) {
        Log.d(TAG, "Bonded device found. Reusing it.");
//        sendLog("Bonded device found. Reusing it.");
        deviceToConnect = bondedDevice;
      } else {
        Log.w(TAG, "Device not bonded. Using getRemoteDevice.");
//        sendLog("Device not bonded. Using getRemoteDevice.");
        deviceToConnect = bluetoothAdapter.getRemoteDevice(macAddress);
      }

      int bondState = deviceToConnect.getBondState();
      Log.d(TAG, "Bond state: " + bondState);
//      sendLog("Bond state: " + bondState);

      // Optional: Only try to create bond if not already bonded
      if (bondState != BluetoothDevice.BOND_BONDED) {
        Log.i(TAG, "Device not bonded. Trying to create bond.");
//        sendLog("Device not bonded. Trying to create bond.");
        deviceToConnect.createBond(); // triggers pairing UI
        // Don't proceed with GATT connection until bonded
        return;
      }

      bluetoothGatt = deviceToConnect.connectGatt(this, true, gattCallback);

      Log.d(TAG, "Attempting to connect to BLE device: " + macAddress);
//      sendLog("Attempting to connect to BLE device: " + macAddress);

    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Invalid MAC format: " + e.getMessage());
//      sendLog("Invalid MAC format: " + e.getMessage());

    } catch (Exception e) {
      Log.e(TAG, "Exception during BLE connect: " + e);
//      sendLog("Exception during BLE connect: " + e);

//      scanHandler.postDelayed(scanRunnable, 5000);
      startScanningForDevice();
    }
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      if (newState == BluetoothGatt.STATE_CONNECTED) {
        Log.d(TAG, "Connected to BLE device.");
        System.out.println("Connected to BLE device");

//        sendLog("Connected to BLE device");

        isDeviceConnected = true;

        stopScanning();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
          Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
          return;
        }

        gatt.discoverServices();

      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        Log.d(TAG, "Disconnected from BLE device.");

//        sendLog("Disconnected from BLE device.");

        txCharacteristic = null;

        isDeviceConnected = false;

        // 🔁 Restart scan loop if disconnected
//        scanHandler.postDelayed(scanRunnable, 5000);
        startScanningForDevice();
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        handler.post(authDlCheckRunnable); // ✅ This stays

        BluetoothGattService service = gatt.getService(Constants.NORDIC_SERVICE_UUID);
        if (service != null) {

          txCharacteristic = service.getCharacteristic(Constants.TX_CHARACTERISTIC_UUID);
          rxCharacteristics = service.getCharacteristic(Constants.RX_CHARACTERISTIC_UUID);

          BleClientHolder.setGatt(gatt);
          BleClientHolder.setRxCharacteristic(rxCharacteristics);

          if (txCharacteristic != null) {
            enableNotification(txCharacteristic);
          } else {
            Log.e(TAG, "TX Characteristic not found.");
//            sendLog("TX Characteristic not found.");
          }

          // ✅ Mark BLE as ready
          isBleReady = (txCharacteristic != null && rxCharacteristics != null);

          // ✅ Retry pending payload if present
          if (isBleReady && pendingBlePayload != null) {
            Log.d(TAG, "BLE is ready. Retrying pending BLE write...");
            writeDataToDevice(rxCharacteristics, pendingBlePayload);
            pendingBlePayload = null; // Clear after sending
          }

        } else {
          Log.e(TAG, "Nordic UART service not found.");
//          sendLog("Nordic UART service not found.");
        }

      } else {
        Log.e(TAG, "Service discovery failed with status: " + status);
//        sendLog("Service discovery failed with status: " + status);
      }
    }


//    @Override
//    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//      if (status == BluetoothGatt.GATT_SUCCESS) {
//        UUID uuid = characteristic.getUuid();
//        byte[] data = characteristic.getValue();
//        Log.d(TAG, "Data read from " + uuid + ": " + new String(data));
//      } else {
//        Log.e(TAG, "Read failed with status: " + status);
//      }
//    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      UUID uuid = characteristic.getUuid();
      byte[] value = characteristic.getValue();

      if (value != null) {
        Log.d(TAG, "Notification received from " + uuid + ": " + new String(value));
//        sendLog("Notification received from " + uuid + ": " + new String(value));

        try {
          String jsonString = new String(value, StandardCharsets.UTF_8);
          Log.d(TAG, "Received Notification JSON: " + jsonString);
          System.out.println("Received Notification JSON: " + jsonString);
//          sendLog("Received Notification JSON: " + jsonString);
          Object mqttUplinkCallBack = null;
          publishMessage(Constants.PUBLISH_TO_TOPIC,jsonString, (MqttUplinkCallBack) mqttUplinkCallBack);

        } catch (Exception e) {
          Log.e("BLE", "Failed to parse JSON: " + e.getMessage());
        }
      }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        System.out.println("Auth DL Successfully written to device");
//        sendLog("Auth DL Successfully written to device");

      } else {
        System.out.println("Failed to write data to device");
//        sendLog("Failed to write data to device");
//        Log.e(TAG, "Chunk write failed at index " + currentChunkIndex);
      }
    }
  };

  private void writeDataToDevice(BluetoothGattCharacteristic characteristic, String authDlData) {

    BluetoothGatt bluetoothGatt = BleClientHolder.getGatt();

    if (bluetoothGatt == null) {
      Log.e(TAG, "BluetoothGatt not initialized");
      return;
    }

    if (characteristic == null) {
      Log.e(TAG, "Characteristic is null — cannot write.");
      return;
    }

    byte[] payload = authDlData.getBytes(StandardCharsets.UTF_8);
//    Log.d(TAG, "Payload to write to device is: " + Arrays.toString(payload));

    characteristic.setValue(payload);
    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    boolean success = bluetoothGatt.writeCharacteristic(characteristic);
    Log.d(TAG, "Write triggered: " + (success ? "success" : "failed"));
  }

  private void enableNotification(BluetoothGattCharacteristic characteristic) {
    if (bluetoothGatt == null) {
      Log.e(TAG, "BluetoothGatt not initialized");
//      sendLog("BluetoothGatt not initialized");
      return;
    }

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
      return;
    }

    bluetoothGatt.setCharacteristicNotification(characteristic, true);

    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
    if (descriptor != null) {
      descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
      bluetoothGatt.writeDescriptor(descriptor);
      Log.d(TAG, "Notification enabled for characteristic: " + characteristic.getUuid());
      System.out.println("Notification enable for characteristic: "+characteristic.getUuid());
//      sendLog("Notification enabled for characteristic: " + characteristic.getUuid());

    } else {
      Log.e(TAG, "Descriptor not found for characteristic: " + characteristic.getUuid());
//      sendLog("Descriptor not found for characteristic: " + characteristic.getUuid());
    }
  }

  private void startPeriodicRead() {
    handler.postDelayed(readRunnable, READ_INTERVAL);
    Log.d(TAG, "Started periodic read every " + READ_INTERVAL / 1000 + " seconds.");
  }

  private void stopPeriodicRead() {
    handler.removeCallbacks(readRunnable);
    Log.d(TAG, "Stopped periodic read.");
  }

  private final Runnable readRunnable = new Runnable() {
    @Override
    public void run() {
      if (txCharacteristic != null && bluetoothGatt != null) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
          boolean readInitiated = bluetoothGatt.readCharacteristic(txCharacteristic);
          Log.d(TAG, "Periodic read initiated: " + readInitiated);
        }
      }
      handler.postDelayed(this, READ_INTERVAL);
    }
  };

  private void startScanningForDevice() {
    if (bluetoothAdapter == null) {
      System.out.println("BluetoothAdapter is null — attempting fallback init");
      Log.w(TAG, "BluetoothAdapter is null — attempting fallback init");
      BluetoothManager bluetoothManager =
        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
      if (bluetoothManager != null) {
        bluetoothAdapter = bluetoothManager.getAdapter();
      }
    }
    if (isScanning.get() || isDeviceConnected) {
      return;
    }

    isScanning.set(true);

    // Ensure Bluetooth adapter and scanner are initialized
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      Log.e(TAG, "Bluetooth is not enabled. Cannot start scanning.");
      sendBluetoothNotification();
      // Register receiver to listen for Bluetooth turning ON
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      getApplicationContext().registerReceiver(bluetoothStateReceiver, filter);

      return;
    }

    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    if (bluetoothLeScanner == null) {
      Log.e(TAG, "BluetoothLeScanner is null. Cannot start scanning.");
      isScanning.set(false);
      return;
    }

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "Bluetooth scan permission not granted.");
      isScanning.set(false);
      return;
    }

    bluetoothLeScanner.startScan(scanCallback);
    Log.d(TAG, "Started scanning for devices.");

    // Stop scan after 15 seconds and restart after 5 seconds
    scanHandler.postDelayed(() -> {
      stopScanning();

      // Restart scanning after 5 seconds
      scanHandler.postDelayed(this::startScanningForDevice, 5000);

    }, 15000); // Scan for 15 seconds
  }

//  private final Runnable scanRunnable = new Runnable() {
//    @Override
//    public void run() {
//
//      if (bluetoothAdapter == null || bluetoothLeScanner == null || !bluetoothAdapter.isEnabled()) {
//        Log.w(TAG, "Bluetooth not ready. Retrying scan loop...");
//
//        sendLog("Bluetooth not ready. Retrying scan loop...");
//        scanHandler.postDelayed(this, 10000); // Retry scan loop in 10 seconds
//        return;
//      }
//
//      if (!isConnectedToDevice()) {
//        Log.d(TAG, "Device not connected. Starting scan...");
//        sendLog("Device not connected. Starting scan...");
//
//        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN)
//          != PackageManager.PERMISSION_GRANTED) {
//          Log.e(TAG, "BLUETOOTH_SCAN permission not granted.");
//          scanHandler.postDelayed(this, 20000); // Retry loop after 20 seconds
//          return;
//        }
//
//        bluetoothLeScanner.startScan(scanCallback);
//
//        // Stop scan after 15 seconds
//        scanHandler.postDelayed(() -> {
//          bluetoothLeScanner.stopScan(scanCallback);
//          Log.d(TAG, "Scan stopped.");
//
//          sendLog("Scan stopped.");
////          intent.putExtra("LOG", "Scan stopped....");
////          intent.putExtra("TAG", TAG);
////          LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
//
//          // Schedule next scan loop in 5 seconds if still disconnected
//          if (!isDeviceConnected) {
//            scanHandler.postDelayed(scanRunnable, 5000);
//          }
//        }, 15000);
//
//      } else {
//        Log.d(TAG, "Device already connected. Skipping scan.");
//        sendLog("Device already connected. Skipping scan.");
////        intent.putExtra("LOG", "Device already connected. Skipping scan.");
////        intent.putExtra("TAG", TAG);
////        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
//        scanHandler.postDelayed(this, 20000); // Check again in 20 seconds
//      }
//    }
//  };

  private boolean isConnectedToDevice() {
    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager != null) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return false;
      }

      List<BluetoothDevice> connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
      for (BluetoothDevice device : connectedDevices) {
        if (device.getAddress().equalsIgnoreCase(macAddress)) {
          isDeviceConnected = true;
          return true;
        }
      }
    }
    isDeviceConnected = false;
    return false;
  }

  /**
   * Stops scanning and ensures the scanner is cleaned up properly.
   */
  private void stopScanning() {
    if (!isScanning.get()) {
      return;
    }

    isScanning.set(false);

    if (bluetoothLeScanner != null) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      bluetoothLeScanner.stopScan(scanCallback);
      Log.d(TAG, "Stopped scanning.");
//      sendLog("Stopped scanning....");
//      Intent intent = new Intent("com.mobicloud.logs");
//      intent.putExtra("LOG", "Stopped scanning....");
//      intent.putExtra("TAG", TAG);
//      LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    } else {
      Log.e(TAG, "Cannot stop scan: BluetoothLeScanner is null.");
    }
  }

  private final ScanCallback scanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      if (result.getDevice().getAddress().equals(macAddress)) {
        Log.d(TAG, "Device found! Reconnecting...");
//        sendLog("Device found! Reconnecting...");
//        Intent intent = new Intent("com.mobicloud.logs");
//        intent.putExtra("LOG", "Device found! Reconnecting...");
//        intent.putExtra("TAG", TAG);
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
          // TODO: Consider calling
          //    ActivityCompat#requestPermissions
          // here to request the missing permissions, and then overriding
          //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
          //                                          int[] grantResults)
          // to handle the case where the user grants the permission. See the documentation
          // for ActivityCompat#requestPermissions for more details.
          return;
        }
        bluetoothLeScanner.stopScan(scanCallback);
        connectToDevice(macAddress);
      }
    }
  };

  private class BluetoothStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
        if (state == BluetoothAdapter.STATE_ON) {
          Log.d(TAG,"Bluetooth Enabled Service active");
          updateNotification("Bluetooth enabled. Service active.");

          setupBluetoothMonitoring();
        } else if (state == BluetoothAdapter.STATE_OFF) {
          sendBluetoothNotification();
        }
      }
    }
  }

  private void updateNotification(String content) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentText(content)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true);

    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }


  public void connectToBroker(String BROKER_URL, String USERNAME, @NonNull String PASSWORD, MqttConnectionCallback callBack)
  {
    String MqttclientId = "smart-gas-regulator";

    MQTT_URL = BROKER_URL;

    MQTT_USERNAME = USERNAME;

    MQTT_Password = PASSWORD;

    this.mqttAndroidClient = new MqttAndroidClient(this.context, BROKER_URL, MqttclientId, Ack.AUTO_ACK,persistence,useReconnect,maxInflight);
    MqttClientHolder.setClient(this.mqttAndroidClient);

    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
    mqttConnectOptions.setAutomaticReconnect(true);
    mqttConnectOptions.setCleanSession(true);
    mqttConnectOptions.setUserName(USERNAME);
    mqttConnectOptions.setPassword(PASSWORD.toCharArray());
    mqttConnectOptions.setKeepAliveInterval(60);

    mqttAndroidClient.connect(mqttConnectOptions, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Connected to broker");
        System.out.println("Connected to MQTT Broker");

//        Intent intent = new Intent("com.mobicloud.logs");
//        intent.putExtra("LOG", "Connected to MQTT Broker");
//        intent.putExtra("TAG", TAG);
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        if (callBack != null) {
          callBack.onConnectionSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to connect to broker", exception);
        System.out.println("Failed to connect to broker: "+exception);

//        sendLog("Failed to connect to broker: "+exception);
//        Intent intent = new Intent("com.mobicloud.logs");
//        intent.putExtra("LOG", "Failed to connect to broker: "+exception);
//        intent.putExtra("TAG", TAG);
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        if (callBack != null) {
          callBack.onConnectionFailure(exception);
        }
      }
    });

    mqttAndroidClient.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection lost", cause);
        System.out.println("Connection Lost : "+cause);

        try
        {
          mqttAndroidClient.reconnect();
        }
        catch (MqttException e)
        {
          System.out.println("Exception Occurred While Reconnecting to MQTT Broker");

          throw new RuntimeException(e);

        }
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) throws JSONException {
        Log.d(TAG, "Message received from topic: " + topic + " - " + new String(message.getPayload()));
        System.out.println("Message received from topic: " + topic + " - " + new String(message.getPayload()));

        String msg = new String(message.getPayload());

        try {
          // Parse it as JSON
          JSONObject json = new JSONObject(msg);

          // Extract required fields
          String regulatorId = json.getString("Regulatorid ");
          String data = json.getString("Data");
          String sha = json.getString("SHA");

          // Build the final string
          String formatted = String.format(
            "{\"Regulatorid \": \"%s\",\"Data\" : \"%s\",\"SHA\" : \"%s\"}",
            regulatorId, data, sha
          );

//          Log.d(TAG, "Formatted payload: " + formatted);

          BluetoothGatt gatt = BleClientHolder.getGatt();
          BluetoothGattCharacteristic characteristic = BleClientHolder.getRxCharacteristic();

          if (gatt == null || characteristic == null) {
            Log.w(TAG, "BLE not ready. Storing message for retry.");
            pendingBlePayload = formatted;
            return;
          }

          writeDataToDevice(characteristic, formatted);
        } catch (Exception e) {
          Log.e(TAG, "Exception occurred while processing MQTT message", e);
        }

        // 📨 If needed, broadcast message to the plugin (currently commented)
        /*
        Intent intent = new Intent("com.mobicloud.MQTT_MESSAGE");
        intent.putExtra("message", msg);
        intent.putExtra("topic", topic);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        */
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Message delivery complete");
        System.out.println("Message Delivery Complete");
//        sendLog("Message Delivery Complete");
      }
    });
  }

  public void subscribeToTopic(String topicToSubscribe, MqttDownlinkCallBack mqttDownlinkCallBack) {
    if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
      Log.e(TAG, "MQTT client is not connected or initialized.");
//      sendLog("MQTT client is not connected or initialized.");
      return;
    }

    if (topicToSubscribe == null || topicToSubscribe.isEmpty()) {
      return;
    }

    mqttAndroidClient.subscribe(topicToSubscribe, 1, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Subscribed to topic: " + topicToSubscribe);
        System.out.println("Subscribed to topic: " + topicToSubscribe);

//        sendLog("Subscribed to topic: " + topicToSubscribe);
//        Intent intent = new Intent("com.mobicloud.logs");
//        intent.putExtra("LOG", "Subscribed to topic: "+topicToSubscribe);
//        intent.putExtra("TAG", TAG);
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to subscribe to topic: " + topicToSubscribe, exception);
//        sendLog("Failed to subscribe to topic: " + topicToSubscribe + exception);
//        Intent intent = new Intent("com.mobicloud.logs");
//        intent.putExtra("LOG", "Failed to subscribe to topic: "+topicToSubscribe);
//        intent.putExtra("TAG", TAG);
//        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onFailure(exception);
        }
      }
    });
  }

  public void subscribeToAuthTopic(String topicToSubscribe, MqttDownlinkCallBack mqttDownlinkCallBack) {
    if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
      Log.e(TAG, "MQTT client is not connected or initialized.");
      return;
    }

    if (topicToSubscribe == null || topicToSubscribe.isEmpty()) {
      return;
    }

    mqttAndroidClient.subscribe(topicToSubscribe, 1, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Subscribed to topic: " + topicToSubscribe);
        System.out.println("Subscribed to topic: " + topicToSubscribe);

        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to subscribe to topic: " + topicToSubscribe, exception);
        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onFailure(exception);
        }
      }
    });
  }

  private void publishMessageTODevice(String uplinkTopic,String messageToUplink) {

    if (uplinkTopic == null || uplinkTopic.isEmpty()) {
      return;
    }

    if (messageToUplink == null) {
      Log.e(TAG,"Empty Payload message cannot publish");
//      sendLog("Empty Payload message cannot publish");
      return;
    }

    try {
      // Check if the MQTT client is initialized and connected
      Log.d(TAG,"MQTT android client is: "+mqttAndroidClient);

//      connectToMQTT(MQTT_URL,MQTT_USERNAME,MQTT_Password);

      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {

        Log.e(TAG, "MQTT client is not connected.");
        System.out.println("MQTT Client is not Connected");
//        sendLog("MQTT Client is not Connected");

        return;
      }

      // Create and configure MQTT message
      MqttMessage mqttMessage = new MqttMessage(messageToUplink.getBytes(StandardCharsets.UTF_8));
      mqttMessage.setQos(1); // QoS level (0, 1, or 2)

      // Publish the message
      mqttAndroidClient.publish(uplinkTopic, mqttMessage,getContext(),new IMqttActionListener(){

        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG, "Message published to topic: " + uplinkTopic + " with payload: " + messageToUplink);

          System.out.println("Message successfully published: " + messageToUplink);
//          sendLog("Message successfully published: " + messageToUplink);

          // Send success response to the JS side
          JSObject result = new JSObject();
          result.put("status", "Message published successfully");
          result.put("topic", messageToUplink);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.d(TAG, "Failed to publish to topic: " + uplinkTopic + " with payload: " + messageToUplink);
          System.out.println("Failed to publish to topic: " + uplinkTopic + " with payload: " + messageToUplink);

//          sendLog("Failed to publish to topic: " + uplinkTopic + " with payload: " + messageToUplink);
        }
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void publishMessage(String topicToPublish, String messageToPublish, MqttUplinkCallBack mqttUplinkCallBack) {
    TOPIC_to_Publish = topicToPublish;

    if (topicToPublish == null || topicToPublish.isEmpty()) return;
    if (messageToPublish == null) {
      Log.e(TAG, "Empty Payload message cannot publish");
      return;
    }

    try {
      mqttAndroidClient = MqttClientHolder.getClient();

      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
        Log.e(TAG, "MQTT client is null or disconnected. Attempting to reconnect...");

        if (MQTT_URL == null || MQTT_USERNAME == null || MQTT_Password == null) {
          Log.e(TAG, "MQTT credentials missing. Cannot reconnect.");
          if (mqttUplinkCallBack != null) mqttUplinkCallBack.onFailure(new Exception("MQTT client not connected and credentials missing"));
          return;
        }

        // Reinitialize
        mqttAndroidClient = new MqttAndroidClient(this.context, MQTT_URL, "smart-gas-regulator", Ack.AUTO_ACK,persistence,useReconnect,maxInflight);
        MqttClientHolder.setClient(mqttAndroidClient);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(60);
        options.setUserName(MQTT_USERNAME);
        options.setPassword(MQTT_Password.toCharArray());

        mqttAndroidClient.connect(options, null, new IMqttActionListener() {
          @Override
          public void onSuccess(IMqttToken asyncActionToken) {
            Log.d(TAG, "MQTT reconnected successfully.");
            publishAfterReconnect(topicToPublish, messageToPublish, mqttUplinkCallBack);
          }

          @Override
          public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            Log.e(TAG, "MQTT reconnection failed", exception);
            if (mqttUplinkCallBack != null) mqttUplinkCallBack.onFailure(exception);
          }
        });

      } else {
        // Already connected, just publish
        publishNow(topicToPublish, messageToPublish, mqttUplinkCallBack);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to publish MQTT message", e);
      if (mqttUplinkCallBack != null) mqttUplinkCallBack.onFailure(e);
    }
  }

  private void publishAfterReconnect(String topic, String message, MqttUplinkCallBack callback) {
    try {
      MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
      mqttMessage.setQos(1);

      mqttAndroidClient.publish(topic, mqttMessage, null, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG, "Message published after reconnect");
          if (callback != null) callback.onSuccess();
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.e(TAG, "Publish failed after reconnect", exception);
          if (callback != null) callback.onFailure(exception);
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Error while publishing after reconnect", e);
      if (callback != null) callback.onFailure(e);
    }
  }

  private void publishNow(String topic, String message, MqttUplinkCallBack callback) {
    try {
      MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
      mqttMessage.setQos(1);

      mqttAndroidClient.publish(topic, mqttMessage, null, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG, "Message published immediately");
          if (callback != null) callback.onSuccess();
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.e(TAG, "Immediate publish failed", exception);
          if (callback != null) callback.onFailure(exception);
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Error during immediate publish", e);
      if (callback != null) callback.onFailure(e);
    }
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "Service destroyed");
//    sendLog("Service destroyed");
    macAddress = null;
//    stopPeriodicRead();
    if (bluetoothStateReceiver != null) {
      unregisterReceiver(bluetoothStateReceiver);
    }

    if (mqttAndroidClient != null) {
      mqttAndroidClient.disconnect();
    }

    if (bluetoothGatt != null) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      bluetoothGatt.close();
    }
  }
}
