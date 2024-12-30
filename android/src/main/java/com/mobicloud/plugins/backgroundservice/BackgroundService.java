package com.mobicloud.plugins.backgroundservice;

import android.util.Log;

public class BackgroundService {

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }
}
