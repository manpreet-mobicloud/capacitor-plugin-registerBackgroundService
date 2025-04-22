package com.mobicloud.plugins.backgroundservice;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "BackgroundService")
public class BackgroundServicePlugin extends Plugin {

    private BroadcastReceiver mqttReceiver;

    @PluginMethod
    public void StartBackgroundService(PluginCall call) throws JSONException {
        String baseURL = call.getString("baseURL");
        String basicAuth = call.getString("basicAUTH");
        String apiSuffix = call.getString("apiSuffix");
        String deviceUUID = call.getString("deviceUUID");
        String deviceType = call.getString("deviceType");
        String macAddress = call.getString("macAddress");
        String BrokerUrl = call.getString("BrokerUrl");
        String username = call.getString("username");
        String password = call.getString("password");
        String topicToSubscribe = call.getString("topicTOSubscribe");
        String topicToPublish = call.getString("topicTOpublish");
        String authTopicToSubscribe = call.getString("authTopicToSubscribe");
        JSObject authPayload = call.getObject("authPayload");
        JSObject messageToPublishForAlerts = call.getObject("messageToPublishForAlerts");
        JSObject messageToPublishForGasComsumtion = call.getObject("messageToPublishForGasComsumtion");

        JSObject apiPayload = new JSObject(authPayload.toString());
        JSONObject jsonMessage1 = new JSONObject(messageToPublishForAlerts.toString());
        JSONObject jsonMessage2 = new JSONObject(messageToPublishForGasComsumtion.toString());

        String message1 = jsonMessage1.toString();
        String message2 = jsonMessage2.toString();
        String payload = apiPayload.toString();
        Intent serviceIntent = new Intent(getContext(), BackgroundService.class);

        serviceIntent.putExtra("baseURL",baseURL);
        serviceIntent.putExtra("basicAuth",basicAuth);
        serviceIntent.putExtra("apiSuffix",apiSuffix);
        serviceIntent.putExtra("deviceUUID",deviceUUID);
        serviceIntent.putExtra("deviceType", deviceType);
        serviceIntent.putExtra("BrokerUrl", BrokerUrl);
        serviceIntent.putExtra("username", username);
        serviceIntent.putExtra("password", password);
        serviceIntent.putExtra("topicToSubscribe", topicToSubscribe);
        serviceIntent.putExtra("topicToPublish", topicToPublish);
        serviceIntent.putExtra("authTopicToSubscribe",authTopicToSubscribe);
        serviceIntent.putExtra("apiPayload",payload);
        serviceIntent.putExtra("messageToPublishForAlerts", message1);
        serviceIntent.putExtra("messageToPublishForGasComsumtion", message2);

        if ("BLE".equals(deviceType)) {
            if (macAddress == null || macAddress.isEmpty()) {
                call.reject("Device ID is required");
//                return;
            }
            serviceIntent.putExtra("macAddress", macAddress);
        }

        // Register BroadcastReceiver if not already
        if (mqttReceiver == null) {
            mqttReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String mqttMessage = intent.getStringExtra("message");
                    JSObject data = new JSObject();
                    data.put("message", mqttMessage);
                    System.out.println("Message to send angular is : "+mqttMessage);
                    System.out.println("MQTT"+ "Sending message to Angular: " + mqttMessage);
                    notifyListeners("onMqttMessage", data);
                }
            };
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(mqttReceiver, new IntentFilter("com.mobicloud.MQTT_MESSAGE"));
        }

        getContext().startService(serviceIntent);
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (mqttReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mqttReceiver);
            mqttReceiver = null;
        }
    }
}
