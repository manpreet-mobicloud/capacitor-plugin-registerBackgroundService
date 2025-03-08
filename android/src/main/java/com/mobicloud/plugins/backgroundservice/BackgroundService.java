package com.mobicloud.plugins.backgroundservice;

import static android.content.ContentValues.TAG;

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
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class BackgroundService extends Service {
  private static final String TAG = "BackgroundService";
  private static final String CHANNEL_ID = "BackgroundServiceChannel";
  private static final int NOTIFICATION_ID = 1;

  private BluetoothAdapter bluetoothAdapter;
  private BluetoothLeScanner bluetoothLeScanner;
  private BluetoothStateReceiver bluetoothStateReceiver;
  private String deviceAddress;
  private BluetoothGatt bluetoothGatt;
  private boolean isConnected = false;

  private boolean isDeviceConnected = false;
  private final AtomicBoolean isScanning = new AtomicBoolean(false);
  private final Handler scanHandler = new Handler();

  // Define persistence
  MqttClientPersistence persistence = new MemoryPersistence();

  // Enable reconnect
  boolean useReconnect = true;

  // Define max inflight messages
  int maxInflight = 10;

  private static String BROKER_URL = ""; // Replace with your MQTT broker URL and port
  private static String USERNAME = ""; // Replace with your username
  private static String PASSWORD = ""; // Replace with your password
  private static String TOPIC_to_Subscribe = " ";
  private static String TOPIC_to_Publish = " ";

  private static String MESSAGE_to_Publish = "";
  private MqttAndroidClient mqttAndroidClient;

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
       BROKER_URL = intent.getStringExtra("BrokerUrl");
       USERNAME = intent.getStringExtra("username");
       PASSWORD = intent.getStringExtra("password");
       TOPIC_to_Subscribe = intent.getStringExtra("topicToSubscribe");
       TOPIC_to_Publish = intent.getStringExtra("topicToPublish");
       MESSAGE_to_Publish = intent.getStringExtra("messageToPublish");
    }

    if (intent != null && intent.hasExtra("deviceId"))
    {
      deviceAddress = intent.getStringExtra("deviceId");
      Log.d(TAG, "Received deviceId: " + deviceAddress);
      System.out.println("Received DeviceId: "+deviceAddress);
      connectToDevice(deviceAddress);
    } else {
      Log.e(TAG, "No deviceId received!");
    }

    checkInitialBluetoothState();
    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
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
      .setContentText("Bluetooth monitoring service running")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
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
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true);

    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager != null) {
      manager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  private void connectToDevice(String address) {
    if (bluetoothAdapter == null || address == null) {
      Log.e(TAG, "BluetoothAdapter not initialized or invalid address.");
      System.out.println("BluetoothAdapter not initialized or invalid address.");
      return;
    }

    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT permission missing.");
      return;
    }

    bluetoothGatt = device.connectGatt(this, false, gattCallback);
    Log.d(TAG, "Attempting to connect to BLE device: " + address);
    System.out.println("Attempting to connect to BLE device: " + address);
  }

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      if (newState == BluetoothGatt.STATE_CONNECTED) {
        isConnected = true;
        Log.d(TAG, "Connected to BLE device.");
        System.out.println("Connected to BLE Device");
        updateNotification("Connected to BLE device.");
        isDeviceConnected = true;
        connectToBroker();
        stopScanning();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
          return;
        }

        gatt.discoverServices();
      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        isConnected = false;
        Log.d(TAG, "Disconnected. Scanning for device...");
        updateNotification("Disconnected. Searching for device...");
        isDeviceConnected = false;
        startScanningForDevice();
      }
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

      // Register receiver to listen for Bluetooth turning ON
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      getApplicationContext().registerReceiver(bluetoothStateReceiver, filter);

      isScanning.set(false);
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
      if (result.getDevice().getAddress().equals(deviceAddress)) {
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
        connectToDevice(deviceAddress);
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
          startScanningForDevice();
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

  public void connectToBroker()
  {
    createNotificationChannel(); // Ensure notification channel exists

    Context appcontext = getApplicationContext();

    //String clientId = "FE-Regulator-client";
    this.mqttAndroidClient = new MqttAndroidClient(appcontext, BROKER_URL, MqttClient.generateClientId(), Ack.AUTO_ACK,persistence,useReconnect,maxInflight);

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
        updateNotification("Successfully connected to the MQTT broker.");

        System.out.println("Connected to MQTT Broker");
        subscribeToTopic(TOPIC_to_Subscribe);

        publishMessage(TOPIC_to_Publish, MESSAGE_to_Publish);

        // JSObject result = new JSObject();
        // result.put("status","connected");
        // call.resolve(result);
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to connect to broker", exception);
        updateNotification( "Failed to connect to MQTT broker.");
        System.out.println("Failed to connect to broker: "+exception);
//                call.reject("Failed to connect to mqtt broker");
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
//                    call.reject("Connection lost failed to reconnect");
          throw new RuntimeException(e);

        }
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        Log.d(TAG, "Message received from topic: " + topic + " - " + new String(message.getPayload()));
        updateNotification("Received Topic: " + topic + " | Message: " + Arrays.toString(message.getPayload()));

        System.out.println("Message received from topic: "+ topic +" - "+new String(message.getPayload()));

        // Use Capacitor bridge to pass messages to JavaScript
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Message delivery complete");
        System.out.println("Message Delivery Complete");
      }
    });
  }

  public void subscribeToTopic(String topicToSubscribe) {
    if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
      Log.e(TAG, "MQTT client is not connected or initialized.");
//            call.reject("MQTT client is not connected or initialized.");
      return;
    }

    TOPIC_to_Subscribe = topicToSubscribe;

    if (TOPIC_to_Subscribe == null || TOPIC_to_Subscribe.isEmpty()) {
//            call.reject("Topic is required");
      return;
    }

    mqttAndroidClient.subscribe(TOPIC_to_Subscribe, 1, getContext(), new IMqttActionListener() {
      @Override
      public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "Subscribed to topic: " + TOPIC_to_Subscribe);
        System.out.println("Subscribed to topic: " + TOPIC_to_Subscribe);
        updateNotification("Subscribed to topic: "+TOPIC_to_Subscribe);
        JSObject result = new JSObject();
        result.put("status", "Subscribed successfully");
//                call.resolve(result);
      }

      @Override
      public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        Log.e(TAG, "Failed to subscribe to topic: " + TOPIC_to_Subscribe, exception);
//                call.reject("Failed to subscribe to topic");
      }
    });
  }

  public void publishMessage(String topicToPublish, String messageToPublish) {
    // Retrieve topic and message from the PluginCall
    TOPIC_to_Publish = topicToPublish;
    System.out.print("Message to Publish : "+messageToPublish);

    if (TOPIC_to_Publish == null || TOPIC_to_Publish.isEmpty()) {
//            call.reject("Topic is required");
      return;
    }

    if (messageToPublish == null) {
//            call.reject("Message is required");
      return;
    }

    try {
      // Convert JSObject to a proper JSON string
      // Check if the MQTT client is initialized and connected
      if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
        Log.e(TAG, "MQTT client is not connected.");
//                call.reject("MQTT client is not connected.");
        return;
      }

      // Create and configure MQTT message
      MqttMessage mqttMessage = new MqttMessage(messageToPublish.getBytes(StandardCharsets.UTF_8));
      mqttMessage.setQos(1); // QoS level (0, 1, or 2)

      // Publish the message
      mqttAndroidClient.publish(TOPIC_to_Publish, mqttMessage,getContext(),new IMqttActionListener(){

        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG, "Message published to topic: " + TOPIC_to_Publish + " with payload: " + messageToPublish);
          updateNotification("Message Published Successfully");

          System.out.println("Message successfully published: " + messageToPublish);

          // Send success response to the JS side
          JSObject result = new JSObject();
          result.put("status", "Message published successfully");
          result.put("topic", TOPIC_to_Publish);
//                    call.resolve(result);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.d(TAG, "Failed to publish to topic: " + TOPIC_to_Publish + " with payload: " + messageToPublish);
          updateNotification("Failed to publish message");

          System.out.println("Failed to publish to topic: " + TOPIC_to_Publish + " with payload: " + messageToPublish);
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Failed to publish MQTT message", e);
//            call.reject("Failed to publish MQTT message: " + e.getMessage());
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "Service destroyed");
    deviceAddress = null;
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
