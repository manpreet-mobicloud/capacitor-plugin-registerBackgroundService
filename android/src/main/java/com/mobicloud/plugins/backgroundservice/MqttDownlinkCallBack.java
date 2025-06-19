package com.mobicloud.plugins.backgroundservice;

public interface MqttDownlinkCallBack {
    void onSuccess();

    void onFailure(Throwable exception);
}
