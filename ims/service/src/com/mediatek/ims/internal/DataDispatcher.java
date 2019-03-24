
package com.mediatek.ims.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.mediatek.ims.ImsAdapter;
import com.mediatek.ims.ImsAdapter.VaSocketIO;
import com.mediatek.ims.ImsAdapter.VaEvent;
import com.mediatek.ims.ImsEventDispatcher;
import com.mediatek.ims.VaConstants;

import java.util.Arrays;
import java.util.HashMap;

import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkRequest.Builder;
import android.net.Uri;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.PreciseDataConnectionState;
import android.text.TextUtils;

public class DataDispatcher implements ImsEventDispatcher.VaEventDispatcher {
    private static final String TAG = DataDispatcherUtil.TAG;
    private static final boolean DUMP_TRANSACTION = true;
    // private static final boolean DBG = true;

    private static DataDispatcher mInstance;
    private Context mContext;
    private boolean mIsEnable;
    private VaSocketIO mSocket;

    private static final int MSG_ON_NOTIFY_DATA_CONNECTED = 7000;
    private static final int MSG_ON_NOTIFY_DATA_DISCONNECTED = 7100;
    private static final int MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT = 7200;
    private static final int MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT = 7300;

    private static final int MAX_NETWORK_ACTIVE_TIMEOUT_MS = 10000;
    private static final int MAX_NETWORK_DEACTIVE_TIMEOUT_MS = 20000;
    private static final int WAITING_FRAMEWORK_STATUS_SYNC = 5000;

    private static final String IMS_INTERFACE_NAME = "ccmni4";
    private static final String EMERGENCY_INTERFACE_NAME = "ccmni5";

    private boolean mSimStatus[];

    DataDispatcherNetworkRequest[] mDataNetworkRequests;
    private static final int[] APN_CAP_LIST = new int[] {
            NetworkCapabilities.NET_CAPABILITY_IMS,
            NetworkCapabilities.NET_CAPABILITY_EIMS
    };

    private HashMap<Integer, TransactionParam> mTransactions = new HashMap<Integer, TransactionParam>();
    private DataDispatcherUtil mDataDispatcherUtil;

    private static final int FAILCAUSE_NONE = 0;
    private static final int FAILCAUSE_UNKNOWN = 65536;

    private HashMap<String, Integer> failCauses = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 1L;
        {
            put("", 0);
            put("OPERATOR_BARRED", 0x08);
            put("MBMS_CAPABILITIES_INSUFFICIENT", 0x18);
            put("LLC_SNDCP_FAILURE", 0x19);
            put("INSUFFICIENT_RESOURCES", 0x1A);
            put("MISSING_UNKNOWN_APN", 0x1B);
            put("UNKNOWN_PDP_ADDRESS_TYPE", 0x1C);
            put("USER_AUTHENTICATION", 0x1D);
            put("ACTIVATION_REJECT_GGSN", 0x1E);
            put("ACTIVATION_REJECT_UNSPECIFIED", 0x1F);
            put("SERVICE_OPTION_NOT_SUPPORTED", 0x20);
            put("SERVICE_OPTION_NOT_SUBSCRIBED", 0x21);
            put("SERVICE_OPTION_OUT_OF_ORDER", 0x22);
            put("NSAPI_IN_USE", 0x23);
            put("REGULAR_DEACTIVATION", 0x24);
            put("QOS_NOT_ACCEPTED", 0x25);
            put("NETWORK_FAILURE", 0x26);
            put("REACTIVATION_REQUESTED", 0x27);
            put("FEATURE_NOT_SUPPORTED", 0x28);
            put("SEMANTIC_ERROR_IN_TFT", 0x29);
            put("SYNTACTICAL_ERROR_IN_TFT", 0x2A);
            put("UNKNOWN_PDP_CONTEXT", 0x2B);
            put("SEMANTIC_ERROR_IN_PACKET_FILTER", 0x2C);
            put("SYNTACTICAL_ERROR_IN_PACKET_FILTER", 0x2D);
            put("PDP_CONTEXT_WITHOU_TFT_ALREADY_ACTIVATED", 0x2E);
            put("MULTICAST_GROUP_MEMBERSHIP_TIMEOUT", 0x2F);
            put("BCM_VIOLATION", 0x30);
            put("LAST_PDN_DISC_NOT_ALLOWED", 0x31);
            put("ONLY_IPV4_ALLOWED", 0x32);
            put("ONLY_IPV6_ALLOWED", 0x33);
            put("ONLY_SINGLE_BEARER_ALLOWED", 0x34);
            put("INFORMATION_NOT_RECEIVED", 0x35);
            put("PDN_CONNECTION_NOT_EXIST", 0x36);
            put("MULTIPLE_PDN_APN_NOT_ALLOWED", 0x037);
            put("COLLISION_WITH_NW_INITIATED_REQUEST", 0x38);
            put("UNSUPPORTED_QCI_VALUE", 0x3B);
            put("BEARER_HANDLING_NOT_SUPPORT", 0x3C);
            put("MAX_PDP_NUMBER_REACHED", 0x41);
            put("APN_NOT_SUPPORT_IN_RAT_PLMN", 0x42);
            put("INVALID_TRANSACTION_ID_VALUE", 0x51);
            put("SEMENTICALLY_INCORRECT_MESSAGE", 0x5F);
            put("INVALID_MANDATORY_INFO", 0x60);
            put("MESSAGE_TYPE_NONEXIST_NOT_IMPLEMENTED", 0x61);
            put("MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE", 0x62);
            put("INFO_ELEMENT_NONEXIST_NOT_IMPLEMENTED", 0x63);
            put("CONDITIONAL_IE_ERROR", 0x64);
            put("MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE", 0x65);
            put("PROTOCOL_ERRORS", 0x6F);
            put("PN_RESTRICTION_VALUE_INCOMPATIBLE_WITH_PDP_CONTEXT", 0x70);

            // Local errors generated by Vendor RIL
            // specified in ril.h
            put("REGISTRATION_FAIL", -1);
            put("GPRS_REGISTRATION_FAIL", -2);
            put("SIGNAL_LOST", -3);
            put("PREF_RADIO_TECH_CHANGED", -4);
            put("RADIO_POWER_OFF", -5);
            put("TETHERED_CALL_ACTIVE", -6);
            put("PDP_FAIL_FALLBACK_RETRY", -1000);
            put("INSUFFICIENT_LOCAL_RESOURCES", 0xFFFFE);
            put("ERROR_UNSPECIFIED", 0xFFFF);

            // Errors generated by the Framework
            put("UNKNOWN", 0x10000);
            put("RADIO_NOT_AVAILABLE", 0x10001);
            put("UNACCEPTABLE_NETWORK_PARAMETER", 0x10002);
            put("CONNECTION_TO_DATACONNECTIONAC_BROKEN", 0x10003);
            put("LOST_CONNECTION", 0x10004);
            put("RESET_BY_FRAMEWORK", 0x10005);

            put("PAM_ATT_PDN_ACCESS_REJECT_IMS_PDN_BLOCK_TEMP", 0x1402);
            put("TCM_ESM_TIMER_TIMEOUT", 0x1502A);

            put("DUE_TO_REACH_RETRY_COUNTER", 0x0E0F);
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("onReceive, intent action is " + intent.getAction());

            if (action.equalsIgnoreCase(
                    TelephonyManager.ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED)) {
                int mState = intent.getIntExtra(PhoneConstants.STATE_KEY,
                        TelephonyManager.DATA_UNKNOWN);
                int mNetworkType = intent.getIntExtra(PhoneConstants.DATA_NETWORK_TYPE_KEY,
                        TelephonyManager.NETWORK_TYPE_UNKNOWN);
                String mAPNType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                String mAPN = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                String mReason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
                String mFailCause = intent.getStringExtra(PhoneConstants.DATA_FAILURE_CAUSE_KEY);

                PreciseDataConnectionState state = new PreciseDataConnectionState(
                        mState, mNetworkType, mAPNType, mAPN, mReason, null, mFailCause);

                if (mAPNType.equalsIgnoreCase(PhoneConstants.APN_TYPE_IMS) ||
                        mAPNType.equalsIgnoreCase(PhoneConstants.APN_TYPE_EMERGENCY)) {
                    log("data state: " + state.toString());

                    synchronized (mAPNStatuses) {
                        ApnStatus apnStatus = mAPNStatuses.get(mAPNType);
                        log("ApnStatus: " + apnStatus);

                        switch (mState) {
                            case TelephonyManager.DATA_CONNECTING:
                                if (apnStatus.mStatus == TelephonyManager.DATA_DISCONNECTED) {
                                    log("[ " + mAPNType
                                            + " ] setupData starting, remove timeout handler.....");
                                    mHandler.removeMessages(MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT);
                                    apnStatus.mStatus = TelephonyManager.DATA_CONNECTING;
                                }
                                break;

                            case TelephonyManager.DATA_DISCONNECTED:
                            case TelephonyManager.DATA_UNKNOWN:
                                if (isReasonAllowedToDetach(mReason)
                                        || (mFailCause != null && mFailCause.length() > 0)) {
                                    if (apnStatus.isSendReq == true
                                            || apnStatus.mStatus == TelephonyManager.DATA_CONNECTED)
                                    {
                                        log("send [ " + apnStatus.mName
                                                + " ] disconnected notify to message queue");
                                        mHandler.sendMessage(mHandler.obtainMessage(
                                                MSG_ON_NOTIFY_DATA_DISCONNECTED, state));
                                    }
                                } else {
                                    log("Igonre, It is not data conneciton event mReason: "
                                            + mReason
                                            + " failCause: " + mFailCause);
                                }
                                break;

                            default:
                                loge("No handle the data status.");
                                break;
                        }
                    }
                }
            } else if (action.equalsIgnoreCase(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, 0);
                String simState = intent.getStringExtra((IccCardConstants.INTENT_KEY_ICC_STATE));
                log("phoneId: " + phoneId + " subId: " + subId + " sim state: " + simState);

                if (simState.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
                    if (phoneId == ImsAdapter.Util.getDefaultVoltePhoneId()) {
                        synchronized (mAPNStatuses) {
                            for (ApnStatus apnStatus: mAPNStatuses.values()) {
                                log("ApnStatus: " + apnStatus);
                                log("Sim is not ready, reset " + apnStatus.mName + " pdn status");
                                apnStatus.mStatus = TelephonyManager.DATA_DISCONNECTED;
                                apnStatus.isSendReq = false;
                                TransactionParam deacTrans = findTransaction(
                                VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ,
                                        apnStatus.mName);
                                if (deacTrans != null) {
                                    if (deacTrans.mIsAbort) {
                                        handleDefaultDataConnAbortRequest(apnStatus.mName, 0);
                                    } else {
                                        responseDefaultBearerDataConnDeactivated(deacTrans,
                                                FAILCAUSE_NONE, apnStatus.ifaceName);
                                    }
                                }
                            }
                        }
                    }
                }

                if (simState.equalsIgnoreCase(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {

                    if (mSimStatus[phoneId] == true) {
                        log("Sim" + (phoneId + 1) + " already enable, ");
                        return;
                    }

                    log("set Sim" + (phoneId + 1) + " state: true");
                    mSimStatus[phoneId] = true;

                    if (phoneId == ImsAdapter.Util.getDefaultVoltePhoneId()) {
                        TransactionParam actTrans = findTransaction
                                (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ,
                                        PhoneConstants.APN_TYPE_IMS);
                        if (actTrans != null) {
                            if (!isImsApnExists(phoneId)) {
                                log("no IMS apn Exists!!");
                                rejectDefaultBearerDataConnActivation(actTrans,
                                        FAILCAUSE_UNKNOWN,
                                        500);
                                return;
                            }
                            log("process pending PDN request ");
                            mHandler.removeMessages(MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT);
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(
                                    MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT, actTrans),
                                    MAX_NETWORK_ACTIVE_TIMEOUT_MS);

                            if (requestNwRequest(PhoneConstants.APN_TYPE_IMS, phoneId) < 0) {
                                rejectDefaultBearerDataConnActivation(actTrans,
                                        FAILCAUSE_UNKNOWN,
                                        0);
                            }
                        }
                    }
                } else {
                    log("set Sim" + (phoneId + 1) + " state: false");
                    mSimStatus[phoneId] = false;
                }
            }
        }

    };

    private HashMap<String, ApnStatus> mAPNStatuses = new HashMap<String, ApnStatus>();

    private Handler mHandler;
    private Thread mHandlerThread = new Thread() {
        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() { // create handler here
                @Override
                synchronized public void handleMessage(Message msg) {
                    if (!mIsEnable) {
                        loge("receives message [" + msg.what
                                + "] but DataDispatcher is not enabled, ignore");
                        return;
                    }

                    if (msg.obj instanceof VaEvent) {
                        VaEvent event = (VaEvent) msg.obj;
                        log("receives request [" + msg.what + ", " + event.getDataLen() +
                                ", phoneId: " + event.getPhoneId() + "]");
                        switch (msg.what) {
                            case VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ:
                                handleDefaultBearerActivationRequest(event);
                                break;

                            case VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ:
                                handleDefaultBearerDeactivationRequest(event);
                                break;
                            case VaConstants.MSG_ID_REQUEST_PCSCF_DISCOVERY:
                                rejectPcscfDiscovery(event.getByte(), FAILCAUSE_UNKNOWN);
                                break;
                            default:
                                log("receives unhandled message [" + msg.what + "]");
                        }
                    } else {
                        log("receives request [" + msg.what + "]");
                        switch (msg.what) {
                            case MSG_ON_NOTIFY_DATA_CONNECTED:
                                Network network = (Network) msg.obj;
                                handleDefaultBearerActivationResponse(network,
                                        getApnTypeByCap(msg.arg1));
                                break;

                            case MSG_ON_NOTIFY_DATA_DISCONNECTED:
                                PreciseDataConnectionState state =
                                        (PreciseDataConnectionState) msg.obj;
                                handleDefaultBearerDeactivationResonse(state);
                                break;

                            case MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT:
                                TransactionParam param = (TransactionParam) msg.obj;
                                rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN,
                                        WAITING_FRAMEWORK_STATUS_SYNC);
                                break;

                            case MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT:
                                log("deactive PDN timeout, clear transation of  IMCB");
                                ApnStatus apnStatus = (ApnStatus) msg.obj;
                                responseDefaultBearerDataConnDeactivated(findTransaction(
                                        VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ,
                                        apnStatus.mName), FAILCAUSE_NONE, apnStatus.ifaceName);
                                break;
                            default:
                                log("receives unhandled message [" + msg.what + "]");
                        }
                    }
                }
            };
            Looper.loop();
        }
    };

    public DataDispatcher(Context context, VaSocketIO IO) {
        mContext = context;
        mSocket = IO;
        mDataDispatcherUtil = new DataDispatcherUtil();
        mInstance = this;

        TelephonyManager tm = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        mSimStatus = new boolean[tm.getPhoneCount()];
        mHandlerThread.start();
        createNetworkRequest();
    }

    public static DataDispatcher getInstance() {
        return mInstance;
    }

    public void enableRequest() {
        synchronized (mHandler) {
            log("receive enableRequest");
            log("registerReceiver");

            Arrays.fill(mSimStatus, false);

            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyManager.ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED);
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, filter);
            mIsEnable = true;
        }
    }

    public void disableRequest() {
        synchronized (mHandler) {
            log("receive disableRequest");
            log("unregisterReceiver");
            if (mIsEnable == true) {
                mContext.unregisterReceiver(mBroadcastReceiver);
                synchronized (mTransactions) {
                    log("disableRequest to clear transactions");
                    mTransactions.clear();
                }
                releaseNwRequest(PhoneConstants.APN_TYPE_IMS);
                releaseNwRequest(PhoneConstants.APN_TYPE_EMERGENCY);
                mHandler.removeMessages(MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT);
                mHandler.removeMessages(MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT);

                synchronized (mAPNStatuses) {
                    for (String apnType : mAPNStatuses.keySet())
                    {
                        ApnStatus status = (ApnStatus) mAPNStatuses.get(apnType);
                        status.mName = "";
                        status.isSendReq = false;
                        status.mStatus = TelephonyManager.DATA_DISCONNECTED;
                    }
                }
                mIsEnable = false;
            } else {
                log("DataDispatcher already be disabled");
            }

        }
    }

    private void handleDefaultBearerActivationRequest(VaEvent event) {
        String apnType = PhoneConstants.APN_TYPE_IMS;
        int phoneId = event.getPhoneId();

        log("handleDefaultBearerActivationRequest");

        DataDispatcherUtil.PdnActivationInd actInd = mDataDispatcherUtil
                .extractDefaultPdnActInd(event);

        TransactionParam param = new TransactionParam(actInd.transactionId,
                event.getRequestID(), phoneId, apnType);

        if (actInd.isEmergency) {
            apnType = PhoneConstants.APN_TYPE_EMERGENCY;
            param.isEmergency = true;
            param.apnName = apnType;
        }

        putTransaction(param);
        if (apnType == PhoneConstants.APN_TYPE_IMS) {
            int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
            if (mSimStatus[phoneId] || subId > 0) {
                mSimStatus[phoneId] = true;
                if (!isImsApnExists(phoneId)) {
                    log("no IMS apn Exists!!");
                    rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 500);
                    return;
                }
                mHandler.removeMessages(MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT, param),
                        MAX_NETWORK_ACTIVE_TIMEOUT_MS);
            } else {
                log("sim is not ready, pending IMS PDN activation.");
                return;
            }
        }

        if (requestNwRequest(apnType, phoneId) < 0) {
            rejectDefaultBearerDataConnActivation(param, FAILCAUSE_UNKNOWN, 0);
        }
    }

    private void responseDefaultBearerDataConnActivated(TransactionParam param,
            int netId, String ifaceName) {
        log("responseDefaultBearerDataConnActivated ");

        if (param == null) {
            loge("TransactionParam can not be null");
            return;
        }

        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(
                    param.phoneId,
                    VaConstants.MSG_ID_WRAP_IMSPA_IMSM_PDN_ACT_ACK_RESP);
            event.putByte(param.transactionId);
            event.putBytes(new byte[3]);
            event.putInt(netId);
            event.putString(ifaceName, 16);

            // log("netId =  " + network.netId + " IfaceName = " + mLink.getInterfaceName());
            removeTransaction(param.transactionId);
            sendVaEvent(event);

        } else {
            loge("responseDefaultBearerDataConnActivated "
                    + "but transactionId does not existed, ignore");
        }

    }

    private void rejectDefaultBearerDataConnActivation(TransactionParam param, int failCause,
            int delayMs) {

        if (param == null) {
            loge("TransactionParam can not be null");
            return;
        }

        if (hasTransaction(param.transactionId)) {
            releaseNwRequest(param.isEmergency ? PhoneConstants.APN_TYPE_EMERGENCY :
                    PhoneConstants.APN_TYPE_IMS);

            removeTransaction(param.transactionId);
            // prevent timing issue, cause framework state not sync with native
            delayForSeconds(delayMs);

            sendVaEvent(makeRejectDefaultBearerEvent(param, failCause));
        } else {
            loge("rejectDefaultBearerDataConnActivation "
                    + "but transactionId does not existed, ignore");
        }
    }

    private ImsAdapter.VaEvent makeRejectDefaultBearerEvent(TransactionParam trans, int failCause) {
        ImsAdapter.VaEvent event;

        if (trans.requestId == VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ) {
            event = new ImsAdapter.VaEvent(trans.phoneId,
                    VaConstants.MSG_ID_WRAP_IMSPA_IMSM_PDN_ACT_REJ_RESP);
            log("rejectDefaultBearerDataConnActivation param" + trans + ", failCause=" + failCause);
        } else {
            event = new ImsAdapter.VaEvent(trans.phoneId,
                    VaConstants.MSG_ID_WRAP_IMSPA_IMSM_PDN_DEACT_REJ_RESP);
            log("rejectDefaultBearerDataConnDeactivation param" + trans + ", failCause="
                    + failCause);
        }

        event.putByte(trans.transactionId);
        event.putByte(failCause);
        event.putBytes(new byte[2]);

        return event;
    }

    private void handleDefaultBearerDeactivationRequest(VaEvent event) {
        log("handleDefaultBearerDeactivationRequest");
        DataDispatcherUtil.PdnDeactivationInd deactInd = mDataDispatcherUtil.extractDeactInd(event);
        int phoneId = event.getPhoneId();
        String apnType = PhoneConstants.APN_TYPE_IMS;

        if (deactInd.isEmergency) {
            apnType = PhoneConstants.APN_TYPE_EMERGENCY;
        }

        TransactionParam trans = new TransactionParam(deactInd.transactionId,
                event.getRequestID()
                , phoneId, apnType);

        synchronized (mAPNStatuses) {
            ApnStatus apnStatus = mAPNStatuses.get(apnType);
            log("ApnStatus: " + apnStatus);

            mHandler.removeMessages(MSG_ON_NOTIFY_ACTIVE_DATA_TIMEOUT);
            mHandler.removeMessages(MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT);

            putTransaction(trans);

            // deactivate default bearer
            if (deactInd.isValid) {
                log("transactionId = " + deactInd.transactionId + " deactivation " + apnType
                        + " PDN");
                if (apnStatus.mStatus == TelephonyManager.DATA_DISCONNECTED) {
                    log("PDN: [" + apnType + "] already deactivation.");
                    responseDefaultBearerDataConnDeactivated(
                            trans, FAILCAUSE_UNKNOWN, apnStatus.ifaceName);
                    return;
                } else {
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(
                            MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT, apnStatus),
                            MAX_NETWORK_DEACTIVE_TIMEOUT_MS);
                    if (releaseNwRequest(apnType) < 0) {
                        rejectDefaultBearerDataConnDeactivation(trans, 1);
                        return;
                    }
                }
            } else {
                // abort default bearer
                trans.mIsAbort = true;
                log("transactionId = " + deactInd.transactionId + " abort transactionId = "
                        + deactInd.abortTransactionId + " " + apnType
                        + " PDN");

                TransactionParam actTrans = findTransaction
                        (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ, apnType);

                if (apnStatus.mStatus == TelephonyManager.DATA_CONNECTING && actTrans != null) {
                    log("ims pdn connecting, pending abort request!");
                    return;
                } else if (apnStatus.mStatus == TelephonyManager.DATA_CONNECTED) {
                    log("IMS PDN already connected!, follow normal deactivae flow......");
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(
                            MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT, apnStatus),
                            MAX_NETWORK_DEACTIVE_TIMEOUT_MS);
                    if (releaseNwRequest(apnType) < 0) {
                        rejectDefaultBearerDataConnDeactivation(trans, 1);
                        return;
                    }
                    return;
                }
                handleDefaultDataConnAbortRequest(apnType, 0);
            }
        }
    }

    private void handleDefaultDataConnAbortRequest(String type, int delayMs) {

        synchronized (mAPNStatuses) {
            ApnStatus apnStatus = mAPNStatuses.get(type);
            apnStatus.mStatus = TelephonyManager.DATA_DISCONNECTED;
            apnStatus.isSendReq = false;
            log("IMCB send abort [" + type + "] connection");
            TransactionParam deactTrans = findTransaction
                    (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ, type);
            if (deactTrans != null) {
                releaseNwRequest(type);
                TransactionParam actTrans = findTransaction
                        (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ, type);
                if (actTrans != null) {
                    log("[Abort] send reject activation transaction to IMSM");
                    rejectDefaultBearerDataConnActivation(actTrans,
                            FAILCAUSE_UNKNOWN, delayMs);
                }
                log("[Abort] send response abort transaction to IMSM");
                responseDefaultBearerDataConnDeactivated(deactTrans,
                        FAILCAUSE_NONE, apnStatus.ifaceName);
            } else {
                // deactImsTrans == null
                loge("abort transaction not in queue...");
            }
        }

    }

    private void rejectDefaultBearerDataConnDeactivation(TransactionParam param, int failCause) {
        mHandler.removeMessages(MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT);
        if (hasTransaction(param.transactionId)) {
            removeTransaction(param.transactionId);
            sendVaEvent(makeRejectDefaultBearerEvent(param, failCause));
        } else {
            loge("rejectDefaultBearerDataConnDeactivation "
                    + "but transactionId does not existed, ignore");
        }
    }

    private void createNetworkRequest() {
        final int count = APN_CAP_LIST.length;
        mDataNetworkRequests = new DataDispatcherNetworkRequest[count];

        for (int i = 0; i < count; i++) {
            NetworkCapabilities netCap = new NetworkCapabilities();
            int cap = APN_CAP_LIST[i];
            netCap.addCapability(cap);
            netCap.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            mDataNetworkRequests[i] = new DataDispatcherNetworkRequest(getNwCBbyCap(cap),
                    getApnTypeByCap(cap));
            mDataNetworkRequests[i].nwCap = netCap;

            // Configure APN Status
            mAPNStatuses.put(getApnTypeByCap(cap), new ApnStatus(
                    getApnTypeByCap(cap)));
        }
    }

    private int releaseNwRequest(String requestApnType) {
        int nRet = 0;
        int endPos = mDataNetworkRequests.length;
        int pos = getNetworkRequetsPos(requestApnType, endPos);

        if (pos > -1 && pos < endPos) {
            log("releaseNwRequest pos: " + pos + ", requestApnType: "
                    + requestApnType);
            NetworkCallback nwCb = mDataNetworkRequests[pos].nwCb;
            try {
                getConnectivityManager().unregisterNetworkCallback(nwCb);
            } catch (IllegalArgumentException ex) {
                loge("cb already has been released!!");
            }
        } else {
            loge("unknown apnType: " + requestApnType + " skip requestNetwork ");
            nRet = -1;
        }

        return nRet;
    }

    private int requestNwRequest(String requestApnType, int phoneId) {
        int nRet = 0;
        int endPos = mDataNetworkRequests.length;
        int pos = getNetworkRequetsPos(requestApnType, endPos);
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);

        if (pos > -1 && pos < endPos) {
            log("requestNwRequest pos: " + pos + ", requestApnType: "
                    + requestApnType + " subId: " + subId);
            NetworkCallback nwCb = mDataNetworkRequests[pos].nwCb;

            // generator network request
            Builder builder = new NetworkRequest.Builder();
            builder.addCapability(APN_CAP_LIST[pos]);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            builder.setNetworkSpecifier(String.valueOf(subId));

            mDataNetworkRequests[pos].nwRequest = builder.build();
            NetworkRequest nwRequest = mDataNetworkRequests[pos].nwRequest;

            log("before start requestNetwork, first relaseNetwork!");
            releaseNwRequest(requestApnType);
            synchronized (mAPNStatuses) {
                if (SystemProperties.getInt("persist.net.wo.debug.no_ims", 0) != 1) {
                    log("start requestNetwork for " + requestApnType);
                    ApnStatus apnStatus = mAPNStatuses.get(requestApnType);
                    apnStatus.mName = requestApnType;
                    apnStatus.mStatus = TelephonyManager.DATA_DISCONNECTED;
                    apnStatus.isSendReq = true;
                    apnStatus.ifaceName = "";
                    log("ApnStatus: " + apnStatus);
                    getConnectivityManager().requestNetwork(nwRequest, nwCb,
                            ConnectivityManager.MAX_NETWORK_REQUEST_TIMEOUT_MS);
                }
            }
        } else {
            loge("unknow apnType: " + requestApnType + " skip requestNetwork ");
            nRet = -1;
        }

        return nRet;
    }

    private boolean isImsApnExists(int phoneId) {
        boolean hasImsApn = false;
        try {
            String operator = "";
            TelephonyManager tm = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);
            operator = tm.getSimOperatorNumericForPhone(phoneId);

            if (operator != null) {
                String selection = "numeric = '" + operator + "'";
                selection += " and type like '" + "%ims%'";
                log("query: selection=" + selection);
                Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
                Cursor cursor = mContext.getContentResolver().query(
                        CONTENT_URI, null, selection, null, null);

                if (cursor != null) {
                    if (cursor.getCount() > 0) {
                        log("has ims apn!!");
                        hasImsApn = true;
                    }
                    cursor.close();
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return hasImsApn;
    }

    NetworkCallback mImsNetworkCallback = new NetworkCallback() {

        @Override
        public void onAvailable(Network network) {
            NetworkInfo netInfo = getConnectivityManager().getNetworkInfo(network);
            log("onAvailable: networInfo: " + netInfo);

            if ("connected".equalsIgnoreCase(netInfo.getReason())) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_NOTIFY_DATA_CONNECTED,
                        NetworkCapabilities.NET_CAPABILITY_IMS, 0, network));
            } else {
                rejectDefaultBearerDataConnActivation(
                        findTransaction(VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ,
                                PhoneConstants.APN_TYPE_IMS), FAILCAUSE_UNKNOWN,
                        WAITING_FRAMEWORK_STATUS_SYNC);
            }
        }
    };

    NetworkCallback mEImsNetworkCallback = new NetworkCallback() {

        @Override
        public void onAvailable(Network network) {
            NetworkInfo netInfo = getConnectivityManager().getNetworkInfo(network);
            log("onAvailable: networInfo: " + netInfo);
            if ("connected".equalsIgnoreCase(netInfo.getReason())) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_NOTIFY_DATA_CONNECTED,
                        NetworkCapabilities.NET_CAPABILITY_EIMS, 0, network));
            } else {
                rejectDefaultBearerDataConnActivation(
                        findTransaction(VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ,
                                PhoneConstants.APN_TYPE_EMERGENCY), FAILCAUSE_UNKNOWN,
                        WAITING_FRAMEWORK_STATUS_SYNC);
            }
        }
    };

    private void handleDefaultBearerActivationResponse(Network network, String type) {
        log("handleDefaultBearerActivationResponse for APN: " + type);

        TransactionParam deacTrans = findTransaction
                (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ, type);

        synchronized (mAPNStatuses) {
            ApnStatus apnStatus = mAPNStatuses.get(type);

            if (deacTrans == null) {
                apnStatus.mStatus = TelephonyManager.DATA_CONNECTED;
                ConnectivityManager cm = (ConnectivityManager) mContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                LinkProperties mLink = cm.getLinkProperties(network);
                if (mLink == null) {
                    loge("Link Propertiys is null at network");
                    rejectDefaultBearerDataConnActivation(
                            findTransaction(VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ,
                                    type), FAILCAUSE_NONE, 0);
                    return;
                }
                apnStatus.ifaceName = mLink.getInterfaceName();
                log("APNStatus: " + apnStatus);
                log("netId =  " + network.netId + " IfaceName = " + mLink.getInterfaceName());
                if (IMS_INTERFACE_NAME.equals(apnStatus.ifaceName)
                        || EMERGENCY_INTERFACE_NAME.equals(apnStatus.ifaceName)) {
                    responseDefaultBearerDataConnActivated(
                            findTransaction(VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ, type),
                            network.netId, apnStatus.ifaceName);
                } else {
                    loge("interface name not valid");
                    rejectDefaultBearerDataConnActivation(
                            findTransaction(VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ,
                                    type), FAILCAUSE_NONE, 0);
                }
            } else {
                log("find pending abort request.... ");
                handleDefaultDataConnAbortRequest(type, WAITING_FRAMEWORK_STATUS_SYNC);
            }
        }
    }

    private boolean isReasonAllowedToDetach(String reason) {
        boolean bRet = false;
        if (Phone.REASON_DATA_DISABLED.equals(reason) || Phone.REASON_DATA_DETACHED.equals(reason)
                || Phone.REASON_APN_CHANGED.equals(reason)
                || Phone.REASON_APN_SWITCHED.equals(reason)
                || Phone.REASON_APN_FAILED.equals(reason)
                || Phone.REASON_PDP_RESET.equals(reason)
                || Phone.REASON_LOST_DATA_CONNECTION.equals(reason)
                || Phone.REASON_QUERY_PLMN.equals(reason)
                || Phone.REASON_CONNECTED.equals(reason)
                || Phone.REASON_RADIO_TURNED_OFF.equals(reason)
                || DcFailCause.LOST_CONNECTION.toString().equals(reason)
                || reason == null) {
            bRet = true;
        }
        log("isReasonAllowedToDetach ret: " + bRet);
        return bRet;
    }

    private NetworkCallback getNwCBbyCap(int cap) {
        NetworkCallback nwCb = null;
        switch (cap) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                nwCb = mImsNetworkCallback;
                break;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                nwCb = mEImsNetworkCallback;
                break;
            default:
                loge("error: nwCB=null for invalid cap (" + cap + ")");
        }
        return nwCb;
    }

    private String getApnTypeByCap(int cap) {
        String apnType = "";
        switch (cap) {
            case NetworkCapabilities.NET_CAPABILITY_IMS:
                apnType = PhoneConstants.APN_TYPE_IMS;
                break;
            case NetworkCapabilities.NET_CAPABILITY_EIMS:
                apnType = PhoneConstants.APN_TYPE_EMERGENCY;
                break;
            default:
                loge("error: apnType=\"\" for invalid cap (" + cap + ")");
        }
        return apnType;
    }

    private int getNetworkRequetsPos(String requestApnType, int endPos) {
        int pos = -1;
        for (int i = 0; i < endPos; i++) {
            if (TextUtils.equals(mDataNetworkRequests[i].apnType, requestApnType)) {
                pos = i;
                break;
            }
        }
        return pos;
    }

    private static class DataDispatcherNetworkRequest {
        Network currentNw;
        NetworkRequest nwRequest;
        NetworkCapabilities nwCap;
        NetworkCallback nwCb;
        String apnType = "";

        public DataDispatcherNetworkRequest(NetworkCallback nwCb, String apnType) {
            this.nwCb = nwCb;
            this.apnType = apnType;
        }

        public String toString() {
            return "apnType: " + apnType + ", nwRequest: "
                    + nwRequest + ", network: " + currentNw;
        }
    }

    private void handleDefaultBearerDeactivationResonse(
            PreciseDataConnectionState state) {

        String apnType = state.getDataConnectionAPNType();
        String failCause = state.getDataConnectionFailCause();

        log("start [" + apnType + "] deactivation flow......");
        synchronized (mAPNStatuses) {
            ApnStatus apnStatus = mAPNStatuses.get(apnType);
            apnStatus.mStatus = TelephonyManager.DATA_DISCONNECTED;
            apnStatus.isSendReq = false;

            TransactionParam deacTrans = findTransaction
                    (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_DEACT_REQ, apnType);
            if (deacTrans == null) {
                TransactionParam actTrans = findTransaction
                        (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ, apnType);
                if (actTrans == null) {
                    log("Network/Framework send deactivation IMS connection");
                    notifyDefaultBearerDataConnDeactivated(
                            failCauses.get(failCause), apnStatus.ifaceName, apnType);
                } else {
                    log("IMCB requestNetwork fail");
                    rejectDefaultBearerDataConnActivation(actTrans,
                            failCauses.get(failCause), WAITING_FRAMEWORK_STATUS_SYNC);
                }
            } else {
                TransactionParam actTrans = findTransaction
                        (VaConstants.MSG_ID_WRAP_IMSM_IMSPA_PDN_ACT_REQ, apnType);
                if (actTrans == null) {
                    log("IMCB send deactivation IMS connection");
                    responseDefaultBearerDataConnDeactivated(deacTrans,
                            failCauses.get(failCause), apnStatus.ifaceName);
                } else {
                    log("find abort pdn request");
                    handleDefaultDataConnAbortRequest(apnType, 0);
                }

            }
        }
    }

    private void responseDefaultBearerDataConnDeactivated(TransactionParam param, int cause,
            String IntlName) {
        log("responseDefaultBearerDataConnDeactivated");
        mHandler.removeMessages(MSG_ON_NOTIFY_DEACTIVE_DATA_TIMEOUT);
        releaseNwRequest(param.isEmergency ? PhoneConstants.APN_TYPE_EMERGENCY :
                PhoneConstants.APN_TYPE_IMS);
        if (hasTransaction(param.transactionId)) {
            ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(param.phoneId,
                    VaConstants.MSG_ID_WRAP_IMSPA_IMSM_PDN_DEACT_ACK_RESP);
            log("responseDataConnectionDeactivated param" + param);

            event.putByte(param.transactionId);
            event.putByte(cause);
            event.putBytes(new byte[2]); // padding
            event.putString(IntlName, 16);

            removeTransaction(param.transactionId);
            sendVaEvent(event);
        } else {
            loge("responseDataConnectionDeactivated but transactionId does not existed, ignore");
        }

    }

    private void notifyDefaultBearerDataConnDeactivated(
            int cause, String intlName, String apnType) {
        log("notifyDefaultBearerDataConnDeactivated for [" + apnType + "]");
        releaseNwRequest(apnType);

        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(ImsAdapter.Util.getDefaultVoltePhoneId(),
                VaConstants.MSG_ID_WRAP_IMSPA_IMSM_PDN_DEACT_IND);

        event.putByte(cause);
        event.putBytes(new byte[3]);
        event.putString(intlName, 16);

        sendVaEvent(event);
    }

    private void rejectPcscfDiscovery(int transactionId, int failCause) {

        ImsAdapter.VaEvent event = new ImsAdapter.VaEvent(
                ImsAdapter.Util.getDefaultVoltePhoneId(),
                VaConstants.MSG_ID_REJECT_PCSCF_DISCOVERY);
        log("rejectPcscfDiscovery transId= " + transactionId + ", failCause=" + failCause);

        // imcf_uint8 transaction_id
        // imc_ps_cause_enum ps_caus
        // imcf_uint8 pad [2]
        event.putByte(transactionId);
        event.putByte(failCause);
        event.putBytes(new byte[2]); // padding

        sendVaEvent(event);
    }

    private void delayForSeconds(int seconds) {
        try {
            Thread.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle Data related event from IMCB.
     *
     * @param VaEvent event from IMCB.
     */
    public void vaEventCallback(VaEvent event) {
        // relay to main thread to keep rceiver and callback handler is working under the same
        // thread
        mHandler.sendMessage(mHandler.obtainMessage(event.getRequestID(), event));
    }

    private void sendVaEvent(VaEvent event) {
        log("DataDispatcher send event [" + event.getRequestID() + ", " + event.getDataLen() + "]");
        mSocket.writeEvent(event);
    }

    private static void log(String text) {
        Log.d(TAG, "[dedicate] DataDispatcher " + text);
    }

    private static void loge(String text) {
        Log.e(TAG, "[dedicate] DataDispatcher " + text);
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    protected boolean hasTransaction(int transactionId) {
        synchronized (mTransactions) {
            return (mTransactions.get(transactionId) != null);
        }
    }

    protected TransactionParam findTransaction(int reqId, String apn) {
        log("findTransaction reqId: " + reqId + " apn: " + apn);
        dumpTransactions();
        synchronized (mTransactions) {
            Integer[] keys = getTransactionKeyArray();
            for (Integer transactionId : keys) {
                TransactionParam param = getTransaction(transactionId);
                if (param.requestId == reqId &&
                        param.apnName.equalsIgnoreCase(apn)) {
                    return param;
                }
            }
            return null;
        }
    }

    protected void putTransaction(TransactionParam param) {
        synchronized (mTransactions) {
            mTransactions.put(param.transactionId, param);
            if (DUMP_TRANSACTION) {
                dumpTransactions();
            }
        }
    }

    protected void removeTransaction(int transactionId) {
        synchronized (mTransactions) {
            mTransactions.remove(transactionId);
            if (DUMP_TRANSACTION) {
                dumpTransactions();
            }
        }
    }

    protected TransactionParam getTransaction(int transactionId) {
        synchronized (mTransactions) {
            return mTransactions.get(transactionId);
        }
    }

    protected Integer[] getTransactionKeyArray() {
        synchronized (mTransactions) {
            Object[] array = mTransactions.keySet().toArray();
            if (array == null) {
                return new Integer[0];
            } else {
                Integer[] intArray = new Integer[array.length];
                for (int i = 0; i < array.length; i++)
                    intArray[i] = (Integer) array[i];
                return intArray;
            }
        }
    }

    protected void dumpTransactions() {
        if (mTransactions.size() > 0) {
            log("====Start dump [transactions]====");
            for (TransactionParam param : mTransactions.values()) {
                log("dump transactions" + param);
            }
            log("====End dump [transactions]====");
        } else {
            log("====dump [transactions] but empty====");
        }
    }

    private class TransactionParam {
        public int transactionId;
        public int requestId;
        public String apnName = "";
        public boolean isEmergency = false;
        public boolean mIsAbort = false;
        public int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;

        public TransactionParam(int tid, int reqId, int phoneId, String apn) {
            transactionId = tid;
            requestId = reqId;
            this.phoneId = phoneId;
            apnName = apn;
        }

        @Override
        public String toString() {
            return "[transactionId= " + transactionId + ", request= " + requestId + ", apn= "
                    + apnName +
                    ", phoneId= " + phoneId + "]";
        }
    }

    private class ApnStatus {

        String mName = "";
        int mStatus = 0;
        boolean isSendReq = false;
        String ifaceName = "";

        // boolean isAbort;

        public ApnStatus(String name) {
            log("new apn status for: " + name);
            mName = name;
            mStatus = TelephonyManager.DATA_DISCONNECTED;
            isSendReq = false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("name: " + mName);
            sb.append(", status: " + statusToString(mStatus));
            sb.append(", isSendReq: " + isSendReq);
            sb.append(", ifaceName: " + ifaceName);

            return sb.toString();
        }

        private String statusToString(int status) {
            String rs = "";

            switch (status) {
                case TelephonyManager.DATA_CONNECTED:
                    rs = "DATA_CONNECTED";
                    break;

                case TelephonyManager.DATA_CONNECTING:
                    rs = "DATA_CONNECTING";
                    break;

                case TelephonyManager.DATA_DISCONNECTED:
                    rs = "DATA_DISCONNECTED";
                    break;
            }

            return rs;
        }

    }
}
