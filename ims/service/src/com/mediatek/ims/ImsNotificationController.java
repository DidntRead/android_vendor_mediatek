/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.ims;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.common.MPlugin;
import com.mediatek.common.wfc.IImsNotificationControllerExt;

/** Class to show WFC related notifications like registration & WFC call.
 */
public class ImsNotificationController {

    BroadcastReceiver mBr = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (DBG) Log.d(TAG, "Intent action:" + intent.getAction());

            /* Restore screen lock state, even if intent received may not provide its effect */
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mIsScreenLock = true;
                handleScreenOff();
            } else {
                mIsScreenLock = mKeyguardManager.isKeyguardLocked();
            }
            if (DBG) Log.d(TAG, "on receive:screen lock:" + mIsScreenLock);

            /* ALPS02260621: Need to save phone_type as there is no provision of getting it
                     * without having phone object.
                     */
       /* if (intent.getAction().equals(PhoneConstants.ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int phoneType = intent.getIntExtra(PhoneConstants.PHONE_TYPE_KEY,
                        RILConstants.NO_PHONE);
                if (phoneType == RILConstants.IMS_PHONE) {
                    if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                            || TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                        mPhoneType = RILConstants.IMS_PHONE;
                    } else {
                        mPhoneType = RILConstants.NO_PHONE;
                    }
                }
            }
            if (DBG) {
                Log.d(TAG, "mPhoneType:" + mPhoneType);
            }*/

            if (intent.getAction().equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
                handleImsStateChange(intent);
            } else if (intent.getAction().equals(PhoneConstants
                    .ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED)) {
                /* ALPS02260621: Need to save phone_type as there is no provision of getting it
                  * without having phone object.
                  */
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int phoneType = intent.getIntExtra(PhoneConstants.PHONE_TYPE_KEY,
                        RILConstants.NO_PHONE);
                if (phoneType == RILConstants.IMS_PHONE) {
                    if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                            || TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                        mPhoneType = RILConstants.IMS_PHONE;
                    } else {
                        mPhoneType = RILConstants.NO_PHONE;
                    }
                }
                handleCallIntent(state, phoneType);
            } else if (intent.getAction().equals(ImsManager.ACTION_IMS_SERVICE_DOWN)) {
                removeWfcNotification();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                handleScreenOn();
            } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                handleScreenUnlock();
            }
            Log.d(TAG, "mPhoneType:" + mPhoneType);
        }
    };

    private static final boolean DBG = true;
    private static final String TAG = "ImsNotificationController";
    private static final String ACTION_LAUNCH_WFC_SETTINGS
            = "android.settings.WIFI_CALLING_SETTINGS";

    /**
    * Wfc registration notification ID. This is
     * the ID of the Notification given to the NotificationManager.
     * Note: Id should be unique within APP.
     */
    private static final int WFC_NOTIFICATION = 0x10;

    private static final int WFC_REGISTERED_ICON =
            com.mediatek.internal.R.drawable.wfc_notify_registration_success;
    private static final int WFC_CALL_ICON =
            com.mediatek.internal.R.drawable.wfc_notify_ongoing_call;

    private static final int WFC_REGISTERED_TITLE =
            com.mediatek.internal.R.string.success_notification_title;
    private static final int WFC_CALL_TITLE =
            com.mediatek.internal.R.string.ongoing_call_notification_title;

    private static final int WFC_REGISTERED_SUMMARY =
            com.mediatek.internal.R.string.success_notification_summary;

    // Current WFC state.
    // Can be: 1) Success: WFC registered (2) DEFAULT: WFC on but not registered
    // (3) Various error codes: defined in WfcReasonInfo
    private int mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;

    private boolean mWfcCapabilityPresent = false;
    private boolean mWfcCallOngoing = false;
    private boolean mIsScreenLock = false;

    /*  Vars required for ImsNotificationController initialization */
    private Context mContext;
    private long mSubId;
    private int mPhoneType = RILConstants.NO_PHONE;

    private NotificationManager mNotificationManager;
    private KeyguardManager mKeyguardManager;

    /* IMSN Plugin */
    IImsNotificationControllerExt mImsnExt;

    /** Constructor.
     * @param context context
     * @param subId subId
     */
    public ImsNotificationController(Context context, long subId) {
        if (DBG) {
            Log.d(TAG, "in constructor: subId:" + subId);
        }
        mContext = context;
        mSubId = subId;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        mIsScreenLock =  mKeyguardManager.isKeyguardLocked();
         /* IMSN plugin part */
        mImsnExt = getIMSNPlugin(context);

        registerReceiver();
    }

    /** Stop the Imsnotification controller.
     */
    public void stop() {
        if (DBG) Log.d(TAG, "in destroy Instance");
        unRegisterReceiver();
        /* Cancel visible notifications, if any */
        mNotificationManager.cancelAll();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(ImsManager.ACTION_IMS_STATE_CHANGED);
        filter.addAction(PhoneConstants.ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED);
        filter.addAction(ImsManager.ACTION_IMS_SERVICE_DOWN);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(mBr, filter);
        if (mImsnExt != null) {
            mImsnExt.register(mContext);
        }
    }

    private void unRegisterReceiver() {
        mContext.unregisterReceiver(mBr);
        if (mImsnExt != null) {
            mImsnExt.unRegister(mContext);
        }
    }

    private void handleCallIntent(String state, int phoneType) {
        //String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        //int phoneType = intent.getIntExtra(PhoneConstants.PHONE_TYPE_KEY, RILConstants.NO_PHONE);
        if (DBG) Log.d(TAG, "in handleCallIntent, phone state:" + state);
        if (DBG) Log.d(TAG, "in handleCallIntent, phone type:" + phoneType);
        if (phoneType == RILConstants.IMS_PHONE) {
            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)
                    || TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                mWfcCallOngoing = true;
                displayWfcCallNotification();
            } else {
                mWfcCallOngoing = false;
                displayWfcRegistrationNotification(false);
            }
        } else if (phoneType == RILConstants.GSM_PHONE && mWfcCallOngoing) {
            mWfcCallOngoing = false;
            displayWfcRegistrationNotification(false);
        }
    }

    private void handleImsStateChange(Intent intent) {
        if (intent.getAction().equals(ImsManager.ACTION_IMS_STATE_CHANGED)) {
            mImsState = intent.getIntExtra(ImsManager.EXTRA_IMS_REG_STATE_KEY,
                    ServiceState.STATE_OUT_OF_SERVICE);
            if (DBG) Log.d(TAG, "in handleImsStateChange, serviceState:" + mImsState);
            if (mImsState != ServiceState.STATE_IN_SERVICE) {
                removeWfcNotification();
            } else {
                handleInStateService(intent);
            }
        }
        if (DBG) Log.d(TAG, "exit handleImsStateChange, imsState:" + mImsState);
    }

    private void handleInStateService(Intent intent) {
        if (DBG) Log.d(TAG, "in handleInStateService");
        /*handle for registration icon*/
        boolean[] enabledFeatures = intent
                .getBooleanArrayExtra(ImsManager.EXTRA_IMS_ENABLE_CAP_KEY);
        if (DBG) Log.d(TAG, "wifi capability:" + enabledFeatures[ImsConfig.FeatureConstants
                .FEATURE_TYPE_VOICE_OVER_WIFI]);
        if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] == true) {
            mWfcCapabilityPresent = true;
            /* Capabilities have been change to WIFI, so set wfc status as Success.
             * It is done to cover handover cases in which IMS_STATE_CHANGE is not
             * received before capability_change intent
            */
            mImsState = WfcReasonInfo.CODE_WFC_SUCCESS;
            /* ALPS02187200: Query phone state to check whether UE is in Call
             * when capability change to Wifi.This case can happen during handover from
             * LTE to Wifi when call is ongoing.
             */
            TelephonyManager tm = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            // TODO: for multiSim
            /* ALPS02260621: check phone_type before showing call icon*/
            if ((tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK
                            || tm.getCallState() == TelephonyManager.CALL_STATE_RINGING)
                        && mPhoneType == RILConstants.IMS_PHONE) {
                mWfcCallOngoing = true;
                displayWfcCallNotification();
            } else {
                displayWfcRegistrationNotification(true);
            }
        } else {
            mWfcCapabilityPresent = false;
            /* Capabilities have been change to other than WIFI, so set wfc status as OFF */
            mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
            removeWfcNotification();
        }
    }



    /* Listening screen off intent because no intent for screen lock present in SDK now
     * So, treating screen Off as screen lock
     * Remove notification, if screen off
     */
    private void handleScreenOff() {
        mNotificationManager.cancel(WFC_NOTIFICATION);
    }

    /* Screen on but check if screen is locked or not. If unlocked, show notification. */
    private void handleScreenOn() {
        if (!mIsScreenLock) {
            if (DBG) Log.d(TAG, "screen not locked & screen on, show notification");
            showNotification();
        }
    }

    /* Intent received when user unlocks. Show notification. */
    private void handleScreenUnlock() {
        showNotification();
    }

    private void showNotification() {
        if (mWfcCallOngoing) {
            displayWfcCallNotification();
        } else if (mWfcCapabilityPresent) {
            displayWfcRegistrationNotification(false);
        }
    }

    private void displayWfcCallNotification() {
        if (!isWfcNotificationSupportOn()) {
            Log.d(TAG, "WFC Notification not supported");
            return;
        }
        if (DBG) Log.d(TAG, "in call handling, screen lock:" + mIsScreenLock);
        if (!mIsScreenLock && mImsState == WfcReasonInfo.CODE_WFC_SUCCESS
                    && mWfcCapabilityPresent) {
            // TODO: to handle fake SRVCC case(wfc registered but during call setup it goes on CS).
            //Need RAT type of call setup
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(WFC_CALL_TITLE))
                    .setSmallIcon(WFC_CALL_ICON)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build();
            mNotificationManager.notify(WFC_NOTIFICATION, noti);
            if (DBG) Log.d(TAG, "showing wfc call notification");
        }
    }

    private void displayWfcRegistrationNotification(boolean showTicker) {
        if (!isWfcNotificationSupportOn()) {
            Log.d(TAG, "WFC Notification not supported");
            return;
        }
        if (DBG) Log.d(TAG, "in registration handling, screen lock:" + mIsScreenLock);
        if (!mIsScreenLock && mImsState == WfcReasonInfo.CODE_WFC_SUCCESS && mWfcCapabilityPresent
            && mWfcCallOngoing == false) {
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(WFC_REGISTERED_TITLE))
                    .setContentText(mContext.getResources().getString(WFC_REGISTERED_SUMMARY))
                    .setSmallIcon(WFC_REGISTERED_ICON)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .build();
            if (showTicker) {
                noti.tickerText = mContext.getResources().getString(WFC_REGISTERED_TITLE);
            }
            Intent intent = new Intent(ACTION_LAUNCH_WFC_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (mImsnExt != null) {
                intent = mImsnExt.getIntent(IImsNotificationControllerExt.REGISTRATION, intent);
            }
            noti.contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
            noti.flags |= Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(WFC_NOTIFICATION, noti);
            if (DBG) Log.d(TAG, "showing wfc registration notification");
        }
    }


    private void removeWfcNotification() {
        if (DBG) Log.d(TAG, "removing wfc notification, if any");
        mNotificationManager.cancel(WFC_NOTIFICATION);
        mImsState = WfcReasonInfo.CODE_WFC_DEFAULT;
        mWfcCapabilityPresent = false;
        mWfcCallOngoing = false;
    }

    public int getRegistrationStatus() {
        return mImsState;
    }

    private IImsNotificationControllerExt getIMSNPlugin(Context context) {
        IImsNotificationControllerExt ext;
        ext = (IImsNotificationControllerExt) MPlugin.createInstance(
                IImsNotificationControllerExt.class.getName(), context);
        Log.d(TAG, "IMSN plugin:" + ext);
        return ext;
    }

    private boolean isWfcNotificationSupportOn() {
        return !(SystemProperties.get("persist.radio.multisim.config", "ss").equals("dsds"));
    }
}

