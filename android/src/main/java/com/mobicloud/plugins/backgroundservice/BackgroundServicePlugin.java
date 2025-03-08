package com.mobicloud.plugins.backgroundservice;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "BackgroundService")
public class BackgroundServicePlugin extends Plugin {


    @PluginMethod
    public void StartBackgroundService(PluginCall call) throws JSONException {
        String deviceId = call.getString("deviceId");
        String BrokerUrl = call.getString("BrokerUrl");
        String username = call.getString("username");
        String password = call.getString("password");
        String topicToSubscribe = call.getString("topicTOSubscribe");
        String topicToPublish = call.getString("topicTOpublish");
        JSObject messageToPublish = call.getObject("messageTOPublish");

        JSONObject jsonMessage = new JSONObject(messageToPublish.toString());
        String messageString = jsonMessage.toString(); // Convert to JSON string

        if (deviceId == null || deviceId.isEmpty()) {
            call.reject("Device ID is required");
            return;
        }

        Intent serviceIntent = new Intent(getContext(), BackgroundService.class);
        serviceIntent.putExtra("deviceId", deviceId);
        serviceIntent.putExtra("BrokerUrl",BrokerUrl);
        serviceIntent.putExtra("username",username);
        serviceIntent.putExtra("password",password);
        serviceIntent.putExtra("topicToSubscribe",topicToSubscribe);
        serviceIntent.putExtra("topicToPublish",topicToPublish);
        serviceIntent.putExtra("messageToPublish",messageString);

        getContext().startService(serviceIntent);

        call.resolve();
    }


}
