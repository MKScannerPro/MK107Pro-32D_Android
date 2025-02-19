package com.moko.mk107pro32d.fragment;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.moko.mk107pro32d.databinding.FragmentLwt107pro32dBinding;
import com.moko.mk107pro32d.utils.ToastUtils;

public class LWTFragment extends Fragment {
    private final String FILTER_ASCII = "[ -~]*";
    private FragmentLwt107pro32dBinding mBind;
    private boolean lwtEnable;
    private boolean lwtRetain;
    private int qos;
    private String topic;
    private String payload;

    public LWTFragment() {
    }

    public static LWTFragment newInstance() {
        return new LWTFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBind = FragmentLwt107pro32dBinding.inflate(inflater, container, false);
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etLwtTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etLwtPayload.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etLwtTopic.setText(topic);
        mBind.etLwtPayload.setText(payload);
        mBind.cbLwt.setChecked(lwtEnable);
        mBind.cbLwtRetain.setChecked(lwtRetain);
        if (qos == 0) {
            mBind.rbQos1.setChecked(true);
        } else if (qos == 1) {
            mBind.rbQos2.setChecked(true);
        } else if (qos == 2) {
            mBind.rbQos3.setChecked(true);
        }
        return mBind.getRoot();
    }

    public boolean isValid() {
        if (!mBind.cbLwt.isChecked()) return true;
        final String topicStr = mBind.etLwtTopic.getText().toString();
        if (TextUtils.isEmpty(topicStr)) {
            ToastUtils.showToast(getActivity(), "LWT Topic Error");
            return false;
        }
        topic = topicStr;
        final String payloadStr = mBind.etLwtPayload.getText().toString();
        if (TextUtils.isEmpty(payloadStr)) {
            ToastUtils.showToast(getActivity(), "LWT Payload Error");
            return false;
        }
        payload = payloadStr;
        return true;
    }

    public void setQos(int qos) {
        this.qos = qos;
        if (mBind == null) return;
        if (qos == 0) {
            mBind.rbQos1.setChecked(true);
        } else if (qos == 1) {
            mBind.rbQos2.setChecked(true);
        } else if (qos == 2) {
            mBind.rbQos3.setChecked(true);
        }
    }

    public int getQos() {
        int qos = 0;
        if (mBind.rbQos2.isChecked()) {
            qos = 1;
        } else if (mBind.rbQos3.isChecked()) {
            qos = 2;
        }
        return qos;
    }

    public boolean getLwtEnable() {
        return mBind.cbLwt.isChecked();
    }

    public void setLwtEnable(boolean lwtEnable) {
        this.lwtEnable = lwtEnable;
        if (mBind == null) return;
        mBind.cbLwt.setChecked(lwtEnable);
    }

    public boolean getLwtRetain() {
        return mBind.cbLwtRetain.isChecked();
    }

    public void setLwtRetain(boolean lwtRetain) {
        this.lwtRetain = lwtRetain;
        if (mBind == null) return;
        mBind.cbLwtRetain.setChecked(lwtRetain);
    }

    public void setTopic(String topic) {
        this.topic = topic;
        if (mBind == null) return;
        mBind.etLwtTopic.setText(topic);
    }

    public String getTopic() {
        return mBind.etLwtTopic.getText().toString();
    }

    public void setPayload(String payload) {
        this.payload = payload;
        if (mBind == null) return;
        mBind.etLwtPayload.setText(payload);
    }

    public String getPayload() {
        return mBind.etLwtPayload.getText().toString();
    }
}
