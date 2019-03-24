package com.mediatek.ims.config.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.ims.config.op.PlmnTable;

public class ImsConfigReceiver extends BroadcastReceiver {
    private static final String TAG = "ImsConfig";
    private Handler mHandler;
    private final int mPhoneId;

    public ImsConfigReceiver(Handler handler, int phoneId) {
        super();
        mPhoneId = phoneId;
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, SubscriptionManager.INVALID_PHONE_INDEX);
        if (phoneId == mPhoneId) {
            switch (state) {
                case IccCardConstants.INTENT_VALUE_ICC_ABSENT:
                case IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR:
                case IccCardConstants.INTENT_VALUE_ICC_UNKNOWN:
                case IccCardConstants.INTENT_VALUE_ICC_LOCKED:
                    break;
                case IccCardConstants.INTENT_VALUE_ICC_LOADED:
                    String simOperator = tm.getSimOperatorNumericForPhone(phoneId);
                    if (!TextUtils.isEmpty(simOperator)) {
                        String code = PlmnTable.getOperatorCode(Integer.valueOf(simOperator));
                        // Remove previous load event
                        mHandler.removeMessages(ImsConfigStorage.MSG_LOAD_CONFIG_STORAGE);
                        Message msg = new Message();
                        Log.d(TAG, "Sim state changed, event = " + state + ", simOperator = " +
                                simOperator + ", code = " + code + " msg = " +
                                msg.hashCode() + " on phone " + phoneId);
                        msg.what = ImsConfigStorage.MSG_LOAD_CONFIG_STORAGE;
                        msg.obj = code;
                        mHandler.sendMessage(msg);
                    } else {
                        Log.e(TAG, "Empty IMSI, should never happended!");
                    }
                    break;
            }
        }
    }
}
