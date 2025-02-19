package com.moko.mk107pro32d.activity.set;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.R;
import com.moko.mk107pro32d.activity.MainActivity107Pro32D;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityDeviceSetting107pro32dBinding;
import com.moko.mk107pro32d.db.DBTools;
import com.moko.mk107pro32d.dialog.AlertMessageDialog;
import com.moko.mk107pro32d.dialog.CustomDialog;
import com.moko.mk107pro32d.entity.MQTTConfig;
import com.moko.mk107pro32d.entity.MokoDevice;
import com.moko.mk107pro32d.utils.SPUtiles;
import com.moko.mk107pro32d.utils.ToastUtils;
import com.moko.support.mk107pro32d.MQTTConstants;
import com.moko.support.mk107pro32d.MQTTSupport;
import com.moko.support.mk107pro32d.entity.MsgConfigResult;
import com.moko.support.mk107pro32d.entity.MsgReadResult;
import com.moko.support.mk107pro32d.event.DeviceDeletedEvent;
import com.moko.support.mk107pro32d.event.DeviceModifyNameEvent;
import com.moko.support.mk107pro32d.event.DeviceOnlineEvent;
import com.moko.support.mk107pro32d.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class DeviceSettingActivity extends BaseActivity<ActivityDeviceSetting107pro32dBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    public static String TAG = DeviceSettingActivity.class.getSimpleName();
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private Handler mHandler;
    private InputFilter filter;
    private boolean outputSwitch;
    private boolean outControlSwitch;

    @Override
    protected void onCreate() {
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) return "";
            return null;
        };
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
        getOutputSwitch();
        mBind.imgOutputSwitch.setOnClickListener(v -> setOutSwitch(MQTTConstants.CONFIG_MSG_ID_OUTPUT_SWITCH));
        mBind.imgOutControl.setOnClickListener(v -> setOutSwitch(MQTTConstants.CONFIG_MSG_ID_OUT_CONTROL));
    }

    @Override
    protected ActivityDeviceSetting107pro32dBinding getViewBinding() {
        return ActivityDeviceSetting107pro32dBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_REBOOT) {
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
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_RESET) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
                XLog.i("重置设备成功");
                if (TextUtils.isEmpty(appMqttConfig.topicSubscribe)) {
                    // 取消订阅
                    try {
                        MQTTSupport.getInstance().unSubscribe(mMokoDevice.topicPublish);
                        if (mMokoDevice.lwtEnable == 1
                                && !TextUtils.isEmpty(mMokoDevice.lwtTopic)
                                && !mMokoDevice.lwtTopic.equals(mMokoDevice.topicPublish))
                            MQTTSupport.getInstance().unSubscribe(mMokoDevice.lwtTopic);
                    } catch (MqttException e) {
                        XLog.e(e);
                    }
                }
                DBTools.getInstance(getApplicationContext()).deleteDevice(mMokoDevice);
                EventBus.getDefault().post(new DeviceDeletedEvent(mMokoDevice.id));
                mBind.tvName.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    // 跳转首页，刷新数据
                    Intent intent = new Intent(this, MainActivity107Pro32D.class);
                    intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
                    startActivity(intent);
                }, 500);
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_OUTPUT_SWITCH) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            outputSwitch = result.data.get("switch_value").getAsInt() == 1;
            mBind.imgOutputSwitch.setImageResource(outputSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
            getOutControlSwitch();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_OUT_CONTROL) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            outControlSwitch = result.data.get("switch_value").getAsInt() == 1;
            mBind.imgOutControl.setImageResource(outControlSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_OUTPUT_SWITCH || msg_id == MQTTConstants.CONFIG_MSG_ID_OUT_CONTROL) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
                if (msg_id == MQTTConstants.CONFIG_MSG_ID_OUTPUT_SWITCH) {
                    mBind.imgOutputSwitch.setImageResource(outputSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
                } else {
                    mBind.imgOutControl.setImageResource(outControlSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
                }
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    private void getOutputSwitch() {
        int msgId = MQTTConstants.READ_MSG_ID_OUTPUT_SWITCH;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getOutControlSwitch() {
        int msgId = MQTTConstants.READ_MSG_ID_OUT_CONTROL;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setOutSwitch(int msgId) {
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 20 * 1000);
        JsonObject jsonObject = new JsonObject();
        if (msgId == MQTTConstants.CONFIG_MSG_ID_OUTPUT_SWITCH) {
            outputSwitch = !outputSwitch;
            jsonObject.addProperty("switch_value", outputSwitch ? 1 : 0);
        } else if (msgId == MQTTConstants.CONFIG_MSG_ID_OUT_CONTROL) {
            outControlSwitch = !outControlSwitch;
            jsonObject.addProperty("switch_value", outControlSwitch ? 1 : 0);
        }
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
            dismissLoadingProgressDialog();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        MokoDevice device = DBTools.getInstance(getApplicationContext()).selectDevice(mMokoDevice.mac);
        mMokoDevice.name = device.name;
        mBind.tvName.setText(mMokoDevice.name);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }


    public void onEditName(View view) {
        if (isWindowLocked()) return;
        View content = LayoutInflater.from(this).inflate(R.layout.modify_name_107pro32d, null);
        final EditText etDeviceName = content.findViewById(R.id.et_device_name);
        String deviceName = etDeviceName.getText().toString();
        etDeviceName.setText(deviceName);
        etDeviceName.setSelection(deviceName.length());
        etDeviceName.setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(20)});
        CustomDialog dialog = new CustomDialog.Builder(this)
                .setContentView(content)
                .setPositiveButton(R.string.cancel, (dialog1, which) -> dialog1.dismiss())
                .setNegativeButton(R.string.save, (dialog12, which) -> {
                    String name = etDeviceName.getText().toString();
                    if (TextUtils.isEmpty(name)) {
                        ToastUtils.showToast(this, R.string.more_modify_name_tips);
                        return;
                    }
                    mMokoDevice.name = name;
                    DBTools.getInstance(getApplicationContext()).updateDevice(mMokoDevice);
                    EventBus.getDefault().post(new DeviceModifyNameEvent(mMokoDevice.mac));
                    etDeviceName.setText(name);
                    dialog12.dismiss();
                })
                .create();
        dialog.show();
        etDeviceName.postDelayed(() -> showKeyboard(etDeviceName), 300);
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

    public void onIndicatorSettings(View view) {
        start(IndicatorSettingActivity.class);
    }

    public void onNetworkStatusReportInterval(View view) {
        start(NetworkReportIntervalActivity.class);
    }

    public void onReconnectTimeout(View view) {
        start(ReconnectTimeoutActivity.class);
    }

    public void onCommunicationTimeout(View view) {
        start(CommunicationTimeoutActivity.class);
    }

    public void onSystemTime(View view) {
        start(SystemTimeActivity.class);
    }

    public void onButtonReset(View view) {
        start(ButtonResetActivity.class);
    }

    public void onOTA(View view) {
        start(OTAActivity.class);
    }

    public void onModifyMqttSettings(View view) {
        start(ModifySettingsActivity.class);
    }

    public void onDeviceInfo(View view) {
        start(DeviceInfoActivity.class);
    }

    public void onAdvertiseIBeacon(View view) {
        start(AdvertiseIBeaconActivity.class);
    }

    public void onRebootDevice(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Reboot Device");
        dialog.setMessage("Please confirm again whether to \n reboot the device");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            rebootDevice();
        });
        dialog.show(getSupportFragmentManager());
    }

    private void rebootDevice() {
        XLog.i("重启设备");
        int msgId = MQTTConstants.CONFIG_MSG_ID_REBOOT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("reset", 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    public void onResetDevice(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Reset Device");
        dialog.setMessage("After reset,the device will be removed  from the device list,and relevant data will be totally cleared.");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            resetDevice();
        });
        dialog.show(getSupportFragmentManager());
    }

    private void resetDevice() {
        XLog.i("重置设备");
        int msgId = MQTTConstants.CONFIG_MSG_ID_RESET;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("factory_reset", 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    //弹出软键盘
    public void showKeyboard(EditText editText) {
        //其中editText为dialog中的输入框的 EditText
        if (editText != null) {
            //设置可获得焦点
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            //请求获得焦点
            editText.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(editText, 0);
        }
    }
}
