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
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class BackgroundService extends Service {
  private static final String TAG = "BackgroundService";
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;

  private BluetoothAdapter bluetoothAdapter;
  private BluetoothLeScanner bluetoothLeScanner;
  private BluetoothStateReceiver bluetoothStateReceiver;
  private String macAddress;
  private BluetoothGatt bluetoothGatt;

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

  private static String payloadData = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Long lastAuthDlTime = null; // set this when MQTT DL arrives

  private MqttAndroidClient mqttAndroidClient;

  private BluetoothGattCharacteristic txCharacteristic;

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

    createNotificationChannel();
    setupBluetoothMonitoring();
    startForegroundServiceCompat();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "Service started");

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
//        System.out.println("Received macAddress: "+macAddress);
//
//        connectToDevice(macAddress);
      }
    }

    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
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
        System.out.println("TTL valid, Auth DL not required. Time left: " + ttlRemaining + "s");
      }

      handler.postDelayed(authDlCheckRunnable, 1000); // check again after 1 sec
    }
  };

  private final Runnable Uplinks = new Runnable() {
    @Override
    public void run() {
      publishMessage(TOPIC_to_Publish,payloadData, new MqttUplinkCallBack() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(Throwable exception) {
        }
      });
      handler.postDelayed(Uplinks,1800000); // check again after 30 minute
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
            Log.d("BackgroundService", "HTTP Response Code: " + responseCode);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

        } catch (Exception e) {
            Log.e("BackgroundService", "HTTP request failed", e);
            System.out.println("Error Occured While Performing HTTP Request: "+e);
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
    Intent notificationIntent = new Intent(this, com.mobicloud.plugins.MainActivity.class);
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

  private void setupBluetoothMonitoring() {
    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager != null) {
      bluetoothAdapter = bluetoothManager.getAdapter();
    }

    if (bluetoothAdapter == null) {
      Log.e(TAG, "Bluetooth not supported on this device.");
      return;
    }

    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

    bluetoothStateReceiver = new BluetoothStateReceiver();
    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    registerReceiver(bluetoothStateReceiver, filter);
  }

  private void checkInitialBluetoothState() {
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
      updateNotification("Service is running in the background.");
    } else {
      sendBluetoothNotification();
    }
  }

  private void sendBluetoothNotification() {
    Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
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
    try {
      if (bluetoothAdapter == null || address == null) {
        Log.e(TAG, "BluetoothAdapter not initialized or invalid address.");
        System.out.println("BluetoothAdapter not initialized or invalid address.");
      }

      BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

      if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
        return;
      }

      bluetoothGatt = device.connectGatt(this, true, gattCallback);
      Log.d(TAG, "Attempting to connect to BLE device: " + address);
      System.out.println("Attempting to connect to BLE device: " + address);

    } catch (IllegalArgumentException e) {
      System.out.println(e);
    } catch (Exception e) {
      System.out.println("Exception occured while connecting to BLE device: "+e);
      startScanningForDevice();
    }
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      if (newState == BluetoothGatt.STATE_CONNECTED) {
        Log.d(TAG, "Connected to BLE device.");
        System.out.println("Connected to BLE device");

        isDeviceConnected = true;
        stopScanning();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
          Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
          return;
        }

        gatt.discoverServices();

      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        Log.d(TAG, "Disconnected from BLE device.");
        stopPeriodicRead();
        txCharacteristic = null;
        isDeviceConnected = false;
        startScanningForDevice();
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        BluetoothGattService service = gatt.getService(Constants.NORDIC_SERVICE_UUID);
        if (service != null) {

          txCharacteristic = service.getCharacteristic(Constants.TX_CHARACTERISTIC_UUID);

          if (txCharacteristic != null) {
            readCharacteristic(txCharacteristic);
            enableNotification(txCharacteristic);
            startPeriodicRead();
          } else {
            Log.e(TAG, "TX Characteristic not found.");
          }

        } else {
          Log.e(TAG, "Nordic UART service not found.");
        }
      } else {
        Log.e(TAG, "Service discovery failed with status: " + status);
      }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        UUID uuid = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        Log.d(TAG, "Data read from " + uuid + ": " + new String(data));
      } else {
        Log.e(TAG, "Read failed with status: " + status);
      }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      UUID uuid = characteristic.getUuid();
      byte[] data = characteristic.getValue();
      Log.d(TAG, "Notification received from " + uuid + ": " + new String(data));
    }
  };

  private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
    if (bluetoothGatt == null) {
      Log.e(TAG, "BluetoothGatt not initialized");
      return;
    }

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
      return;
    }

    boolean readInitiated = bluetoothGatt.readCharacteristic(characteristic);
    Log.d(TAG, "Read initiated: " + readInitiated + " for " + characteristic.getUuid());
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
    } else {
      Log.e(TAG, "Cannot stop scan: BluetoothLeScanner is null.");
    }
  }

  private final ScanCallback scanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      if (result.getDevice().getAddress().equals(macAddress)) {
        Log.d(TAG, "Device found! Reconnecting...");
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
          updateNotification("Bluetooth enabled. Service active.");
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

  public void connectToBroker(String BROKER_URL, String USERNAME, String PASSWORD, MqttConnectionCallback callBack)
  {
    this.mqttAndroidClient = new MqttAndroidClient(this.context, BROKER_URL, MqttClient.generateClientId(), Ack.AUTO_ACK,persistence,useReconnect,maxInflight);

    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
    mqttConnectOptions.setAutomaticReconnect(true);
    mqttConnectOptions.setCleanSession(false);
    mqttConnectOptions.setUserName(USERNAME);
    mqttConnectOptions.setPassword(PASSWORD.toCharArray());
    mqttConnectOptions.setKeepAliveInterval(60);

    mqttAndroidClient.connect(mqttConnectOptions, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Connected to broker");
        System.out.println("Connected to MQTT Broker");

        if (callBack != null) {
          callBack.onConnectionSuccess();
        }
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to connect to broker", exception);
        System.out.println("Failed to connect to broker: "+exception);

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
      public void messageArrived(String topic, MqttMessage message) {
        Log.d(TAG, "Message received from topic: " + topic + " - " + new String(message.getPayload()));
        System.out.println("Message received from topic: "+ topic +" - "+new String(message.getPayload()));

        String msg = new String(message.getPayload());

        // Broadcast the message to the plugin
        Intent intent = new Intent("com.mobicloud.MQTT_MESSAGE");
        intent.putExtra("message", msg);
        intent.putExtra("topic", topic);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Message delivery complete");
        System.out.println("Message Delivery Complete");
      }
    });
  }

  public void subscribeToTopic(String topicToSubscribe, MqttDownlinkCallBack mqttDownlinkCallBack) {
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

        handler.post(authDlCheckRunnable);

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

  public void publishMessage(String topicToPublish, String messageToPublish, MqttUplinkCallBack mqttUplinkCallBack) {
    // Retrieve topic and message from the PluginCall
    TOPIC_to_Publish = topicToPublish;

    payloadData = messageToPublish;

    System.out.print("Message to Publish : "+messageToPublish);

    if (topicToPublish == null || topicToPublish.isEmpty()) {
      return;
    }

    if (messageToPublish == null) {
      Log.e(TAG,"Empty Payload message cannot publish");
      return;
    }

    try {
      // Convert JSObject to a proper JSON string
      // Check if the MQTT client is initialized and connected
      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
        Log.e(TAG, "MQTT client is not connected.");
        return;
      }

      // Create and configure MQTT message
      MqttMessage mqttMessage = new MqttMessage(messageToPublish.getBytes(StandardCharsets.UTF_8));
      mqttMessage.setQos(1); // QoS level (0, 1, or 2)

      // Publish the message
      mqttAndroidClient.publish(topicToPublish, mqttMessage,getContext(),new IMqttActionListener(){

        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG, "Message published to topic: " + topicToPublish + " with payload: " + messageToPublish);

          System.out.println("Message successfully published: " + messageToPublish);

          // Send success response to the JS side
          JSObject result = new JSObject();
          result.put("status", "Message published successfully");
          result.put("topic", topicToPublish);
          handler.post(Uplinks);

          if(mqttUplinkCallBack != null) {
            mqttUplinkCallBack.onSuccess();
          }
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.d(TAG, "Failed to publish to topic: " + topicToPublish + " with payload: " + messageToPublish);
          System.out.println("Failed to publish to topic: " + topicToPublish + " with payload: " + messageToPublish);

          if(mqttUplinkCallBack != null) {
            mqttUplinkCallBack.onFailure(exception);
          }
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Failed to publish MQTT message", e);
    }
  }

//  public void publishMessageForConsumtion(String topicToPublish, String messageToPublish) {
//    // Retrieve topic and message from the PluginCall
//    System.out.print("Message to Publish : "+messageToPublish);
//
//    if (topicToPublish == null || topicToPublish.isEmpty()) {
////            call.reject("Topic is required");
//      return;
//    }
//
//    if (messageToPublish == null) {
////            call.reject("Message is required");
//      return;
//    }
//
//    try {
//      // Convert JSObject to a proper JSON string
//      // Check if the MQTT client is initialized and connected
//      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
//        Log.e(TAG, "MQTT client is not connected.");
////                call.reject("MQTT client is not connected.");
//        return;
//      }
//
//      // Create and configure MQTT message
//      MqttMessage mqttMessage = new MqttMessage(messageToPublish.getBytes(StandardCharsets.UTF_8));
//      mqttMessage.setQos(1); // QoS level (0, 1, or 2)
//
//      // Publish the message
//      mqttAndroidClient.publish(topicToPublish, mqttMessage,getContext(),new IMqttActionListener(){
//
//        @Override
//        public void onSuccess(IMqttToken asyncActionToken) {
//          Log.d(TAG, "Message published to topic: " + topicToPublish + " with payload: " + messageToPublish);
////          updateNotification("Message Published Successfully");
//
//          System.out.println("Message successfully published: " + messageToPublish);
//
//          // Send success response to the JS side
//          JSObject result = new JSObject();
//          result.put("status", "Message published successfully");
//          result.put("topic", topicToPublish);
////                    call.resolve(result);
//        }
//
//        @Override
//        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//          Log.d(TAG, "Failed to publish to topic: " + TOPIC_to_Publish + " with payload: " + messageToPublish);
////          updateNotification("Failed to publish message");
//
//          System.out.println("Failed to publish to topic: " + TOPIC_to_Publish + " with payload: " + messageToPublish);
//        }
//      });
//    } catch (Exception e) {
//      Log.e(TAG, "Failed to publish MQTT message", e);
////            call.reject("Failed to publish MQTT message: " + e.getMessage());
//    }
//  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "Service destroyed");
    macAddress = null;
    stopPeriodicRead();
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
