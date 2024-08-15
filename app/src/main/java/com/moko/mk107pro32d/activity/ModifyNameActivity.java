package com.moko.mk107pro32d.activity;

import android.content.Context;
import android.content.Intent;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.moko.mk107pro32d.AppConstants;
import com.moko.mk107pro32d.R;
import com.moko.mk107pro32d.base.BaseActivity;
import com.moko.mk107pro32d.databinding.ActivityModifyDeviceName107pro32dBinding;
import com.moko.mk107pro32d.db.DBTools;
import com.moko.mk107pro32d.entity.MokoDevice;
import com.moko.mk107pro32d.utils.ToastUtils;
import com.moko.support.mk107pro32d.event.MQTTConnectionCompleteEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ModifyNameActivity extends BaseActivity<ActivityModifyDeviceName107pro32dBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    public static String TAG = ModifyNameActivity.class.getSimpleName();
    private MokoDevice device;

    @Override
    protected void onCreate() {
        device = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        assert null != device;
        mBind.etNickName.setText(device.name);
        mBind.etNickName.setSelection(mBind.etNickName.getText().toString().length());
        mBind.etNickName.setFilters(new InputFilter[]{filter, new InputFilter.LengthFilter(20)});
        mBind.etNickName.postDelayed(() -> {
            InputMethodManager inputManager = (InputMethodManager) mBind.etNickName.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(mBind.etNickName, 0);
        }, 300);
    }

    @Override
    protected ActivityModifyDeviceName107pro32dBinding getViewBinding() {
        return ActivityModifyDeviceName107pro32dBinding.inflate(getLayoutInflater());
    }

    public void modifyDone(View view) {
        String name = mBind.etNickName.getText().toString();
        if (TextUtils.isEmpty(name)) {
            ToastUtils.showToast(this, R.string.modify_device_name_empty);
            return;
        }
        device.name = name;
        DBTools.getInstance(getApplicationContext()).updateDevice(device);
        // 跳转首页，刷新数据
        Intent intent = new Intent(this, MainActivity107Pro32D.class);
        intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
        intent.putExtra(AppConstants.EXTRA_KEY_MAC, device.mac);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
    }
}
