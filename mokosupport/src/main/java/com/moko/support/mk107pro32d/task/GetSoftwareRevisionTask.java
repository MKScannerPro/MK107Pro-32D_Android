package com.moko.support.mk107pro32d.task;

import com.moko.ble.lib.task.OrderTask;
import com.moko.support.mk107pro32d.entity.OrderCHAR;

public class GetSoftwareRevisionTask extends OrderTask {

    public byte[] data;

    public GetSoftwareRevisionTask() {
        super(OrderCHAR.CHAR_SOFTWARE_REVISION, OrderTask.RESPONSE_TYPE_READ);
    }

    @Override
    public byte[] assemble() {
        return data;
    }
}
