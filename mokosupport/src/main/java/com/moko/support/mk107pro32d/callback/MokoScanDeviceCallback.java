package com.moko.support.mk107pro32d.callback;

import com.moko.support.mk107pro32d.entity.DeviceInfo;

public interface MokoScanDeviceCallback {
    void onStartScan();

    void onScanDevice(DeviceInfo device);

    void onStopScan();
}
