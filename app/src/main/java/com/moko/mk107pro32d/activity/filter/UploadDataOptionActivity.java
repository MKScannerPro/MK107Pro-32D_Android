package com.moko.mk107pro32d.activity.filter;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityUploadDataOption107pro32dBinding;
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

public class UploadDataOptionActivity extends BaseActivity<ActivityUploadDataOption107pro32dBinding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getUploadDataOption();
    }

    @Override
    protected ActivityUploadDataOption107pro32dBinding getViewBinding() {
        return ActivityUploadDataOption107pro32dBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_UPLOAD_DATA_OPTION) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbTimestamp.setChecked(result.data.get("timestamp").getAsInt() == 1);
            mBind.cbRawDataAdv.setChecked(result.data.get("adv_data").getAsInt() == 1);
            mBind.cbRawDataRsp.setChecked(result.data.get("rsp_data").getAsInt() == 1);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_UPLOAD_DATA_OPTION) {
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    private void setUploadDataOption() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_UPLOAD_DATA_OPTION;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("timestamp", mBind.cbTimestamp.isChecked() ? 1 : 0);
        jsonObject.addProperty("adv_data", mBind.cbRawDataAdv.isChecked() ? 1 : 0);
        jsonObject.addProperty("rsp_data", mBind.cbRawDataRsp.isChecked() ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getUploadDataOption() {
        int msgId = MQTTConstants.READ_MSG_ID_UPLOAD_DATA_OPTION;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setUploadDataOption();
    }
}
