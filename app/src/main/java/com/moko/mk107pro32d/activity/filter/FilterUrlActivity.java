package com.moko.mk107pro32d.activity.filter;

import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityFilterUrl107pro32dBinding;
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

public class FilterUrlActivity extends BaseActivity<ActivityFilterUrl107pro32dBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;

    @Override
    protected void onCreate() {
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(37), inputFilter});
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
        getFilterUrl();
    }

    @Override
    protected ActivityFilterUrl107pro32dBinding getViewBinding() {
        return ActivityFilterUrl107pro32dBinding.inflate(getLayoutInflater());
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
            XLog.e(e);
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_URL) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbUrl.setChecked(result.data.get("switch_value").getAsInt() == 1);
            mBind.etUrl.setText(result.data.get("url").getAsString());
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_URL) {
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

    private void getFilterUrl() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_URL;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        saveParams();
    }

    private void saveParams() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_URL;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", mBind.cbUrl.isChecked() ? 1 : 0);
        jsonObject.addProperty("url", mBind.etUrl.getText().toString());
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }
}
