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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.telephony.TelephonyManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;

import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;

import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsServiceClass;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsService;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsNotificationController;
import com.mediatek.ims.WfcReasonInfo;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.wfo.DisconnectCause;
import com.mediatek.wfo.IWifiOffloadService;
import com.mediatek.wfo.WifiOffloadManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ImsService extends IImsService.Stub {
    private static final String LOG_TAG = "ImsService";
    private static final boolean DBG = true;
    private static final boolean VDBG = false; // STOPSHIP if true

    private ImsAdapter mImsAdapter = null;
    private ImsRILAdapter  mImsRILAdapter = null;
    private IImsCallSession mPendingMT = null;

    // For synchronization of private variables
    private Object mLockObj = new Object();
    private Context mContext;

    private static IWifiOffloadService sWifiOffloadService = null;
    private IWifiOffloadServiceDeathRecipient mDeathRecipient =
            new IWifiOffloadServiceDeathRecipient();

    private static ImsUtStub sImsUtStub = null;

    private final Handler mHandler;
    private IImsRegistrationListener mListener = null;
    private int mImsRegInfo = ServiceState.STATE_POWER_OFF;
    private int mImsExtInfo = 0;
    private int mServiceId = 0;
    private int mImsState = PhoneConstants.IMS_STATE_DISABLED;
    private int mActivePhoneId = 0;
    private int mRegErrorCode = ImsReasonInfo.CODE_UNSPECIFIED;
    private int mRAN = WifiOffloadManager.RAN_TYPE_MOBILE_3GPP;

    /// M: IMS Configuration
    /// Maintain ImsConfigImpl instance for each phoneId
    private Map<Integer, IImsConfig> mImsConfigInstanceMap =
            new HashMap<Integer, IImsConfig>();

    //***** Event Constants
    private static final int EVENT_IMS_REGISTRATION_INFO = 1;
    protected static final int EVENT_RADIO_NOT_AVAILABLE    = 2;
    protected static final int EVENT_SET_IMS_ENABLED_DONE   = 3;
    protected static final int EVENT_SET_IMS_DISABLE_DONE   = 4;
    protected static final int EVENT_IMS_DISABLED_URC   = 5;
    private static final int EVENT_VIRTUAL_SIM_ON = 6;
    protected static final int EVENT_INCOMING_CALL_INDICATION = 7;
    protected static final int EVENT_CALL_INFO_INDICATION = 8;
    protected static final int EVENT_CALL_RING = 9;
    protected static final int EVENT_IMS_ENABLING_URC   = 10;
    protected static final int EVENT_IMS_ENABLED_URC   = 11;
    protected static final int EVENT_IMS_DISABLING_URC   = 12;
    ///M : WFC @{
    protected static final int EVENT_SIP_CODE_INDICATION = 13;
    /// M: Event for network initiated USSI @{
    protected static final int EVENT_ON_NETWORK_INIT_USSI = 14;
    /// @}

    private static final int IMS_ALLOW_INCOMING_CALL_INDICATION = 0;
    private static final int IMS_DISALLOW_INCOMING_CALL_INDICATION = 1;

    //***** IMS Feature Support
    private static final int IMS_VOICE_OVER_LTE = 1;
    private static final int IMS_RCS_OVER_LTE = 2;
    private static final int IMS_SMS_OVER_LTE = 4;
    private static final int IMS_VIDEO_OVER_LTE = 8;
    private static final int IMS_VOICE_OVER_WIFI = 16;

    //Refer to ImsConfig FeatureConstants
    private static final int IMS_MAX_FEATURE_SUPPORT_SIZE = 4;

    ///M : WFC @{
    private ImsNotificationController mNotificationController = null;
    /// @}

    /** events id definition */
    /// M: Simulate IMS Registration @{
    private boolean mImsRegistry = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            ///M : WFC @{
            if ("ACTION_IMS_SIMULATE".equals(intent.getAction())){
            /// @}
                mImsRegistry = intent.getBooleanExtra("registry", false);
                Rlog.w(LOG_TAG, "Simulate IMS Registration: " + mImsRegistry);
                int[] result = new int[] {
                    (mImsRegistry ? 1 : 0),
                    15,
                    mActivePhoneId};
                AsyncResult ar = new AsyncResult(null, result, null);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_IMS_REGISTRATION_INFO, ar));
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                if (sWifiOffloadService == null){
                    //first use wifioffloadservice and new the object.
                    checkAndBindWifiOffloadService();
                }
                if (sWifiOffloadService != null) {
                    try {
                        sWifiOffloadService.registerForHandoverEvent(
                                createWifiOffloadListenerProxy());
                    } catch (RemoteException e) {
                        Rlog.e(LOG_TAG, "can't register handover event");
                    }
                } else {
                    Rlog.e(LOG_TAG, "can't get WifiOffloadService");
                }
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                String simState = intent.getStringExtra((IccCardConstants.INTENT_KEY_ICC_STATE));
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                int mcc = 0;
                int mnc = 0;
                Rlog.w(LOG_TAG, "ACTION_SIM_STATE_CHANGED on phone" + phoneId);
                IImsConfig instance = null;
                synchronized (mImsConfigInstanceMap) {
                    if (mImsConfigInstanceMap.containsKey(phoneId)) {
                        instance = mImsConfigInstanceMap.get(phoneId);
                    } else {
                        instance = new ImsConfigImpl(context, mImsRILAdapter, phoneId);
                        mImsConfigInstanceMap.put(phoneId, instance);
                    }
                }

                try {
                    boolean volteRes = false;
                    boolean vilteRes = false;
                    boolean wfcRes = false;

                    if (simState.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                        Rlog.w(LOG_TAG, "setImsCapability by default value");
                        // Back to volte only w/o SIM card.
                        instance.setImsCapability(true, false, false);
                    } else if (simState.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
                        if (!isTestSim(phoneId)) {
                            TelephonyManager tm = (TelephonyManager) context
                                    .getSystemService(Context.TELEPHONY_SERVICE);
                            String operator = tm.getSimOperatorNumericForPhone(phoneId);
                            mcc = Integer.parseInt(operator.substring(0, 3));
                            mnc = Integer.parseInt(operator.substring(3));
                            Rlog.w(LOG_TAG, "ACTION_SIM_STATE_CHANGED on mcc: " +
                                    mcc + " mnc: " + mnc);
                            Configuration newConfiguration;
                            Resources res = context.getResources();
                            newConfiguration = res.getConfiguration();
                            newConfiguration.mcc = mcc;
                            newConfiguration.mnc = mnc == 0 ? Configuration.MNC_ZERO : mnc;
                            res.updateConfiguration(newConfiguration, null);

                            volteRes = res.getBoolean(
                                    com.android.internal.R.bool.config_device_volte_available);
                            vilteRes = res.getBoolean(
                                    com.android.internal.R.bool.config_device_vt_available);
                            wfcRes = res.getBoolean(
                                    com.android.internal.R.bool.config_device_wfc_ims_available);
                        } else {
                            // For test SIM cards, forece enable all IMS functions for lab event.
                            Rlog.w(LOG_TAG, "Found test SIM on phone " + phoneId);
                            volteRes = true;
                            vilteRes = true;
                            wfcRes = true;
                        }

                        Rlog.w(LOG_TAG, "Set volte capability is " + volteRes);
                        Rlog.w(LOG_TAG, "Set vilte capability is  " + vilteRes);
                        Rlog.w(LOG_TAG, "Set wfc capability is  " + wfcRes);
                        instance.setImsCapability(volteRes, vilteRes, wfcRes);
                        // Force update IMS feature values after SIM event.
                        ImsManager.updateImsServiceConfig(context, phoneId, true);
                    }
                } catch (RemoteException e) {
                    Rlog.e(LOG_TAG, "SetImsCapability fail: " + e);
                }
            }
        }
    };
    /// @}

    public ImsService(Context context) {
        mImsAdapter = new ImsAdapter(context);

        mImsRILAdapter = new ImsRILAdapter(context);
        Rlog.d(LOG_TAG, " mImsRILAdapter= " + mImsRILAdapter);

        mContext = context;
        mHandler = new MyHandler();

        int mainPhoneId = -1;
        ITelephonyEx telephony = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(mContext.TELEPHONY_SERVICE_EX));

        if (telephony != null) {
            try {
                mainPhoneId = telephony.getMainCapabilityPhoneId();
                Rlog.d(LOG_TAG, "getMainCapabilityPhoneId: mainPhoneId = " + mainPhoneId);
            } catch (RemoteException e) {
                Rlog.d(LOG_TAG, "getMainCapabilityPhoneId: remote exception");
            }
        } else {
            Rlog.d(LOG_TAG, "fail to get ITelephonyEx !!!");
        }

        if (SubscriptionManager.isValidPhoneId(mainPhoneId)) {
            synchronized (this) {
                Rlog.d(LOG_TAG, "Init config interface on main capability " + mainPhoneId);
                ImsConfigImpl instance = new ImsConfigImpl(mContext, mImsRILAdapter, mainPhoneId);
                mImsConfigInstanceMap.put(mainPhoneId, instance);
            }
        }

        mImsRILAdapter.registerForImsRegistrationInfo(mHandler, EVENT_IMS_REGISTRATION_INFO, null);
        mImsRILAdapter.registerForImsEnableStart(mHandler, EVENT_IMS_ENABLING_URC, null);
        mImsRILAdapter.registerForImsEnableComplete(mHandler, EVENT_IMS_ENABLED_URC, null);
        mImsRILAdapter.registerForImsDisableStart(mHandler, EVENT_IMS_DISABLING_URC, null);
        mImsRILAdapter.registerForImsDisableComplete(mHandler, EVENT_IMS_DISABLED_URC, null);
        mImsRILAdapter.setOnIncomingCallIndication(mHandler, EVENT_INCOMING_CALL_INDICATION, null);
        mImsRILAdapter.setOnCallRing(mHandler, EVENT_CALL_RING, null);
        mImsRILAdapter.registerForCallProgressIndicator(mHandler, EVENT_SIP_CODE_INDICATION, null);
        /// register for radio state changed
        mImsRILAdapter.registerForNotAvailable(mHandler, EVENT_RADIO_NOT_AVAILABLE, null);
        /// M: Listen for network initiated USSI @{
        mImsRILAdapter.setOnNetworkInitUSSI(mHandler, EVENT_ON_NETWORK_INIT_USSI, null);
        /// @}

        /// M: Simulate IMS Registration @{
        final IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_IMS_SIMULATE");
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        /// M: Dynamic IMS Switch @{
        if (SystemProperties.get("persist.mtk_dynamic_ims_switch").equals("1")) {
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        }
        /// @}

        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            /* Register for Content Observer */
            // TODO: where to unregister it: in close()??
            registerForWfcPreferenceChange(mHandler);
            /* Send wfc preference info to Modem */
            setWfcProfileInfo();
            mNotificationController = new ImsNotificationController(context, 1);
            Rlog.d(LOG_TAG, "noticontroller created" + mNotificationController);
        }

        context.registerReceiver(mBroadcastReceiver, filter);

        // init flow, call ImsRILAdapter directly instead of function call to make sure
        // turn on/off will pass thru ims state checking mechanism
        if (mainPhoneId >= 0) {
            if (mActivePhoneId != mainPhoneId) {
                mActivePhoneId = mainPhoneId;
            }
            if (SystemProperties.getInt("persist.mtk.volte.enable", 0) == 1 ||
                    SystemProperties.getInt("persist.mtk.wfc.enable", 0) == 1) {
                Rlog.d(LOG_TAG, "turnOnIms, MainCapabilityPhoneId = " + mActivePhoneId);
                mImsRILAdapter.turnOnIms(mHandler.obtainMessage(EVENT_SET_IMS_ENABLED_DONE));
                mImsState = PhoneConstants.IMS_STATE_ENABLING;
            } else {
                Rlog.d(LOG_TAG, "turnOffIms, MainCapabilityPhoneId = " + mActivePhoneId);
                mImsRILAdapter.turnOffIms(mHandler.obtainMessage(EVENT_SET_IMS_DISABLE_DONE));
                mImsState = PhoneConstants.IMS_STATE_DISABLING;
            }
        }
        /// @}
    }

    public void enableImsAdapter() {
        mImsAdapter.enableImsAdapter();
    }

    public void disableImsAdapter(boolean isNormalDisable) {
        mImsAdapter.disableImsAdapter(isNormalDisable);
        mImsState = PhoneConstants.IMS_STATE_DISABLED;
    }

    @Override
    public boolean isConnected(int serviceId, int serviceType, int callType) {
        /* temp solution: always return ImsAdapter if enable */
        return mImsAdapter.getImsAdapterEnable();
    }

    @Override
    public int open(int phoneId, int serviceClass, PendingIntent incomingCallIntent,
            IImsRegistrationListener listener) {

        synchronized(mLockObj) {

            if (mListener != null) {
                Rlog.e(LOG_TAG, "IMS: it did not close IMS servide before open() !!");
            }
            setRegistrationListener(1, listener);
            return 1;
        }
    }

    @Override
    public void close(int serviceId) {

        synchronized(mLockObj) {
            // remove registration listener
            mListener = null;
        }
    }

    @Override
    public boolean isOpened(int serviceId) {
        /* temp solution: always return ImsAdapter if enable */
        return mImsAdapter.getImsAdapterEnable();
    }

    /**
     * Used for turning on IMS when its in OFF state.
     */

    @Override
    public void turnOnIms(int phoneId) {
        Rlog.d(LOG_TAG, "turnOnIms, mActivePhoneId = " + mActivePhoneId +
            " phoneId = " + phoneId);
        phoneId = getMainCapabilityPhoneId();
        Rlog.d(LOG_TAG, "turnOnIms, MainCapabilityPhoneId = " + phoneId);

        if (mActivePhoneId != phoneId) {
            mActivePhoneId = phoneId;
        }

        if (mImsState != PhoneConstants.IMS_STATE_ENABLE) {
            mImsRILAdapter.turnOnIms(mHandler.obtainMessage(EVENT_SET_IMS_ENABLED_DONE));
            mImsState = PhoneConstants.IMS_STATE_ENABLING;
        } else {
            Rlog.d(LOG_TAG, "turnOnIms: Ims already enable and ignore to send AT command.");
        }
    }

    /**
     * Used for turning off IMS when its in ON state.
     * When IMS is OFF, device will behave as CSFB'ed.
     */
    @Override
    public void turnOffIms(int phoneId) {
        Rlog.d(LOG_TAG, "turnOffIms, mActivePhoneId = " + mActivePhoneId +
            " phoneId = " + phoneId);
        phoneId = getMainCapabilityPhoneId();
        Rlog.d(LOG_TAG, "turnOffIms, MainCapabilityPhoneId = " + phoneId);

        if (mActivePhoneId != phoneId) {
            mActivePhoneId = phoneId;
        }
        if (mImsState != PhoneConstants.IMS_STATE_DISABLED) {
            mImsRILAdapter.turnOffIms(mHandler.obtainMessage(EVENT_SET_IMS_DISABLE_DONE));
            mImsState = PhoneConstants.IMS_STATE_DISABLING;
        } else {
            Rlog.d(LOG_TAG, "turnOffIms: Ims already disabled and ignore to send AT command.");
        }
    }

    @Override
    public void setRegistrationListener(int serviceId, IImsRegistrationListener listener) {
        mListener = listener;
        if (mImsRegInfo != ServiceState.STATE_POWER_OFF) {
            notifyRegistrationStateChange(mImsRegInfo);
        }
        if ((mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
            notifyRegistrationCapabilityChange(mImsExtInfo);
        }
    }

    @Override
    public ImsCallProfile createCallProfile(int serviceId, int serviceType, int callType) {
        return new ImsCallProfile(serviceType, callType);
    }

    @Override
    public IImsCallSession createCallSession(int serviceId, ImsCallProfile profile, IImsCallSessionListener listener) {
        // This API is for outgoing call to create IImsCallSession
        return new ImsCallSessionProxy(mContext, profile, listener, this, mHandler, mImsRILAdapter);
    }

    @Override
    public IImsCallSession getPendingCallSession(int serviceId, String callId) {
        // This API is for incoming call to create IImsCallSession
        if (mPendingMT == null) {
            return null;
        }

        IImsCallSession pendingMT = mPendingMT;

        try {
            if (pendingMT.getCallId().equals(callId)) {
                mPendingMT = null;
                return pendingMT;
            }
        } catch (RemoteException e) {
            // error handling. Currently no-op
        }

        return null;
    }

    /**
     * Ut interface for the supplementary service configuration.
     */
    @Override
    public IImsUt getUtInterface(int serviceId) {
        if (sImsUtStub == null) {
            sImsUtStub = new ImsUtStub(mContext);
        }
        return sImsUtStub;
    }

    /**
     * Config interface to get/set IMS service/capability parameters.
     */
    @Override
    public IImsConfig getConfigInterface(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            Rlog.w(LOG_TAG, "Invalid phoneId " + phoneId + " to get config interface");
            phoneId = getMainCapabilityPhoneId();
            Rlog.w(LOG_TAG, "Get config interface on main capability phone " + phoneId);
        }

        IImsConfig instance = null;
        if (mImsConfigInstanceMap.containsKey(phoneId)) {
            instance = mImsConfigInstanceMap.get(phoneId);
        } else {
            instance = new ImsConfigImpl(mContext, mImsRILAdapter, phoneId);
            mImsConfigInstanceMap.put(phoneId, instance);
        }
        return instance;
    }

    /**
     * ECBM interface for Emergency Callback mode mechanism.
     */
    @Override
    public IImsEcbm getEcbmInterface(int serviceId) {
        /* leave blank */
        return new ImsEcbmProxy();
    }

    /**
      * Used to set current TTY Mode.
      */
    @Override
    public void setUiTTYMode(int serviceId, int uiTtyMode, Message onComplete) {
        return;
    }

    /**
     *call interface for allowing/refusing the incoming call indication send to App.
     *@hide
     */
    public void setCallIndication(String callId, String callNum, int seqNum, boolean isAllow) {
        /* leave blank */
        if (isAllow) {
            ImsCallProfile imsCallProfile = new ImsCallProfile();
            if ((callNum != null) && (!callNum.equals(""))) {
                imsCallProfile.setCallExtra(ImsCallProfile.EXTRA_OI, callNum);
            }

            if (mPendingMT != null) {
                try {
                    mPendingMT.close();
                } catch (RemoteException e) {
                    // error handling. Currently no-op
                    Rlog.e(LOG_TAG, "setCallIndication: can't close pending MT");
                }
            }
            mPendingMT = new ImsCallSessionProxy(mContext, imsCallProfile,
                    null, ImsService.this, mHandler, mImsRILAdapter, callId);
            mImsRILAdapter.setCallIndication(IMS_ALLOW_INCOMING_CALL_INDICATION,
                    Integer.parseInt(callId), seqNum);
        } else {
            mImsRILAdapter.setCallIndication(IMS_DISALLOW_INCOMING_CALL_INDICATION,
                    Integer.parseInt(callId), seqNum);
        }
    }

    /**
     * Use to query ims enable/disable status.
     *@return ims status
     *@hide
     */
    public int getImsState() {
        return mImsState;
    }

    /**
     * Use to query ims registration information.
     *@return true if the ims is registered or false if the ims is unregistered.
     *@hide
     */
    public boolean getImsRegInfo(int phoneId) {
        if (phoneId != mActivePhoneId) {
            Rlog.d(LOG_TAG, "IMS: getImsRegInfo() phoneId = " + phoneId +
                " mActivePhoneId = " + mActivePhoneId);
            return false;
        }

        if (mImsRegInfo == ServiceState.STATE_IN_SERVICE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Use to query ims registration extension information.
     *@return string ims extension information.
     *@hide
     */
    public String getImsExtInfo() {
        return Integer.toHexString(mImsExtInfo);
    }

    /**
     * Use to query ims service state .
     *@return mImsRegInfo for service state information.
     *@hide
     */
    public int getImsServiceState() {
        return mImsRegInfo;
    }

    /**
     * Use to hang up all calls .
     *@hide
     */
    public void hangupAllCall() {
        mImsRILAdapter.hangupAllCall(null);
    }

    /**
     *sned the incoming call intent  to ImsPhoneCallTracker.
     *@hide
     */
    private void sendIncomingCallIndication(AsyncResult ar) {
        // +EAIC:<call_id>,<number>,<type>,<call_mode>,<seq_no>
        String callId = ((String[]) ar.result)[0];
        String dialString = ((String[]) ar.result)[1];
        String callMode = ((String[]) ar.result)[3];
        String seqNum = ((String[]) ar.result)[4];
        int serviceId = 1;

        Rlog.d(LOG_TAG, "IMS: sendIncomingCallIndication() call_id = " + callId +
                " dialString = " +  dialString + " seqNum = " + seqNum);

        Intent intent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL_INDICATION);
        intent.putExtra(ImsManager.EXTRA_CALL_ID, callId);
        intent.putExtra(ImsManager.EXTRA_DIAL_STRING, dialString);
        intent.putExtra(ImsManager.EXTRA_CALL_MODE, Integer.parseInt(callMode));
        intent.putExtra(ImsManager.EXTRA_SEQ_NUM, Integer.parseInt(seqNum));
        intent.putExtra(ImsManager.EXTRA_SERVICE_ID, serviceId);
        mContext.sendBroadcast(intent);
    }

    /**
     *create wifiOffloadListnerProxy.
     *@return return wifiOffloadLisetnerProxy
     *@hide
     */
    private IWifiOffloadListenerProxy createWifiOffloadListenerProxy() {
        IWifiOffloadListenerProxy proxy =
                new IWifiOffloadListenerProxy();
        return proxy;
    }

    /**
     *transfer AP wifi call mode to MD wifi call mode.
     *@param wfcMode which is used in AP side.
     *@return return rilWfcMode which is used in MD side.
     *@hide
     */
    private int mapToMDWfcProfile(int wfcMode) {

        int rilWfcMode = ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED;

        switch (wfcMode) {
            case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                rilWfcMode = 3;
                break;
            case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                rilWfcMode = 2;
                break;
            case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                rilWfcMode = 1;
                break;
            default:
                break;
        }
        return rilWfcMode;
    }

    /**
     *notify MD wifi profile is changed.
     *@hide
     */
    private void setWfcProfileInfo() {
        int wfcMode = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                android.provider.Settings.Global.WFC_IMS_MODE,
                ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED);
        int rilWfcMode = mapToMDWfcProfile(wfcMode);
        mImsRILAdapter.sendWfcProfileInfo(rilWfcMode);
    }

    /**
     *transfer sip error cause to wfc specified error cause
     *@param sipErrorCode sip error code.
     *@param sipMethod sip operration. (0:REG, 9:SUBSCRIBE)
     *@return return wfcRegErrorCode which is used in AP side.
     *@hide
     */
    private int mapToWfcRegErrorCause(int sipErrorCode, int sipMethod) {

        int wfcRegErrorCode = WfcReasonInfo.CODE_UNSPECIFIED;

        switch (sipErrorCode) {
            case 40301:
                wfcRegErrorCode = WfcReasonInfo.CODE_WFC_403_UNKNOWN_USER;
                break;
            case 40302:
                wfcRegErrorCode = WfcReasonInfo.CODE_WFC_403_ROAMING_NOT_ALLOWED;
                break;
            case 40303:
                if (sipMethod == 9) {
                    wfcRegErrorCode = WfcReasonInfo.CODE_WFC_911_MISSING;
                } else {
                    wfcRegErrorCode = WfcReasonInfo.CODE_WFC_403_MISMATCH_IDENTITIES;
                }
                break;
            case 40304:
                wfcRegErrorCode = WfcReasonInfo.CODE_WFC_403_AUTH_SCHEME_UNSUPPORTED;
                break;
            case 40305:
                wfcRegErrorCode = WfcReasonInfo.CODE_WFC_403_HANDSET_BLACKLISTED;
                break;
            case 500:
                wfcRegErrorCode = WfcReasonInfo.CODE_WFC_INTERNAL_SERVER_ERROR;
                break;
            default:
                break;
        }
        return wfcRegErrorCode;
    }

    /**
     * Adapter class for {@link IWifiOffloadListener}.
     */
    private class IWifiOffloadListenerProxy extends WifiOffloadManager.Listener {

        @Override
        public void onHandover(int stage, int ratType) {
            if (DBG) {
                Rlog.d(LOG_TAG,"onHandover");
            }

            mRAN = ratType;

            if ((stage == WifiOffloadManager.HANDOVER_END &&
                    mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
                notifyRegistrationCapabilityChange(mImsExtInfo);
            }
        }
    }

    public ImsRILAdapter getImsRILAdapter() {
        if (mImsRILAdapter == null) {
            Rlog.d(LOG_TAG, "IMS: getImsRILAdapter, mImsRILAdapter is null ");
        }

        return mImsRILAdapter;
    }


   /**
    * to get main capability phone id.
    *
    * @return The phone id with highest capability.
    */
   private int getMainCapabilityPhoneId() {

       ITelephonyEx telephony = ITelephonyEx.Stub.asInterface(
               ServiceManager.getService(mContext.TELEPHONY_SERVICE_EX));

       if (telephony != null) {
           try {
               int mainPhoneId = telephony.getMainCapabilityPhoneId();
               Rlog.d(LOG_TAG, "getMainCapabilityPhoneId: mainPhoneId = " + mainPhoneId);
               return mainPhoneId;
           } catch (RemoteException e) {
               Rlog.d(LOG_TAG, "getMainCapabilityPhoneId: remote exception");
               return 0;
           }
       } else {
           Rlog.d(LOG_TAG, "fail to get ITelephonyEx !!!");
           return 0;
       }
   }

    /**
     * Death recipient class for monitoring IMS service.
     *
     * @param phoneId  to indicate which phone.
     */
    private void checkAndBindWifiOffloadService() {
        IBinder b = ServiceManager.getService(WifiOffloadManager.WFO_SERVICE);

        if (b != null) {
            try {
                b.linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
            }
        }

        sWifiOffloadService = IWifiOffloadService.Stub.asInterface(b);
        Rlog.d(LOG_TAG, "checkAndBindWifiOffloadService: sWifiOffloadService = " +
                sWifiOffloadService);
    }

    /**
     * Death recipient class for monitoring WifiOffload service.
     */
    private class IWifiOffloadServiceDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            sWifiOffloadService = null;
        }
    }


    /**
     *notify upper application ims registration information.
     *@param imsRegInfo   the registration inforamtion.
     *@hide
     */
    private void notifyRegistrationStateChange(int imsRegInfo) {

        synchronized(mLockObj) {

            if (mListener == null) {
                return;
            }

            if (DBG) {
                Rlog.d(LOG_TAG, "IMS: notifyRegistrationStateChange imsRegInfo= " + imsRegInfo);
            }

            if (imsRegInfo == ServiceState.STATE_IN_SERVICE) {
                try {
                    mListener.registrationConnected();
                    if (sWifiOffloadService != null) {
                        mRAN = sWifiOffloadService.getRatType();
                    }
                    mRegErrorCode = ImsReasonInfo.CODE_UNSPECIFIED;
                } catch (RemoteException e) {
                    // error handling. Currently no-op
                    Rlog.e(LOG_TAG, "IMS: notifyStateChange fail on access WifiOffloadService");
                }
            } else {
                try {
                    ImsReasonInfo imsReasonInfo = null;

                    imsReasonInfo= new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                            mRegErrorCode,Integer.toString(mRegErrorCode));

                    mListener.registrationDisconnected(imsReasonInfo);
                } catch (RemoteException e) {
                    // error handling. Currently no-op
                }
            }
        }
    }

    /**
     *notify upper application ims feature capability information.
     *@param imsExtInfo   the ims feature capability inforamtion.
     *@hide
     */
    private void notifyRegistrationCapabilityChange(int imsExtInfo) {

        if (mListener == null) {
            return;
        }

        int[] enabledFeatures = new int[IMS_MAX_FEATURE_SUPPORT_SIZE];
        int[] disabledFeatures = new int[IMS_MAX_FEATURE_SUPPORT_SIZE];

        for (int i = 0; i < IMS_MAX_FEATURE_SUPPORT_SIZE; i++) {
            enabledFeatures[i] = -1;
            disabledFeatures[i] = -1;
        }

        if (mRAN != WifiOffloadManager.RAN_TYPE_WIFI &&
                (imsExtInfo & IMS_VOICE_OVER_LTE) == IMS_VOICE_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE;
        }

        if (mRAN != WifiOffloadManager.RAN_TYPE_WIFI &&
                (imsExtInfo & IMS_VIDEO_OVER_LTE) == IMS_VIDEO_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE;
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE;
        }
        /// WFC @{
        if (mRAN == WifiOffloadManager.RAN_TYPE_WIFI &&
                (imsExtInfo & IMS_VOICE_OVER_LTE) == IMS_VOICE_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
            Rlog.d(LOG_TAG, "[WFC]IMS_VOICE_OVER_WIFI");
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI;
        }

        if (mRAN == WifiOffloadManager.RAN_TYPE_WIFI &&
                (imsExtInfo & IMS_VIDEO_OVER_LTE) == IMS_VIDEO_OVER_LTE) {
            enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI;
            Rlog.d(LOG_TAG, "[WFC]IMS_VIDEO_OVER_WIFI");
        } else {
            disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI] =
                    ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI;
        }
        /// @}

        synchronized(mLockObj) {
            try {
                if (mListener != null){
                    mListener.registrationFeatureCapabilityChanged(ImsServiceClass.MMTEL,
                            enabledFeatures, disabledFeatures);
                }
            } catch (RemoteException e) {
                // error handling. Currently no-op
            }
        }
    }

    /*
    * Observes WFC preference settings changes .
    */
    private void registerForWfcPreferenceChange(Handler handler) {
        ContentObserver contentObserver = new ContentObserver(handler) {

            @Override
            public void onChange(boolean selfChange) {
                this.onChange(selfChange, Settings.System.getUriFor(Settings.Global.WFC_IMS_MODE));
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                Uri i = android.provider.Settings.Global
                        .getUriFor(android.provider.Settings.Global.WFC_IMS_MODE);
                int wfc_preference = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.Global.WFC_IMS_MODE,
                        ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED);
                int ril_wfc_preference = mapToMDWfcProfile(wfc_preference);
                if (DBG) Rlog.d(LOG_TAG, "uri:" + uri + ", db_uri:" + i);
                if (DBG) Rlog.d(LOG_TAG, "wfc_preference:" + wfc_preference);
                if (i != null && i.equals(uri)) {
                    /* send change to modem */
                    mImsRILAdapter.sendWfcProfileInfo(ril_wfc_preference);
                }
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.Global.WFC_IMS_MODE),
                false, contentObserver);
    }

    /**
     *Ims service Message hanadler.
     *@hide
     */
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            Intent intent;
            int phoneId = getMainCapabilityPhoneId();
            switch (msg.what) {
                case EVENT_IMS_REGISTRATION_INFO:
                    if (DBG) Rlog.d(LOG_TAG, "receive EVENT_IMS_REGISTRATION_INFO");

                    /**
                     * According to 3GPP TS 27.007 +CIREGU format
                     *
                     * AsyncResult.result is an Object[]
                     * ((Object[])AsyncResult.result)[0] is integer type to indicate the IMS regiration status.
                     *                                    0: not registered
                     *                                    1: registered
                     * ((Object[])AsyncResult.result)[1] is numeric value in hexadecimal format to indicate the IMS capability.
                     *                                    1: RTP-based transfer of voice according to MMTEL (see 3GPP TS 24.173 [87])
                     *                                    2: RTP-based transfer of text according to MMTEL (see 3GPP TS 24.173 [87])
                     *                                    4: SMS using IMS functionality (see 3GPP TS 24.341[101])
                     *                                    8: RTP-based transfer of video according to MMTEL (see 3GPP TS 24.183 [87])
                     *
                     */
                    ar = (AsyncResult) msg.obj;
                    /// M: Fix DSDS bug
                    /// Do not notify AP when socket_id from RILD is not equal the active phone id. @{
                    int socketId = ((int[]) ar.result)[2];
                    if (socketId != mActivePhoneId) {
                        Rlog.d(LOG_TAG, "IMS: drop IMS reg info, socketId = " + socketId +
                            " mActivePhoneId = " + mActivePhoneId);
                        break;
                    }
                    /// @}

                    int newImsRegInfo = ServiceState.STATE_POWER_OFF;
                    if (((int[]) ar.result)[0] == 1) {
                        newImsRegInfo = ServiceState.STATE_IN_SERVICE;
                    } else {
                        newImsRegInfo = ServiceState.STATE_OUT_OF_SERVICE;
                    }
                    /// M: Simulate IMS Registration @{
                    if (SystemProperties.getInt("persist.ims.simulate", 0) == 1) {
                        newImsRegInfo = (mImsRegistry ?
                                ServiceState.STATE_IN_SERVICE : ServiceState.STATE_OUT_OF_SERVICE);
                        Rlog.d(LOG_TAG, "Override EVENT_IMS_REGISTRATION_INFO: newImsRegInfo=" +
                                newImsRegInfo);
                    }
                    /// @}
                    int newImsExtInfo = ((int[]) ar.result)[1];

                    /* notify upper application the IMS registration status is chagned */
                    if (DBG) {
                        Rlog.d(LOG_TAG, "newReg:" + newImsRegInfo + " oldReg:" + mImsRegInfo);
                    }

                    mImsRegInfo = newImsRegInfo;
                    notifyRegistrationStateChange(mImsRegInfo);

                    /* notify upper application the IMS capability is chagned when IMS is registered */
                    if (DBG) {
                        Rlog.d(LOG_TAG, "newRegExt:" + newImsExtInfo + "oldRegExt:" + mImsExtInfo);
                    }

                    if ((mImsRegInfo == ServiceState.STATE_IN_SERVICE)) {
                        mImsExtInfo = newImsExtInfo;
                    } else {
                        mImsExtInfo = 0;
                    }
                    notifyRegistrationCapabilityChange(mImsExtInfo);
                    break;
                case EVENT_IMS_ENABLING_URC:
                    //+EIMS: 1
                    //Since MD doens't send IMS_ENABLED_URC to AP we should handle initial here.
                    if (DBG) {
                        Rlog.d(LOG_TAG, "EVENT_IMS_ENABLING_URC: mActivePhoneId = " + mActivePhoneId +
                            " phoneId = " + phoneId);
                    }

                    if (mActivePhoneId != phoneId) {
                        mActivePhoneId = phoneId;
                    }
                    // notify AP Ims Service is up
                    intent = new Intent(ImsManager.ACTION_IMS_SERVICE_UP);
                    intent.putExtra(ImsManager.EXTRA_PHONE_ID, mActivePhoneId);
                    mContext.sendBroadcast(intent);
                    // enable ImsAdapter
                    enableImsAdapter();
                    mImsState = PhoneConstants.IMS_STATE_ENABLE;
                    if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
                        setWfcProfileInfo();
                    }
                    break;
                case EVENT_IMS_ENABLED_URC:
                    //+EIMCFLAG: 1
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_IMS_ENABLED_URC");
                    }
                    break;
                case EVENT_IMS_DISABLING_URC:
                    //+EIMS: 0
                    if (DBG) {
                        Rlog.d(LOG_TAG, "EVENT_IMS_DISABLING_URC: mActivePhoneId = " + mActivePhoneId +
                            " phoneId = " + phoneId);
                    }

                    if (mActivePhoneId != phoneId) {
                        mActivePhoneId = phoneId;
                    }
                    break;
                case EVENT_IMS_DISABLED_URC:
                    //+EIMCFLAG: 0
                    if (DBG) Rlog.d(LOG_TAG, "receive EVENT_IMS_DISABLED_URC");
                    Rlog.d(LOG_TAG, "IMS: phoneId = " + phoneId);
                    disableIms(true);
                    break;
                case EVENT_SET_IMS_ENABLED_DONE:
                    // Only log for tracking set ims enable command response
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_SET_IMS_ENABLED_DONE");
                    }
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) {
                            Rlog.d(LOG_TAG, "turnOnIms failed, return to disabled state!");
                        }
                        disableIms(false);
                    }
                    break;
                case EVENT_SET_IMS_DISABLE_DONE:
                    // Only log for tracking set ims disable command response
                    if (DBG) {
                        Rlog.d(LOG_TAG, "receive EVENT_SET_IMS_DISABLE_DONE");
                    }
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) {
                            Rlog.d(LOG_TAG, "turnOffIms failed, return to disabled state!");
                        }
                        disableIms(false);
                    }
                    break;
                case EVENT_INCOMING_CALL_INDICATION:
                    Rlog.d(LOG_TAG, "receive EVENT_INCOMING_CALL_INDICATION");
                    ar = (AsyncResult) msg.obj;
                    sendIncomingCallIndication(ar);
                    break;
                case EVENT_CALL_RING:
                    Rlog.d(LOG_TAG, "receive EVENT_CALL_RING");
                    break;
                case EVENT_RADIO_NOT_AVAILABLE:
                    Rlog.d(LOG_TAG, "receive EVENT_RADIO_NOT_AVAILABLE");
                    disableIms(false);
                    break;
                case EVENT_SIP_CODE_INDICATION:
                    ar = (AsyncResult) msg.obj;
                    int[] sipMessage = (int[]) ar.result;
                    /* ESIPCPI: <call_id>,<dir>,<SIP_msg_type>,<method>,<response_code> */
                    if (sipMessage != null) {
                        Rlog.d(LOG_TAG, "Method =" + sipMessage[3] + "Reg cause =" + sipMessage[4]);
                        if (sipMessage[3] == 0 ||
                                sipMessage[3] == 9) {
                            if (mRAN == WifiOffloadManager.RAN_TYPE_WIFI) {
                                mRegErrorCode = mapToWfcRegErrorCause(sipMessage[4],sipMessage[3]);
                            }else {
                                mRegErrorCode = sipMessage[4];
                            }
                        }
                    }
                    break;
                /// M: Event for network initiated USSI @{
                case EVENT_ON_NETWORK_INIT_USSI:
                    Rlog.d(LOG_TAG, "receive EVENT_ON_NETWORK_INIT_USSI");
                    ar = (AsyncResult) msg.obj;
                    // +EIUSD: <m>,<n>,<str>,<lang>
                    String[] eiusd = (String[]) ar.result;

                    ImsCallProfile imsCallProfile = createCallProfile(1,
                            ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.CALL_TYPE_VOICE);
                    imsCallProfile.setCallExtraInt(ImsCallProfile.EXTRA_DIALSTRING,
                            ImsCallProfile.DIALSTRING_USSD);
                    imsCallProfile.setCallExtra("m", eiusd[0]);
                    imsCallProfile.setCallExtra("n", eiusd[1]);
                    imsCallProfile.setCallExtra("str", eiusd[2]);

                    mPendingMT = new ImsCallSessionProxy(mContext, imsCallProfile,
                            null, ImsService.this, mHandler, mImsRILAdapter, "1");

                    intent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL);
                    intent.putExtra(ImsManager.EXTRA_USSD, true);
                    intent.putExtra(ImsManager.EXTRA_CALL_ID, "1");
                    intent.putExtra(ImsManager.EXTRA_SERVICE_ID, 1);
                    mContext.sendBroadcast(intent);
                    break;
                /// @}
                default:
                    break;
            }
        }
    }

    @Override
    public int getRegistrationStatus() {
        if (mNotificationController == null) return WfcReasonInfo.CODE_WFC_DEFAULT;
        return mNotificationController.getRegistrationStatus();
    }

    /**
     * Notify AP IMS Service is disabled and disable ImsAdapter.
     *@param isNormalDisable is IMS service disabled normally or abnormally
     *@hide
     */
    private void disableIms(boolean isNormalDisable) {
        mContext.sendBroadcast(new Intent(ImsManager.ACTION_IMS_SERVICE_DOWN));
        disableImsAdapter(isNormalDisable);
    }

    private static boolean isTestSim(int phoneId) {
        boolean isTestSim = false;
        switch (phoneId) {
            case PhoneConstants.SIM_ID_1:
                isTestSim = "1".equals(SystemProperties.get("gsm.sim.ril.testsim", "0"));
                break;
            case PhoneConstants.SIM_ID_2:
                isTestSim = "1".equals(SystemProperties.get("gsm.sim.ril.testsim.2", "0"));
                break;
            case PhoneConstants.SIM_ID_3:
                isTestSim = "1".equals(SystemProperties.get("gsm.sim.ril.testsim.3", "0"));
                break;
            case PhoneConstants.SIM_ID_4:
                isTestSim = "1".equals(SystemProperties.get("gsm.sim.ril.testsim.4", "0"));
                break;
        }
        return isTestSim;
    }

}
