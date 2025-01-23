package com.mobicloud.plugins.backgroundservice;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

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

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

@CapacitorPlugin(name = "MqttService")
public class BackgroundServicePlugin extends Plugin {
    // Define persistence
    MqttClientPersistence persistence = new MemoryPersistence();

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

    @PluginMethod
    public void connectToBroker(PluginCall call)
    {
        String message = "Hello MQTT Broker,from mobicloud!!!";

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
                System.out.println("Connected to MQTT Broker");
                JSObject result = new JSObject();
                result.put("status","connected");
                call.resolve(result);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Failed to connect to broker", exception);
                System.out.println("Failed to connect to broker");
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
        System.out.println("Message successfully published: " + mqttMessage);

        // Send success response to the JS side
        JSObject result = new JSObject();
        result.put("status", "Message published successfully");
        result.put("topic", topic);
        call.resolve(result);

    }
}
