package com.mobicloud.plugins.backgroundservice;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class BleClientHolder {
  private static BluetoothGatt bluetoothGatt;
  private static BluetoothGattCharacteristic rxCharacteristic;

  public static void setGatt(BluetoothGatt gatt) {
    bluetoothGatt = gatt;
  }

  public static void setRxCharacteristic(BluetoothGattCharacteristic characteristic) {
    rxCharacteristic = characteristic;
  }

  public static BluetoothGatt getGatt() {
    return bluetoothGatt;
  }

  public static BluetoothGattCharacteristic getRxCharacteristic() {
    return rxCharacteristic;
  }

  public static boolean isBleReady() {
    return bluetoothGatt != null && rxCharacteristic != null;
  }

  public static void clear() {
    bluetoothGatt = null;
    rxCharacteristic = null;
  }
}
