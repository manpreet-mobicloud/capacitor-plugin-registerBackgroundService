package com.mobicloud.plugins.backgroundservice;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Arrays;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

@CapacitorPlugin(name = "MqttService")
public class BackgroundServicePlugin extends Plugin {
    // Define persistence
    MqttClientPersistence persistence = new MemoryPersistence();
    private static final String CHANNEL_ID = "mqtt_notifications";
    private static final int NOTIFICATION_ID = 1;

    // Enable reconnect
    boolean useReconnect = true;

    // Define max inflight messages
    int maxInflight = 10;

    private static final String BROKER_URL = "ssl://platform.iot.tatacommunications.com:8883"; // Replace with your MQTT broker URL and port
    private static final String USERNAME = "fe_regulator"; // Replace with your username
    private static final String PASSWORD = "FeRegulator@123"; // Replace with your password
    private static final String TOPIC_to_Subscribe = "send/command";
    private static final String TOPIC_to_Publish = "devices/status";
    private MqttAndroidClient mqttAndroidClient;

    private void createNotificationChannel() {
      CharSequence name = "MQTT Notifications";
      String description = "Notifications for MQTT events";
      int importance = NotificationManager.IMPORTANCE_HIGH;
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
      channel.setDescription(description);

      NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }

    private void showNotification(String title, String message) {
      Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
      PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

      NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_2)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent);

      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
      if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @PluginMethod
    public void connectToBroker(PluginCall call)
    {
        String message = "Hello MQTT Broker,from mobicloud!!!";
        createNotificationChannel(); // Ensure notification channel exists

        Context appcontext = this.getActivity().getApplicationContext();

        //String clientId = "FE-Regulator-client";
        this.mqttAndroidClient = new MqttAndroidClient(appcontext, BROKER_URL, MqttClient.generateClientId(), Ack.AUTO_ACK,persistence,useReconnect,maxInflight);

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setUserName(USERNAME);
        mqttConnectOptions.setPassword(PASSWORD.toCharArray());
        mqttConnectOptions.setKeepAliveInterval(10);
        mqttConnectOptions.setCleanSession(false);

        mqttAndroidClient.connect(mqttConnectOptions, getContext(), new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Connected to broker");
                showNotification("MQTT Connected", "Successfully connected to the broker.");

                System.out.println("Connected to MQTT Broker");
                JSObject result = new JSObject();
                result.put("status","connected");
                call.resolve(result);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Failed to connect to broker", exception);
                showNotification("MQTT Connection Failed", "Failed to connect to broker.");
                System.out.println("Failed to connect to broker: "+exception);
                call.reject("Failed to connect to mqtt broker");
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
                    call.reject("Connection lost failed to reconnect");
                    throw new RuntimeException(e);

                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Log.d(TAG, "Message received from topic: " + topic + " - " + new String(message.getPayload()));
                showNotification("New MQTT Message", "Topic: " + topic + " | Message: " + Arrays.toString(message.getPayload()));

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

    @PluginMethod
    public void subscribeToTopic(PluginCall call) {
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "MQTT client is not connected or initialized.");
            call.reject("MQTT client is not connected or initialized.");
            return;
        }

        String topic = call.getString("topic");
        if (topic == null || topic.isEmpty()) {
            call.reject("Topic is required");
            return;
        }

        mqttAndroidClient.subscribe(topic, 1, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Subscribed to topic: " + topic);
                showNotification("MQTT Downlink - Status", "Subscribed to topic: "+topic);
                JSObject result = new JSObject();
                result.put("status", "Subscribed successfully");
                call.resolve(result);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Failed to subscribe to topic: " + topic, exception);
                call.reject("Failed to subscribe to topic");
            }
        });
    }

    @PluginMethod
    public void publishMessage(PluginCall call) {
        // Retrieve topic and message from the PluginCall
        String topic = call.getString("topic");
        String message = call.getString("message");

        if (topic == null || topic.isEmpty()) {
            call.reject("Topic is required");
            return;
        }

        if (message == null || message.isEmpty()) {
            call.reject("Message is required");
            return;
        }

        // Check if the MQTT client is initialized and connected
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "MQTT client is not connected.");
            call.reject("MQTT client is not connected.");
            return;
        }

        // Create and configure MQTT message
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1); // QoS level (0, 1, or 2)

        // Publish the message
        mqttAndroidClient.publish(topic, mqttMessage);

        Log.d(TAG, "Message published to topic: " + topic + " with payload: " + message);
        showNotification("MQTT Uplink - Status", "Message Published Successfully");

        System.out.println("Message successfully published: " + mqttMessage);

        // Send success response to the JS side
        JSObject result = new JSObject();
        result.put("status", "Message published successfully");
        result.put("topic", topic);
        call.resolve(result);

    }
}
