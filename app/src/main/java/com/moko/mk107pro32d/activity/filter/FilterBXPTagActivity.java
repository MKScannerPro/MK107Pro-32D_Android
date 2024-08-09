package com.moko.mk107pro32d.activity.filter;


import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.R;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityFilterBxpTag107pro32dBinding;
import com.moko.mk107pro32d.dialog.AlertMessageDialog;
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
import java.util.List;

public class FilterBXPTagActivity extends BaseActivity<ActivityFilterBxpTag107pro32dBinding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private List<String> filterTagList;

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
        getFilterTag();
    }

    @Override
    protected ActivityFilterBxpTag107pro32dBinding getViewBinding() {
        return ActivityFilterBxpTag107pro32dBinding.inflate(getLayoutInflater());
    }

    @SuppressLint("DefaultLocale")
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
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_BXP_TAG) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbBxpTag.setChecked(result.data.get("switch_value").getAsInt() == 1);
            mBind.cbPreciseMatch.setChecked(result.data.get("precise").getAsInt() == 1);
            mBind.cbReverseFilter.setChecked(result.data.get("reverse").getAsInt() == 1);
            JsonArray tagList = result.data.getAsJsonArray("tagid");
            int number = tagList.size();
            filterTagList = new ArrayList<>();
            if (number != 0) {
                int index = 1;
                for (JsonElement jsonElement : tagList) {
                    filterTagList.add(jsonElement.getAsString());
                    String tagId = jsonElement.getAsString();
                    View v = LayoutInflater.from(this).inflate(R.layout.item_mac_address_filter_107pro32d, mBind.llTagId, false);
                    TextView title = v.findViewById(R.id.tv_mac_address_title);
                    EditText etTadId = v.findViewById(R.id.et_mac_address);
                    title.setText(String.format("ID %d", index));
                    etTadId.setText(tagId);
                    mBind.llTagId.addView(v);
                    index++;
                }
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_TAG) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
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

    private void getFilterTag() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_BXP_TAG;
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
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }

    @SuppressLint("DefaultLocale")
    public void onAdd(View view) {
        if (isWindowLocked()) return;
        int count = mBind.llTagId.getChildCount();
        if (count > 9) {
            ToastUtils.showToast(this, "You can set up to 10 filters!");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.item_mac_address_filter_107pro32d, mBind.llTagId, false);
        TextView title = v.findViewById(R.id.tv_mac_address_title);
        title.setText(String.format("ID %d", count + 1));
        mBind.llTagId.addView(v);
    }

    public void onDel(View view) {
        if (isWindowLocked()) return;
        final int c = mBind.llTagId.getChildCount();
        if (c == 0) {
            ToastUtils.showToast(this, "There are currently no filters to delete");
            return;
        }
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning");
        dialog.setMessage("Please confirm whether to delete it, if yes, the last option will be deleted!");
        dialog.setOnAlertConfirmListener(() -> {
            int count = mBind.llTagId.getChildCount();
            if (count > 0) {
                mBind.llTagId.removeViewAt(count - 1);
            }
        });
        dialog.show(getSupportFragmentManager());
    }


    private void saveParams() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_TAG;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", mBind.cbBxpTag.isChecked() ? 1 : 0);
        jsonObject.addProperty("precise", mBind.cbPreciseMatch.isChecked() ? 1 : 0);
        jsonObject.addProperty("reverse", mBind.cbReverseFilter.isChecked() ? 1 : 0);
        JsonArray macList = new JsonArray();
        for (String mac : filterTagList)
            macList.add(mac);
        jsonObject.add("tagid", macList);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private boolean isValid() {
        final int c = mBind.llTagId.getChildCount();
        if (c > 0) {
            // 发送设置的过滤RawData
            int count = mBind.llTagId.getChildCount();
            if (count == 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            filterTagList.clear();
            for (int i = 0; i < count; i++) {
                View v = mBind.llTagId.getChildAt(i);
                EditText etMacAddress = v.findViewById(R.id.et_mac_address);
                final String macAddress = etMacAddress.getText().toString();
                if (TextUtils.isEmpty(macAddress)) {
                    ToastUtils.showToast(this, "Para Error");
                    return false;
                }
                int length = macAddress.length();
                if (length % 2 != 0) {
                    ToastUtils.showToast(this, "Para Error");
                    return false;
                }
                filterTagList.add(macAddress);
            }
        } else {
            filterTagList = new ArrayList<>();
        }
        return true;
    }
}
