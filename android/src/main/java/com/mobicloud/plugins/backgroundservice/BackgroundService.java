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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;


public class BackgroundService extends Service {

  public static BackgroundService instance;

  private static final String TAG = "BackgroundService";
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;

  private boolean authSentAfterReconnect = false;
  private BluetoothAdapter bluetoothAdapter;
  private BluetoothLeScanner bluetoothLeScanner;
  private BluetoothStateReceiver bluetoothStateReceiver;
  private String macAddress;
  private BluetoothGatt bluetoothGatt;
  private volatile boolean isBleReady = false;
  private String pendingBlePayload = null;

  private static String MQTT_URL = "";

  private boolean isMqttCallbackSet = false;

  private boolean isBLEMonitoringInitialized = false;

  private boolean isConnecting = false;

  private static String MQTT_USERNAME = "";

  private static String MQTT_Password = "";

  private boolean isDeviceConnected = false;

  private boolean isAuthDlWrittenToDevice = false;

  private final AtomicBoolean isScanning = new AtomicBoolean(false);
  private final Handler scanHandler = new Handler();

  public static Queue<String> normalDlQueue = new ConcurrentLinkedQueue<>();
  private String lastProcessedPayload = null;

  private Runnable waitForAuthNotificationRunnable;

  private Runnable waitForDlNotificationRunnable;

  private boolean notificationReceived = false;

  // Define persistence
  MqttClientPersistence persistence = new MemoryPersistence();

  // Enable reconnect
  boolean useReconnect = true;

  // Define max inflight messages
  int maxInflight = 10;

  private static String DEVICE_TYPE = "";

  private static String TOPIC_to_Publish = " ";

  private static String TOPIC_to_Subscribe= " ";
  private static String BASE_URL = "";
  private static String BASIC_AUTH = "";
  private static String DEVICE_UUID = "";
  private static String API_SUFFIX = "";
  private static String API_PAYLOAD = "";

  private static boolean isInternetAvailable = false;
  private Context context;

  private static Context appContext;
  private static String authDlData = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Long lastAuthDlTime = null; // set this when MQTT DL arrives

  private Runnable waitAfterUlRunnable;


  private MqttAndroidClient mqttAndroidClient;

  private BluetoothGattCharacteristic txCharacteristic;

  private BluetoothGattCharacteristic rxCharacteristics;

  private final long READ_INTERVAL = 5000;  // 5 seconds

  private SharedPreferences sharedPref;

  private final Runnable scanLoopRunnable = new Runnable() {
    @Override
    public void run() {
      if (!isDeviceConnected && !isScanning.get()) {
        startScanningForDevice();
      } else {
        Log.d(TAG, "Skipping scan loop — either connected or already scanning.");
      }
    }
  };

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

    BackgroundService.appContext = getApplicationContext();
//    sendNotification("Service created");
    instance = this;
    createNotificationChannel();
    startForegroundServiceCompat();
    registerNetworkCallback();

    sharedPref = getApplicationContext().getSharedPreferences("SmartRegulatorPrefs", Context.MODE_PRIVATE);
  }

  public static Context getAppContext() {
    return appContext;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "Service started");
    sharedPref = getApplicationContext().getSharedPreferences("SmartRegulatorPrefs", Context.MODE_PRIVATE);

//    sendNotification("Service started");
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

  private void registerNetworkCallback() {
    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

    if (connectivityManager != null) {
      NetworkRequest request = new NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build();

      connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
          isInternetAvailable = true;
          Log.d(TAG, "Internet is available again!");
          sendLog("Internet is available again!");
          mqttAndroidClient = MqttClientHolder.getClient();

          if(mqttAndroidClient != null) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setUserName(MQTT_USERNAME);
            options.setPassword(MQTT_Password.toCharArray());

            mqttAndroidClient.connect(options, getContext(), new IMqttActionListener() {
              @Override
              public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Connected to broker");
                sendLog("Connected to broker");

                subscribeToTopic(TOPIC_to_Subscribe,null);

                // Also push stored BLE messages
                Set<String> stored = getStoredNotifications();
                for (String msg : stored) {
                  publishNow(Constants.PUBLISH_TO_TOPIC, msg, null);
                }
                clearStoredNotifications();
              }

              @Override
              public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Failed to connect to broker", exception);
                sendLog("Failed to connect to broker"+ exception);
              }
            });
          }
        }
        @Override
        public void onLost(@NonNull Network network) {
          Log.d(TAG, "Internet connection lost.");
          Toast.makeText(getBaseContext(),"Internet connection is required",Toast.LENGTH_LONG).show();
          sendLog("Internet connection lost.");
          isInternetAvailable = false;
        }
      });
    }
  }


  private void sendNotification(String message) {
//    Log.d(TAG, message);
//    System.out.println(message);

    Intent intent = new Intent("com.mobicloud.notifcationLogs");
    intent.putExtra("notification", message);

    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  private void sendLog(String message) {
    Intent intent = new Intent("com.mobicloud.logs");
    intent.putExtra("LOG",message);
    intent.putExtra("TAG", TAG);

    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  private void sendMqttPublishStatus(String message) {
    Intent intent = new Intent("com.mobicloud.MQTT_MESSAGE");
    intent.putExtra("mqttPublishStatus", message);

    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  private final Runnable authDlCheckRunnable = new Runnable() {
    @Override
    public void run() {
      long currentTimeSec = System.currentTimeMillis() / 1000;
      long ttlRemaining = (lastAuthDlTime == null) ? 0 : (3600 - (currentTimeSec - lastAuthDlTime));

      if (ttlRemaining == 0) {
        SharedPreferences.Editor editor = sharedPref.edit();
        isAuthDlWrittenToDevice = false;
        authSentAfterReconnect = false;
        editor.remove("authDlData");

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
//        Log.d(TAG,"TTL valid, Auth DL not required. Time left: " + ttlRemaining + "s");

//        sendNotification("TTL valid, Auth DL not required. Time left: " + ttlRemaining + "s");
      }

      handler.postDelayed(authDlCheckRunnable, 1000); // check again after 1 sec
    }
  };

  private void sendHttpRequest(String urlString, String jsonPayload, String authHeader) {
    System.out.println(urlString);
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.remove("authDlData");
    editor.apply(); // or editor.commit();

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
        String packetType = resultObject.getString("packetType");
        String regulatorId = resultObject.getString("Regulatorid ");
        String data = resultObject.getString("Data");
        String sha = resultObject.getString("SHA");

//        authDlData = String.format(
//          "{\"Regulatorid \": \"%s\",\n\"Data\" : \"%s\",\n\"SHA\" : \"%s\"}",
//          regulatorId, data, sha
//        );
        // Build the string same as JavaScript
        authDlData = String.format("{\"Regulatorid \": \"%s\",\"Data\" : \"%s\",\"SHA\" : \"%s\"}",regulatorId, data, sha);

        editor.putString("authDlData", authDlData);
        editor.apply(); // or editor.commit();

        // Log or use the string
        Log.d(TAG,"Auth DL Data is: "+authDlData);
        System.out.println("authDLdata: " + authDlData);
//            sendNotification("Auth DL Data to write to device is: "+authDlData);
        byte[] payload = authDlData.getBytes(StandardCharsets.UTF_8);

//        Log.d(TAG,"Payload to write to device is: "+authDlData);
//        writeDataToDevice(rxCharacteristics,authDlData);

      } catch (Exception e) {
        Log.e("BackgroundService", "HTTP request failed", e);
        System.out.println("Error Occured While Performing HTTP Request: "+e);
//            sendNotification("Error Occured While Performing HTTP Request: "+e);
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

    if(isBLEMonitoringInitialized == true) {
      Log.d(TAG,"BLE Montoring is already intialized");
      connectToDevice(macAddress);
      return;
    }

    BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);

//    sendNotification("Inside setupBluetoothMonitoring");

    bluetoothStateReceiver = new BluetoothStateReceiver();
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(bluetoothStateReceiver, filter);

    if (bluetoothManager == null) {
      Log.e(TAG, "BluetoothManager is null");
      System.out.println("BluetoothManager is null");

//      sendNotification("BluetoothManager is null");
      return;
    }

    bluetoothAdapter = bluetoothManager.getAdapter();

    if (bluetoothAdapter == null) {
      Log.e(TAG, "BluetoothAdapter is null");
      System.out.println("BluetoothAdapter is null");

//      sendNotification("BluetoothAdapter is null");

      return;
    }

    if (!bluetoothAdapter.isEnabled()) {
      Log.e(TAG, "Bluetooth is off");
      System.out.println("BluetoothManage is OFF");

//      sendNotification("Bluetooth is off");

      return;
    }

    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    if (bluetoothLeScanner == null) {
      Log.e(TAG, "BluetoothLeScanner is null");
      System.out.println("BluetoothLE Scanner is null");

//      sendNotification("BluetoothLE Scanner is null");

    }

    Log.d(TAG, "Bluetooth monitoring initialized.");
    System.out.println("Bluetooth monitoring initialized");

    isBLEMonitoringInitialized = true;
//    sendNotification("Bluetooth monitoring initialized");
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
    Log.d(TAG, "connectToDevice() called with address: " + address);
    macAddress = address;

    if (macAddress == null || macAddress.isEmpty()) {
      Log.e(TAG, "Invalid MAC address.");
      return;
    }

    try {
      if (bluetoothAdapter == null) {
        Log.w(TAG, "BluetoothAdapter is null — attempting fallback init");
        BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
          bluetoothAdapter = bluetoothManager.getAdapter();
        }
      }

      if (bluetoothAdapter == null) {
        Log.e(TAG, "Failed to get BluetoothAdapter.");
        return;
      }

      if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
        return;
      }

      // Find bonded device if available
      BluetoothDevice bondedDevice = null;
      Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
      for (BluetoothDevice device : bondedDevices) {
        if (device.getAddress().equalsIgnoreCase(macAddress)) {
          bondedDevice = device;
          break;
        }
      }

      BluetoothDevice deviceToConnect = (bondedDevice != null)
        ? bondedDevice
        : bluetoothAdapter.getRemoteDevice(macAddress);

      if (bondedDevice != null) {
        Log.d(TAG, "Bonded device found. Reusing it.");
        sendLog("Bonded device found. Reusing it.");
      } else {
        Log.w(TAG, "Device not bonded. Using getRemoteDevice.");
      }

      int bondState = deviceToConnect.getBondState();
      Log.d(TAG, "Bond state: " + bondState);

      // If not bonded, initiate bonding and return
      if (bondState != BluetoothDevice.BOND_BONDED) {
        Log.i(TAG, "Device not bonded. Trying to create bond.");
        deviceToConnect.createBond();
        return;
      }

      // Prevent multiple connection attempts
      if (isConnecting || isDeviceConnected) {
        Log.d(TAG, "Already connecting or connected, skipping new GATT connection.");
        return;
      }

      bluetoothGatt = deviceToConnect.connectGatt(this, true, gattCallback);
      isConnecting = true;
      Log.d(TAG, "Attempting to connect to BLE device: " + macAddress);
      sendLog("Attempting to connect to BLE device: " + macAddress);

    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Invalid MAC format: " + e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "Exception during BLE connect: " + e);
      isConnecting = false;

      // Only restart scan if device isn't connected already
      if (!isDeviceConnected) {
        scanHandler.postDelayed(scanLoopRunnable, 5000);
      }
    }
  }


  public void saveNotification(String message) {
    SharedPreferences prefs = getAppContext().getSharedPreferences("ble_notifications", MODE_PRIVATE);
    Set<String> set = new HashSet<>(prefs.getStringSet("pending_messages", new HashSet<>()));
    set.add(message);  // Add new message
    prefs.edit().putStringSet("pending_messages", set).apply();
  }

  public Set<String> getStoredNotifications() {
    SharedPreferences prefs = getAppContext().getSharedPreferences("ble_notifications", MODE_PRIVATE);
    return new HashSet<>(prefs.getStringSet("pending_messages", new HashSet<>()));
  }

  public void clearStoredNotifications() {
    SharedPreferences prefs = getAppContext().getSharedPreferences("ble_notifications", MODE_PRIVATE);
    prefs.edit().remove("pending_messages").apply();
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      Log.d(TAG, "onConnectionStateChange: state=" + newState + ", status=" + status + ", thread=" + Thread.currentThread().getName());

      if (newState == BluetoothGatt.STATE_CONNECTED) {
        if (isDeviceConnected) {
          Log.w(TAG, "Already marked connected. Ignoring duplicate CONNECTED callback.");
          return;
        }

        Log.d(TAG, "Connected to BLE device. GATT: " + gatt);
        sendLog("Connected to BLE device");

        isDeviceConnected = true;
        isConnecting = false;

        scanHandler.removeCallbacks(scanLoopRunnable);
        stopScanning();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)
          != PackageManager.PERMISSION_GRANTED) {
          Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
          return;
        }

        gatt.discoverServices();

      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        Log.d(TAG, "Disconnected from BLE device.");
        sendLog("Disconnected from BLE device");

        txCharacteristic = null;
        isAuthDlWrittenToDevice = false;
        isDeviceConnected = false;
        isConnecting = false;

        if (bluetoothGatt != null) {
          bluetoothGatt.close();
          bluetoothGatt = null;
        }

        if (!isScanning.get()) {
          scanHandler.postDelayed(scanLoopRunnable, 3000);
        }
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
//            sendNotification("TX Characteristic not found.");
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
//          sendNotification("Nordic UART service not found.");
        }

      } else {
        Log.e(TAG, "Service discovery failed with status: " + status);
//        sendNotification("Service discovery failed with status: " + status);
      }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      byte[] value = characteristic.getValue();
      if (value == null) return;

      String jsonString = new String(value, StandardCharsets.UTF_8);

      // Sanitize the JSON if needed (already done in your code)

      // Ensure starts with {
      if (!jsonString.startsWith("{")) {
        jsonString = "{" + jsonString;
      }

      // Balance quotes
      long quoteCount = jsonString.chars().filter(ch -> ch == '"').count();
      if (quoteCount % 2 != 0) {
        jsonString += "\"";
      }

      // Balance brackets: add missing closing braces
      long openCount = jsonString.chars().filter(ch -> ch == '{').count();
      long closeCount = jsonString.chars().filter(ch -> ch == '}').count();
      while (closeCount < openCount) {
        jsonString += "}";
        closeCount++;
      }

      Log.d(TAG, "Notification received: " + jsonString);
      sendNotification("Notification received from device: " + jsonString);

      try {
        JSONObject json = new JSONObject(jsonString);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        json.put("timestamp", timestamp);

        if(isInternetAvailable)
        {
          publishMessage(Constants.PUBLISH_TO_TOPIC, json.toString(), null);
        }
        else
        {
          sendLog("Internet is not available notification is save and will be published once internet is available");
          saveNotification(json.toString());
        }
      } catch (Exception e) {
        Log.e(TAG, "Failed to process MQTT payload", e);
      }

      notificationReceived = true;

      // Cancel any previously queued post-notification DL send
      if (waitAfterUlRunnable != null) {
        handler.removeCallbacks(waitAfterUlRunnable);
      }

      // ✅ Wait 5 seconds after notification, then send next normal DL
      waitAfterUlRunnable = () -> {
        Log.d(TAG, "5 seconds passed after notification. Sending next normal DL.");
        sendLog("5 seconds passed after notification. Sending next normal DL.");
        sendNextNormalDl();
      };

      handler.postDelayed(waitAfterUlRunnable, 5000);
    }

//    private void startWaitForNextDl() {
//      if (waitAfterUlRunnable != null) handler.removeCallbacks(waitAfterUlRunnable);
//
//      waitAfterUlRunnable = () -> {
//        Log.d(TAG, "1.5s passed after UL, sending next normal DL.");
//        sendNextNormalDl();
//      };
//
//      handler.postDelayed(waitAfterUlRunnable, 1500);
//    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
//        System.out.println("Auth DL Successfully written to device");

        authSentAfterReconnect = true;

//        sendNotification("Auth DL Successfully written to device");

      } else {
        System.out.println("Failed to write data to device");
        sendLog("Failed to write data to device");
        Log.e(TAG, "Failed to write data to device");
      }
    }
  };

  private boolean writeDataToDevice(BluetoothGattCharacteristic characteristic, String authDlData) {
    Log.d(TAG,"Inside write to Device Function...!!");
    Context context = BackgroundService.getAppContext();
    if (context == null) {
      Log.e(TAG, "Context is null");
      return false;
    }

    BluetoothGatt bluetoothGatt = BleClientHolder.getGatt();

    if (bluetoothGatt == null) {
      Log.e(TAG, "BluetoothGatt not initialized");
      return false;
    } else {
      Log.d(TAG,"BlutoothGatt is not null"+bluetoothGatt);
    }

    if (characteristic == null) {
      Log.e(TAG, "Characteristic is null — cannot write.");
      return false;
    } else {
      Log.d(TAG,"Characteristic is not null"+characteristic);
    }

    byte[] payload = authDlData.getBytes(StandardCharsets.UTF_8);
    Log.d(TAG, "Payload to write to device is: " + Arrays.toString(payload));

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
      return false;
    }

    try {
      boolean success = bluetoothGatt.writeCharacteristic(characteristic);
      Log.d(TAG, "Write triggered: " + (success ? "success" : "failed"));
//      sendLog("Write triggered: " + (success ? "success" : "failed"));
      return success;
    } catch (IllegalArgumentException e) {
      Log.e(TAG,"Exception occured while writing data to device"+e);
//      sendLog("Exception occured while writing data to device"+e);
      return  false;
    }
  }

  private void enableNotification(BluetoothGattCharacteristic characteristic) {
    if (bluetoothGatt == null) {
      Log.e(TAG, "BluetoothGatt not initialized");
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
    } else {
      Log.e(TAG, "Descriptor not found for characteristic: " + characteristic.getUuid());
    }

    // Step 2: Send stored Auth DL
    String authDlData = sharedPref.getString("authDlData", "default_name");

    handler.postDelayed(() -> {
      if (!isAuthDlWrittenToDevice && !"default_name".equals(authDlData)) {
        Log.d(TAG, "Shared AUTH DL Data is: " + authDlData);
        Boolean bret = writeDataToDevice(rxCharacteristics, authDlData);

        if (bret == true) {
          Log.d(TAG, "Auth DL Successfully Written to device after reconnection waiting for notifications");
          sendLog("Auth DL Successfully Written to device after reconnection waiting for notifications");
          isAuthDlWrittenToDevice = true;

          authSentAfterReconnect = true;
        } else {
          Log.d(TAG, "Unable to write Auth DL to device after reconnection");
          sendLog("Unable to write Auth DL to device after reconnection");
          isAuthDlWrittenToDevice = false;

          authSentAfterReconnect = false;
          return;
        }

        notificationReceived = false;
      }
    }, 1500); // 300ms delay to let BLE stack stabilize
  }

  private void sendNextNormalDl() {
    if (!authSentAfterReconnect) {
      Log.d(TAG, "Auth DL not yet sent. Skipping normal DL.");
      return;
    }

    if (normalDlQueue.isEmpty()) {
      Log.d(TAG, "Normal DL queue is empty.");
      sendLog("Normal DL queue is empty.");
      return;
    }

    BluetoothGatt gatt = BleClientHolder.getGatt();
    BluetoothGattCharacteristic characteristic = BleClientHolder.getRxCharacteristic();

    String nextDl = normalDlQueue.poll();

    if (gatt == null || characteristic == null) {
      Log.w(TAG, "BLE not ready. Re-queuing normal DL.");
      normalDlQueue.add(nextDl);
      return;
    }

    Log.d(TAG, "Sending Normal DL: " + nextDl);
    sendLog("Sending Normal DL: " + nextDl);
    boolean bret = writeDataToDevice(characteristic, nextDl);

    if (bret) {
      Log.d(TAG, "Normal DL written. Waiting 5s for notification...");
      sendLog("Normal DL written. Waiting 5s for notification...");
      notificationReceived = false;

      if (waitForDlNotificationRunnable != null) {
        handler.removeCallbacks(waitForDlNotificationRunnable);
      }

      // 🕒 Start 5s wait to *see* if notification comes
      waitForDlNotificationRunnable = () -> {
        if (notificationReceived) {
          // 🕒 Notification came within 5s → wait 5 more seconds before next DL
          Log.d(TAG, "Notification received within 5s. Waiting 5 more sec...");
          sendLog("Notification received within 5s. Waiting 5 more sec...");
          handler.postDelayed(() -> {
            sendNextNormalDl();
          }, 5000);
        } else {
          Log.d(TAG, "No notification within 5s. Not sending next normal DL.");
          sendLog("No notification within 5s. Not sending next normal DL.");
          // Don't send anything — just stop
        }
      };

      handler.postDelayed(waitForDlNotificationRunnable, 5000);

    } else {
      Log.d(TAG, "Failed to write normal DL. Re-queuing...");
      sendLog("Failed to write normal DL. Re-queuing...");
      normalDlQueue.add(nextDl);
    }
  }

  private void startScanningForDevice() {
    if (isScanning.get() || isDeviceConnected) {
      Log.d(TAG, "Scan already running or device connected — skipping start.");
      return;
    }

    if (bluetoothAdapter == null) {
      Log.w(TAG, "BluetoothAdapter is null — attempting fallback init");
      BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
      if (bluetoothManager != null) {
        bluetoothAdapter = bluetoothManager.getAdapter();
      }
    }

    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      Log.e(TAG, "Bluetooth is not enabled. Cannot start scanning.");
      sendLog("Bluetooth is not enabled. Cannot start scanning.");
      sendBluetoothNotification();

      // Register receiver to listen for Bluetooth turning ON
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      getApplicationContext().registerReceiver(bluetoothStateReceiver, filter);
      return;
    }

    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    if (bluetoothLeScanner == null) {
      Log.e(TAG, "BluetoothLeScanner is null. Cannot start scanning.");
      return;
    }

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN)
      != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "Bluetooth scan permission not granted.");
      return;
    }

    isScanning.set(true);
    bluetoothLeScanner.startScan(scanCallback);
    Log.d(TAG, "Started scanning for devices.");
    sendLog("Started scanning for devices.");

    // Stop scan after 15 seconds and schedule next loop if not connected
    scanHandler.postDelayed(() -> {
      stopScanning();
      isScanning.set(false);

      if (!isDeviceConnected) {
        Log.d(TAG, "Scan stopped, scheduling next scan in 5 seconds...");
        scanHandler.postDelayed(scanLoopRunnable, 5000);
      } else {
        Log.d(TAG, "Device is connected, stopping scan loop.");
      }

    }, 15000);
  }

  /**
   * Stops scanning and ensures the scanner is cleaned up properly.
   */
  private void stopScanning() {
    if (!isScanning.get()) {
      return;
    }

    isScanning.set(false);

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      Log.e(TAG, "Cannot stop scan: Bluetooth Adapter is OFF or not available.");
      return;
    }

    if (bluetoothLeScanner != null) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        != PackageManager.PERMISSION_GRANTED) {
        // Handle permission request if needed
        return;
      }

      try {
        bluetoothLeScanner.stopScan(scanCallback);
        Log.d(TAG, "Stopped scanning.");
        sendLog("Stopped scanning.");

      } catch (IllegalStateException e) {
        Log.e(TAG, "Failed to stop scan: " + e.getMessage(), e);
      }
    } else {
      Log.e(TAG, "Cannot stop scan: BluetoothLeScanner is null.");
    }
  }

  private final ScanCallback scanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      if (result.getDevice().getAddress().equals(macAddress)) {
        Log.d(TAG, "Device found! Reconnecting...");
        sendLog("Device found! Reconnecting...");
//        sendNotification("Device found! Reconnecting...");
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
        stopScanning();
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
          isBLEMonitoringInitialized = false;
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

  public void connectToBroker(String BROKER_URL, String USERNAME, @NonNull String PASSWORD, MqttConnectionCallback callBack) {
    MQTT_URL = BROKER_URL;
    MQTT_USERNAME = USERNAME;
    MQTT_Password = PASSWORD;

    SharedPreferences prefs = getAppContext().getSharedPreferences("mqtt_prefs", Context.MODE_PRIVATE);
    String clientId = prefs.getString("mqtt_client_id", null);

    if (clientId == null) {
      clientId = "client_" + UUID.randomUUID().toString();
      prefs.edit().putString("mqtt_client_id", clientId).apply();
    }

    if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
      Log.d(TAG, "MQTT already connected. Skipping reconnect.");
      if (callBack != null) {
        callBack.onConnectionSuccess();
      }
      return;
    }

    mqttAndroidClient = new MqttAndroidClient(getAppContext(), MQTT_URL, clientId, Ack.AUTO_ACK,null,false,1000);
    MqttClientHolder.setClient(mqttAndroidClient);

    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(true);
    options.setCleanSession(true);
    options.setKeepAliveInterval(60);
    options.setUserName(MQTT_USERNAME);
    options.setPassword(MQTT_Password.toCharArray());

    mqttAndroidClient.connect(options, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Connected to broker");
        sendLog("Connected to broker");

        subscribeToTopic(TOPIC_to_Subscribe,null);

        // Also push stored BLE messages
        Set<String> stored = getStoredNotifications();
        for (String msg : stored) {
          publishNow(Constants.PUBLISH_TO_TOPIC, msg, null);
        }
        clearStoredNotifications();

        if (callBack != null) {
          callBack.onConnectionSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to connect to broker", exception);
        sendLog("Failed to connect to broker"+ exception);
        if (callBack != null) {
          callBack.onConnectionFailure(exception);
        }
      }
    });

    // ✅ Set callback only once
    if (!isMqttCallbackSet) {
      mqttAndroidClient.setCallback(new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
          Log.e(TAG, "Connection lost", cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws JSONException {
          String msg = new String(message.getPayload());
          Log.d(TAG, "MQTT messageArrived hash=" + message.hashCode() + ", topic=" + topic);
          Log.d(TAG, "Payload: " + msg);

          sendLog("MQTT messageArrived"+msg);

//           🛡️ Deduplication: Skip if same as last processed
          if (msg.equals(lastProcessedPayload)) {
            Log.w(TAG, "Duplicate MQTT message detected — skipping.");
            return;
          }
          lastProcessedPayload = msg;

          try {
            JSONObject json = new JSONObject(msg);
            String packetType = json.getString("packetType");
            Log.d(TAG,"Packet Type: "+packetType);

            String regulatorId = json.getString("Regulatorid ");
            String data = json.getString("Data");
            String sha = json.getString("SHA");

//            String formatted = String.format(
//              "{\"Regulatorid \": \"%s\",\n\"Data\" : \"%s\",\n\"SHA\" : \"%s\"}",
//              regulatorId, data, sha
//            );

            String formatted = String.format(
              "{\"Regulatorid \": \"%s\",\"Data\" : \"%s\",\"SHA\" : \"%s\"}",
              regulatorId, data, sha
            );

            BluetoothGatt gatt = BleClientHolder.getGatt();
            BluetoothGattCharacteristic characteristic = BleClientHolder.getRxCharacteristic();

            handler.postDelayed(() -> {
              if("AuthDL".equalsIgnoreCase(packetType)) {
                Log.d(TAG, "Received Auth DL: " + formatted);
                sendLog("Received Auth DL: " + formatted);

                if (!isAuthDlWrittenToDevice) {
                  if (gatt == null || characteristic == null) {
                    Log.w(TAG, "BLE not ready. Cannot send Auth DL now.");
                    return;
                  }

                  boolean bret = writeDataToDevice(characteristic, formatted);

                  if (bret) {
                    isAuthDlWrittenToDevice = true;
                    authSentAfterReconnect = true;
                    notificationReceived = false;

                    Log.d(TAG, "Auth DL written to device. Waiting for notification...");
                    sendLog("Auth DL written to device. Waiting for notification...");
                    // ❌ No fallback timer here — notification MUST be received to continue
                  } else {
                    isAuthDlWrittenToDevice = false;
                    authSentAfterReconnect = false;
                    Log.d(TAG, "Failed to write Auth DL.");
                  }
                }
              }
               else {
                Log.d(TAG, "Received Normal DL. Adding to queue.");
                sendLog("Received Normal DL. Adding to queue: " + formatted);
                normalDlQueue.add(formatted);
                if (gatt != null && characteristic != null && authSentAfterReconnect) {
                  sendNextNormalDl();  // This now follows strict notif → 5s → next DL logic
                }
              }
            }, 1500);

          } catch (Exception e) {
            Log.e(TAG, "Failed to parse MQTT message", e);
          }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
          Log.d(TAG, "Message delivery complete");
          sendLog("Message delivery complete");
          lastProcessedPayload = null;
        }
      });

      isMqttCallbackSet = true;
    }
  }

  public void subscribeToTopic(String topicToSubscribe, MqttDownlinkCallBack mqttDownlinkCallBack) {
    TOPIC_to_Subscribe = topicToSubscribe;
    if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
      Log.e(TAG, "MQTT client is not connected or initialized.");
//      sendNotification("MQTT client is not connected or initialized.");
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
        sendLog("Subscribed to topic: " + topicToSubscribe);

        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to subscribe to topic: " + topicToSubscribe, exception);
        sendLog("Failed to subscribe to topic: " + topicToSubscribe+ exception);
        if(mqttDownlinkCallBack != null) {
          mqttDownlinkCallBack.onFailure(exception);
        }
      }
    });
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
      SharedPreferences prefs = getSharedPreferences("mqtt_prefs", Context.MODE_PRIVATE);
      String clientId = prefs.getString("mqtt_client_id", null);

      if (clientId == null) {
        clientId = "client_" + UUID.randomUUID().toString();
        prefs.edit().putString("mqtt_client_id", clientId).apply();
      }

      if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
        Log.d(TAG, "MQTT already connected. Skipping reconnect.");
      }


      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
        Log.e(TAG, "MQTT client is null or disconnected. Attempting to reconnect...");

        if (MQTT_URL == null || MQTT_USERNAME == null || MQTT_Password == null) {
          Log.e(TAG, "MQTT credentials missing. Cannot reconnect.");
          if (mqttUplinkCallBack != null) mqttUplinkCallBack.onFailure(new Exception("MQTT client not connected and credentials missing"));
          return;
        }

        // Reinitialize
        mqttAndroidClient = new MqttAndroidClient(getAppContext(), MQTT_URL,clientId , Ack.AUTO_ACK,null,false,1000);
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
          sendLog("Message published after reconnect"+message);
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
          sendLog("Message published immediately"+message);
          sendMqttPublishStatus("Message published immediately" + mqttMessage);

          if (callback != null) callback.onSuccess();
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          saveNotification(message);

          Log.e(TAG, "Immediate publish failed", exception);
          sendMqttPublishStatus("Immediate publish failed");
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
//    sendNotification("Service destroyed");
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
