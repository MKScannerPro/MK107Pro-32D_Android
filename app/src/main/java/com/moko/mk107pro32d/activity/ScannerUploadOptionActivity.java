package com.moko.mk107pro32d.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.R;
import com.moko.mk107pro32d.activity.filter.DuplicateDataFilterActivity;
import com.moko.mk107pro32d.activity.filter.FilterAdvNameActivity;
import com.moko.mk107pro32d.activity.filter.FilterMacAddressActivity;
import com.moko.mk107pro32d.activity.filter.FilterRawDataSwitchActivity;
import com.moko.mk107pro32d.activity.filter.UploadDataOptionActivity;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityScannerUploadOption107pro32dBinding;
import com.moko.mk107pro32d.dialog.BottomDialog;
import com.moko.mk107pro32d.entity.MQTTConfig;
import com.moko.mk107pro32d.entity.MokoDevice;
import com.moko.mk107pro32d.utils.SPUtiles;
import com.moko.mk107pro32d.utils.ToastUtils;
import com.moko.support.mk107pro32d.MQTTConstants;
import com.moko.support.mk107pro32d.MQTTSupport;
import com.moko.support.mk107pro32d.entity.MsgConfigResult;
import com.moko.support.mk107pro32d.entity.MsgReadResult;
import com.moko.support.mk107pro32d.event.DeviceOnlineEvent;
import com.moko.support.mk107pro32d.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

public class ScannerUploadOptionActivity extends BaseActivity<ActivityScannerUploadOption107pro32dBinding> implements SeekBar.OnSeekBarChangeListener {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private final String[] mRelationshipValues = {"Null", "Only MAC", "Only ADV Name", "Only RAW DATA", "ADV name&Raw data", "MAC&ADV name&Raw data", "ADV name | Raw data", "ADV NAME & MAC"};
    private int mRelationshipSelected;
    private final String[] phyArr = {"1M PHY(V4.2)", "1M PHY(V5.0)", "1M PHY(V4.2) & 1M PHY(V5.0)", "Coded PHY(V5.0)"};
    private int phySelected;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBind.tvName.setText(mMokoDevice.name);
        mBind.sbRssiFilter.setOnSeekBarChangeListener(this);
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getFilterRSSI();
        mBind.tvFilterPhy.setOnClickListener(v -> onFilterPhyClick());
    }

    @Override
    protected ActivityScannerUploadOption107pro32dBinding getViewBinding() {
        return ActivityScannerUploadOption107pro32dBinding.inflate(getLayoutInflater());
    }

    @SuppressLint("DefaultLocale")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            XLog.e(e);
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_RSSI) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            final int rssi = result.data.get("rssi").getAsInt();
            int progress = rssi + 127;
            mBind.sbRssiFilter.setProgress(progress);
            mBind.tvRssiFilterValue.setText(String.format("%ddBm", rssi));
            mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, rssi));
            getFilterRelationship();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_RELATIONSHIP) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            final int relation = result.data.get("relation").getAsInt();
            mRelationshipSelected = relation;
            mBind.tvFilterRelationship.setText(mRelationshipValues[relation]);
            getFilterPhy();
        }

        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_PHY) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            phySelected = result.data.get("phy_filter").getAsInt();
            mBind.tvFilterPhy.setText(phyArr[phySelected]);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_RSSI) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code != 0) return;
            setFilterRelationship();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_RELATIONSHIP) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code != 0) return;
            setFilterPhy();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_PHY) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    private void onFilterPhyClick() {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(phyArr)), phySelected);
        dialog.setListener(value -> {
            phySelected = value;
            mBind.tvFilterPhy.setText(phyArr[value]);
        });
        dialog.show(getSupportFragmentManager());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    private void getFilterRSSI() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_RSSI;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setFilterRSSI() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_RSSI;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("rssi", mBind.sbRssiFilter.getProgress() - 127);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getFilterRelationship() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_RELATIONSHIP;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getFilterPhy() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_PHY;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setFilterRelationship() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_RELATIONSHIP;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("relation", mRelationshipSelected);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setFilterPhy() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_PHY;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("phy_filter", phySelected);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    public void onFilterRelationship(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(mRelationshipValues)), mRelationshipSelected);
        dialog.setListener(value -> {
            mRelationshipSelected = value;
            mBind.tvFilterRelationship.setText(mRelationshipValues[value]);
        });
        dialog.show(getSupportFragmentManager());
    }

    private void start(Class<?> clazz) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, clazz);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onDuplicateDataFilter(View view) {
        start(DuplicateDataFilterActivity.class);
    }

    public void onUploadDataOption(View view) {
        start(UploadDataOptionActivity.class);
    }

    public void onFilterByMac(View view) {
        start(FilterMacAddressActivity.class);
    }

    public void onFilterByName(View view) {
        start(FilterAdvNameActivity.class);
    }

    public void onFilterByRawData(View view) {
        start(FilterRawDataSwitchActivity.class);
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setFilterRSSI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_FILTER_CONDITION) {
            showLoadingProgressDialog();
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            getFilterRSSI();
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        int rssi = progress - 127;
        mBind.tvRssiFilterValue.setText(String.format("%ddBm", rssi));
        mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, rssi));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
