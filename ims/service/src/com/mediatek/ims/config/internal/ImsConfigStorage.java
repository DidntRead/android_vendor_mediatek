package com.mediatek.ims.config.internal;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;

import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.ims.config.*;
import com.mediatek.ims.config.op.PlmnTable;

import java.util.HashMap;
import java.util.Map;


/**
 * The wrapper class to manage IMS configuration storage, including
 * 1.) Load default value from carrier's customization xml file.
 * 2.) TODO: Load provisioned value from non-volatile memory.
 * 3.) Initialize IMS configuration databases.
 */
public class ImsConfigStorage {
    private static final String TAG = "ImsConfig";
    private static final boolean DEBUG = true;

    private FeatureHelper mFeatureHelper = null;
    private ConfigHelper mConfigHelper = null;
    private int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
    private Context mContext = null;
    private ImsConfigStorage() {}
    private Handler mHandler;
    private BroadcastReceiver mReceiver;

    // Clear config store w/o reloading default value.
    static final int MSG_RESET_CONFIG_STORAGE = 0;
    // Clear config store w/ reloading default value.
    static final int MSG_LOAD_CONFIG_STORAGE = 1;

    public ImsConfigStorage(Context context, int phoneId) {
        Log.d(TAG, "ImsConfigStorage() on phone " + phoneId);
        mContext = context;
        mPhoneId = phoneId;
        HandlerThread thread = new HandlerThread("ImsConfig-" + mPhoneId);
        thread.start();
        mHandler = new CarrierConfigHandler(mPhoneId, thread.getLooper());

        mFeatureHelper = new FeatureHelper(mContext, mPhoneId);
        mConfigHelper = new ConfigHelper(mContext, mHandler, mPhoneId);
        // To init config storage when opCode is not the same.
        mConfigHelper.initConfigStorageOnOpChanged();

        // Setup a receiver observes to notify reloading when sim changed.
        mReceiver = new ImsConfigReceiver(mHandler, mPhoneId);
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
    }

    class CarrierConfigHandler extends Handler {
        private int mPhoneId;
        CarrierConfigHandler(int phoneId, Looper looper) {
            super(looper);
            mPhoneId = phoneId;
        }

        @Override
        public void handleMessage(Message msg) {
            if (ImsConfigStorage.DEBUG) {
                Log.d(TAG, "Received msg = " + msg.hashCode() + ", what = " + msg.what);
            }
            switch (msg.what) {
                case MSG_RESET_CONFIG_STORAGE:
                    Log.d(TAG, "Reset config storage");
                    mConfigHelper.clear();
                    break;
                case MSG_LOAD_CONFIG_STORAGE:
                    synchronized (mConfigHelper) {
                        String opCode = (String) msg.obj;
                        if (!mConfigHelper.getOpCode().equals(opCode)) {
                            mConfigHelper.setOpCode(opCode);
                            if (ImsConfigStorage.DEBUG) {
                                Log.d(TAG, "Start load config storage for " + opCode +
                                    " on phone " + mPhoneId);
                            }
                            mConfigHelper.clear();
                            mConfigHelper.init(opCode);
                            mConfigHelper.setInitDone(true);
                            Log.d(TAG, "Finish Loading config storage for " + opCode);
                        } else {
                            Log.d(TAG, "Skip reloading config by same opCode: " + opCode +
                                " on phone " + mPhoneId);
                        }
                    }
                    break;
                default:
                    // do nothing...
                    break;
            }
        }
    }

    /**
     * Gets the value for IMS feature item for specified network type.
     *
     * @param featureId, defined as in FeatureConstants.
     * @param network, defined as in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized int getFeatureValue(int featureId, int network)
            throws ImsException {
        return mFeatureHelper.getFeatureValue(featureId, network);
    }

    /**
     * Sets the value for IMS feature item for specified network type.
     *
     * @param featureId, as defined in FeatureConstants.
     * @param network, as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value, as defined in FeatureValueConstants.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized void setFeatureValue(int featureId, int network, int value)
            throws ImsException {
        mFeatureHelper.updateFeature(featureId, network, value);
    }

    /**
     * API to retrieve provisioned int value of IMS configurations.
     * @param configId The id defined in ImsConfig.ConfigConstants.
     * @return The int type configuration value.
     * @throws ImsException with following reason code
     *         1.) CODE_LOCAL_ILLEGAL_ARGUMENT if the configId can't match any data.
     *         2.) CODE_UNSPECIFIED if the config is without setting any value, even default.
     */
    public synchronized int getProvisionedValue(int configId)
            throws ImsException {
        enforceConfigStorageInit("getProvisionedValue(" + configId + ")");
        return mConfigHelper.getConfigValue(ImsConfigContract.TABLE_MASTER, configId);
    }

    /**
     * API to retrieve provisioned String value of IMS configurations.
     * @param configId The id defined in ImsConfig.ConfigConstants.
     * @return The int type configuration value.
     * @throws ImsException with following reason code
     *         1.) CODE_LOCAL_ILLEGAL_ARGUMENT if the configId can't match any data.
     *         2.) CODE_UNSPECIFIED if the config is without setting any value, even default.
     */
    public synchronized String getProvisionedStringValue(int configId)
            throws ImsException  {
        enforceConfigStorageInit("getProvisionedStringValue(" + configId + ")");
        return mConfigHelper.getConfigStringValue(ImsConfigContract.TABLE_MASTER, configId);
    }

    /**
     * Sets the value for IMS service/capabilities parameters by
     * the operator device management entity.
     * This function should not be called from main thread as it could block
     * mainthread.
     *
     * @param configId, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in Integer format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized void setProvisionedValue(int configId, int value)
            throws ImsException {
        enforceConfigStorageInit("setProvisionedValue(" + configId + ", " + value + ")");
        // 1. Add / update to provision table
        mConfigHelper.addConfig(ImsConfigContract.TABLE_PROVISION,
                configId, ImsConfigContract.MimeType.INTEGER, value);
        // 2. Update master table
        mConfigHelper.updateConfig(ImsConfigContract.TABLE_MASTER,
                configId, ImsConfigContract.MimeType.INTEGER, value);
    }

    /**
     * Sets the value for IMS service/capabilities parameters by
     * the operator device management entity.
     * This function should not be called from main thread as it could block
     * mainthread.
     *
     * @param configId, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in String format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized void setProvisionedStringValue(int configId, String value)
            throws ImsException {
        enforceConfigStorageInit("setProvisionedStringValue(" + configId + ", " + value + ")");
        // 1. Add / update to provision table
        mConfigHelper.addConfig(ImsConfigContract.TABLE_PROVISION, configId,
                ImsConfigContract.MimeType.STRING, value);
        // 2. Update master table
        mConfigHelper.updateConfig(ImsConfigContract.TABLE_MASTER, configId,
                ImsConfigContract.MimeType.STRING, value);
    }

    /**
     * Gets the value for IMS Volte provisioned.
     * It should be the same as operator provisioned value if applies.
     *
     * @return boolean
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized boolean getVolteProvisioned() throws ImsException {
        enforceConfigStorageInit("getVolteProvisioned");
        return mConfigHelper.getImsProvisioned(
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE);
    }

    /**
     * Gets the value for IMS Wfc provisioned.
     * It should be the same as operator provisioned value if applies.
     *
     * @return boolean
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public synchronized boolean getWfcProvisioned() throws ImsException {
        enforceConfigStorageInit("setWfcProvisioned");
        return mConfigHelper.getImsProvisioned(
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI);
    }

    private synchronized void enforceConfigStorageInit(String msg) throws ImsException {
        if (!mConfigHelper.isInitDone()) {
            Log.e(TAG, msg);
            throw new ImsException("Config storage not ready",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
        }
    }

    /* @hide */
    public synchronized void resetConfigStorage() {
        resetConfigStorage(ImsConfigContract.Operator.OP_DEFAULT);
    }

    /* @hide */
    public synchronized void resetConfigStorage(String opCode) {
        Log.d(TAG, "resetConfigStorage(" + opCode + ")");
        mConfigHelper.clear();
        mConfigHelper.init(opCode);
    }

    /* @hide */
    public synchronized void resetFeatureStorage() {
        Log.d(TAG, "resetFeatureStorage()");
        mFeatureHelper.clear();
    }

    private static class FeatureHelper {
        private int mPhoneId;
        private Context mContext = null;
        private ContentResolver mContentResolver = null;

        FeatureHelper(Context context, int phoneId) {
            mPhoneId = phoneId;
            mContext = context;
            mContentResolver = mContext.getContentResolver();
        }

        private void initFeatureStorage() {
            /*
            // VoLTE
            boolean VltEnabled = ImsManager.isVolteEnabledByPlatform(mContext) &&
                    ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext);
            int value = VltEnabled ? ImsConfig.FeatureValueConstants.ON : ImsConfig.FeatureValueConstants.OFF;
            addFeature(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE, value);

            // ViLTE
            boolean LvcEnabled = ImsManager.isVtEnabledByPlatform(mContext) &&
                    ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext);
            value = LvcEnabled ? ImsConfig.FeatureValueConstants.ON : ImsConfig.FeatureValueConstants.OFF;
            addFeature(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE, value);

            // VoWIFI
            boolean WfcEnabled = ImsManager.isWfcEnabledByPlatform(mContext) &&
                    ImsManager.isWfcEnabledByUser(mContext);
            value = WfcEnabled ? ImsConfig.FeatureValueConstants.ON : ImsConfig.FeatureValueConstants.OFF;
            addFeature(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI, value);

            // ViWIFI Not support currently
            value = ImsConfig.FeatureValueConstants.OFF;
            addFeature(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI, value);
            */
        }

        private void clear() {
            String selection = ImsConfigContract.BasicConfigTable.PHONE_ID + " = ?";
            String[] args = {String.valueOf(mPhoneId)};
            mContentResolver.delete(ImsConfigContract.Feature.CONTENT_URI, selection, args);
        }

        private void updateFeature(int featureId, int network, int value) {
            boolean result = false;
            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.Feature.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.Feature.FEATURE_ID, featureId);
            cv.put(ImsConfigContract.Feature.NETWORK_ID, network);
            cv.put(ImsConfigContract.Feature.VALUE, value);

            // Check exist or not
            try {
                getFeatureValue(featureId, network);
                mContentResolver.update(
                        ImsConfigContract.Feature.getUriWithFeatureId(mPhoneId, featureId, network),
                        cv, null, null);
            } catch (ImsException e) {
                mContentResolver.insert(ImsConfigContract.Feature.CONTENT_URI, cv);
            }
        }

        int getFeatureValue(int featureId, int network) throws ImsException {
            int result = -1;
            String[] projection = {
                    ImsConfigContract.Feature.PHONE_ID,
                    ImsConfigContract.Feature.FEATURE_ID,
                    ImsConfigContract.Feature.NETWORK_ID,
                    ImsConfigContract.Feature.VALUE};
            Cursor c = mContentResolver.query(
                    ImsConfigContract.Feature.getUriWithFeatureId(mPhoneId, featureId, network),
                    projection, null, null, null);
            if (c != null && c.getCount() == 1) {
                c.moveToFirst();
                int valueIndex = c.getColumnIndex(ImsConfigContract.Feature.VALUE);
                result = c.getInt(valueIndex);
                c.close();
            } else {
                throw new ImsException("Feature " + featureId + " not assigned with value!",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
            }
            return result;
        }
    }

    private static class ConfigHelper {
        private Context mContext = null;
        private ContentResolver mContentResolver = null;
        private int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        DefaultConfigPolicyFactory mDefConfigPolicyFactory = null;
        private String mOpCode = ImsConfigContract.Operator.OP_NONE;
        private Handler mHandler = null;
        private boolean mInitDone = false;

        ConfigHelper(Context context, Handler handler, int phoneId) {
            mContext = context;
            mHandler = handler;
            mPhoneId = phoneId;
            mContentResolver = mContext.getContentResolver();

            try {
                mOpCode = getConfigSetting(ImsConfigContract.ConfigSetting.SETTING_ID_OPCODE);
            } catch (ImsException e) {
                mOpCode = ImsConfigContract.Operator.OP_NONE;
            }
        }

        synchronized void setOpCode(String opCode) {
            mOpCode = opCode;
        }

        synchronized String getOpCode() {
            return mOpCode;
        }

        synchronized void setInitDone(boolean done) {
            mInitDone = done;

            // Send broadcast to notify observers that storage is restored.
            Intent intent = new Intent(ImsConfigContract.ACTION_CONFIG_LOADED);
            intent.putExtra(ImsConfigContract.EXTRA_PHONE_ID, mPhoneId);
            mContext.sendBroadcast(intent);
        }

        synchronized boolean isInitDone() {
            return mInitDone;
        }

        synchronized void init() {
            initDefaultStorage(ImsConfigContract.Operator.OP_DEFAULT);
            initMasterStorage();
        }

        synchronized void init(String opCode) {
            initDefaultStorage(opCode);
            initMasterStorage();
            // To remember config storage.
            initConfigSettingStorage(opCode);
        }

        synchronized public boolean isStorageInitialized() {
            boolean initialized = false;
            String[] projection = {
                    ImsConfigContract.ConfigSetting.PHONE_ID,
                    ImsConfigContract.ConfigSetting.SETTING_ID,
                    ImsConfigContract.ConfigSetting.VALUE};
            Cursor c = mContentResolver.query(
                    ImsConfigContract.ConfigSetting.getUriWithSettingId(mPhoneId,
                            ImsConfigContract.ConfigSetting.SETTING_ID_OPCODE),
                    projection, null, null, null);

            if (c != null && c.getCount() == 1) {
                initialized = true;
            }
            return initialized;
        }

        synchronized public boolean getImsProvisioned(int feature) {
            int result = 0;
            try {
                switch (feature) {
                    // VoLTE
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE:
                        result = getConfigValue(ImsConfigContract.TABLE_MASTER,
                                ImsConfig.ConfigConstants.WFC_SETTING_ENABLED);
                        break;
                    // WFC
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI:
                        result = getConfigValue(ImsConfigContract.TABLE_MASTER,
                                ImsConfig.ConfigConstants.VLT_SETTING_ENABLED);
                        break;
                    default:
                    // Do nothing
                    break;
                }
            } catch (ImsException e) {
                Log.e(TAG, "getImsProvisioned(" + feature + ") failed");
            }
            return (result == 1) ? true : false;
        }

        public synchronized void initConfigStorageOnOpChanged() {
            TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String simOperator = tm.getSimOperatorNumericForPhone(mPhoneId);
            String currentOpCode = getOpCode();
            if (!TextUtils.isEmpty(simOperator)) {
                String code = PlmnTable.getOperatorCode(Integer.valueOf(simOperator));

                switch (currentOpCode) {
                    case ImsConfigContract.Operator.OP_NONE:
                        // Try to load one
                        Log.d(TAG, "First load opCode = " + code +
                                " on phone " + mPhoneId);
                        loadConfigStorage(mHandler, code);
                        break;
                    default:
                        // Try to load if changed
                        if (!currentOpCode.equals(code)) {
                            Log.d(TAG, "Find new opCode " + code + " to load, current = " +
                                    currentOpCode + " on phone " + mPhoneId);
                            loadConfigStorage(mHandler, code);
                        } else {
                            Log.d(TAG, "Ignore loading same opCode " + code +
                                    " on phone " + mPhoneId);
                            setInitDone(true);
                        }
                        break;
                }
            } else {
                if (ImsConfigContract.Operator.OP_NONE.equals(currentOpCode)) {
                    Log.d(TAG, "Empty IMSI on phone " + mPhoneId + ", reload when sim ready");
                } else {
                    Log.d(TAG, "Empty IMSI on phone " + mPhoneId +
                            ", already load for " + currentOpCode + ", reload when sim ready");
                    setInitDone(true);
                }
            }
        }

        static void loadConfigStorage(Handler handler, String code) {
            if (handler != null) {
                // Remove previous load event
                handler.removeMessages(ImsConfigStorage.MSG_LOAD_CONFIG_STORAGE);
                Message msg = new Message();
                if (DEBUG) {
                    Log.d(TAG, "LoadConfigStorage() msg = " + msg.hashCode());
                }
                msg.what = ImsConfigStorage.MSG_LOAD_CONFIG_STORAGE;
                msg.obj = code;
                handler.sendMessage(msg);
            }
        }

        private void initConfigSettingStorage(String mOpCode) {
            addConfigSetting(ImsConfigContract.ConfigSetting.SETTING_ID_OPCODE, mOpCode);
        }

        private void addConfigSetting(int id, String value) {
            Uri result = null;
            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.ConfigSetting.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.ConfigSetting.SETTING_ID, id);
            cv.put(ImsConfigContract.ConfigSetting.VALUE, value);
            result = mContentResolver.insert(ImsConfigContract.ConfigSetting.CONTENT_URI, cv);
            if (result == null) {
                throw new IllegalArgumentException("addConfigSetting " + id +
                        " for phone " + mPhoneId + " failed!");
            }
        }

        private void updateConfigSetting(int id, int value) {
            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.ConfigSetting.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.ConfigSetting.SETTING_ID, id);
            cv.put(ImsConfigContract.ConfigSetting.VALUE, value);
            Uri uri = ImsConfigContract.getConfigUri(
                    ImsConfigContract.TABLE_CONFIG_SETTING, mPhoneId, id);
            int count = mContentResolver.update(uri, cv, null, null);
            if (count != 1) {
                throw new IllegalArgumentException("updateConfigSetting " + id +
                        " for phone " + mPhoneId + " failed!");
            }
        }

        private String getConfigSetting(int id) throws ImsException {
            String result = "";
            String[] projection = {
                    ImsConfigContract.ConfigSetting.PHONE_ID,
                    ImsConfigContract.ConfigSetting.SETTING_ID,
                    ImsConfigContract.ConfigSetting.VALUE};
            Cursor c = mContentResolver.query(
                    ImsConfigContract.ConfigSetting.getUriWithSettingId(mPhoneId, id),
                    projection, null, null, null);

            if (c != null && c.getCount() == 1) {
                c.moveToFirst();
                int index = c.getColumnIndex(ImsConfigContract.ConfigSetting.VALUE);
                result = c.getString(index);
                c.close();
            } else {
                throw new ImsException("getConfigSetting " + id + " for phone " +
                        mPhoneId + " not found", ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }
            return result;
        }

        private void initDefaultStorage(String opCode) {
            Map<Integer, ImsConfigSettings.Setting> configSettings =
                    ImsConfigSettings.getConfigSettings();
            Map<Integer, ImsConfigPolicy.DefaultConfig> defSettings =
                    new HashMap<Integer, ImsConfigPolicy.DefaultConfig>();

            mDefConfigPolicyFactory = DefaultConfigPolicyFactory.getInstance(opCode);
            defSettings = mDefConfigPolicyFactory.load();
            if (defSettings != null && defSettings.isEmpty()) {
                Log.d(TAG, "No default value");
                return;
            }

            for (Integer configId : configSettings.keySet()) {
                String value = ImsConfigContract.VALUE_NO_DEFAULT;
                int unitId = ImsConfigContract.Unit.UNIT_NONE;
                if (!mDefConfigPolicyFactory.hasDefaultValue(configId)) {
                    continue;
                }
                ImsConfigPolicy.DefaultConfig base = defSettings.get(configId);
                if (base != null) {
                    value = base.defVal;
                    unitId = base.unitId;
                }
                ImsConfigSettings.Setting setting = configSettings.get(configId);
                if (ImsConfigContract.MimeType.INTEGER == setting.mimeType) {
                    ContentValues cv = getConfigCv(
                            configId, setting.mimeType, Integer.parseInt(value));
                    cv.put(ImsConfigContract.Default.UNIT_ID, unitId);
                    mContentResolver.insert(ImsConfigContract.Default.CONTENT_URI, cv);
                } else if (ImsConfigContract.MimeType.STRING == setting.mimeType) {
                    ContentValues cv = getConfigCv(
                            configId, setting.mimeType, value);
                    cv.put(ImsConfigContract.Default.UNIT_ID, unitId);
                    mContentResolver.insert(ImsConfigContract.Default.CONTENT_URI, cv);
                } else { // For Object type
                    // ToDo: May be different Object type in IMS MO. ex. String.class.cast(obj)
                }
            }
        }

        private void initMasterStorage() {
            Map<Integer, ImsConfigSettings.Setting> configSettings =
                    ImsConfigSettings.getConfigSettings();

            for (Integer configId : configSettings.keySet()) {
                Cursor c = null;
                ContentValues cv = new ContentValues();
                boolean isFoundInNvRam = false;
                boolean isFoundInAny = true;

                try {
                    // Step 1: TODO: Phase II Get / Load provisioned value from RAM if exist
                    // isfoundInNvRAM = true;
                    throw new ImsException("here", ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
                } catch (ImsException e) {
                    // Step 2: Try to get default value.
                    try {
                        c = getConfigFirstCursor(ImsConfigContract.TABLE_DEFAULT, configId);
                        if (c != null) {
                            int phoneIdIndex =
                                    c.getColumnIndex(ImsConfigContract.BasicConfigTable.PHONE_ID);
                            int configIndex =
                                    c.getColumnIndex(ImsConfigContract.BasicConfigTable.CONFIG_ID);
                            int mimeTypeIndex =
                                    c.getColumnIndex(ImsConfigContract.Master.MIMETYPE_ID);
                            int dataIndex =
                                    c.getColumnIndex(ImsConfigContract.BasicConfigTable.DATA);
                            cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID,
                                    c.getInt(phoneIdIndex));
                            cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID,
                                    c.getInt(configIndex));
                            cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID,
                                    c.getInt(mimeTypeIndex));
                            cv.put(ImsConfigContract.BasicConfigTable.DATA,
                                    c.getString(dataIndex));
                            Log.d(TAG, "Load default value " + c.getString(dataIndex) +
                                    " for config " + configId);
                            c.close();
                        }
                    } catch (ImsException e2) {
                        isFoundInAny = false;
                    }
                }
                if (!isFoundInAny || c == null) {
                    cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID, mPhoneId);
                    cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID, configId);
                    cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID,
                            ImsConfigSettings.getMimeType(configId));
                    cv.put(ImsConfigContract.BasicConfigTable.DATA,
                            ImsConfigContract.VALUE_NO_DEFAULT);
                }

                // Step 3: Add to master db
                mContentResolver.insert(ImsConfigContract.Master.CONTENT_URI, cv);

                // Step4: Record provisioned data if found any
                if (isFoundInNvRam) {
                    mContentResolver.insert(ImsConfigContract.Provision.CONTENT_URI, cv);
                }
            }
        }

        private void clear() {
            String selection = ImsConfigContract.BasicConfigTable.PHONE_ID + " = ?";
            String[] args = {String.valueOf(mPhoneId)};
            mContentResolver.delete(ImsConfigContract.ConfigSetting.CONTENT_URI, selection, args);
            mContentResolver.delete(ImsConfigContract.Provision.CONTENT_URI, selection, args);
            mContentResolver.delete(ImsConfigContract.Master.CONTENT_URI, selection, args);
            mContentResolver.delete(ImsConfigContract.Default.CONTENT_URI, selection, args);
        }
        private ContentValues getConfigCv(int configId, int mimeType, int value) {
            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID, configId);
            cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID, mimeType);
            cv.put(ImsConfigContract.BasicConfigTable.DATA, value);
            return cv;
        }

        private ContentValues getConfigCv(int configId, int mimeType, String value) {
            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID, configId);
            cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID, mimeType);
            cv.put(ImsConfigContract.BasicConfigTable.DATA, value);
            return cv;
        }

        private Uri addConfig(String table, int configId, int mimeType, int value)
                throws ImsException {
            enforceConfigId(configId);

            ContentValues cv = getConfigCv(configId, mimeType, value);
            return mContentResolver.insert(ImsConfigContract.getTableUri(table), cv);
        }

        private Uri addConfig(String table, int configId, int mimeType, String value)
                throws ImsException {
            enforceConfigId(configId);

            ContentValues cv = getConfigCv(configId, mimeType, value);
            return mContentResolver.insert(ImsConfigContract.getTableUri(table), cv);
        }

        private int updateConfig(String table, int configId, int mimeType, int value)
                throws ImsException {
            enforceConfigId(configId);

            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID, configId);
            cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID, mimeType);
            cv.put(ImsConfigContract.BasicConfigTable.DATA, value);

            return mContentResolver.update(
                    ImsConfigContract.getConfigUri(table, mPhoneId, configId), cv, null, null);
        }

        private int updateConfig(String table, int configId, int mimeType, String value)
                throws ImsException {
            enforceConfigId(configId);

            ContentValues cv = new ContentValues();
            cv.put(ImsConfigContract.BasicConfigTable.PHONE_ID, mPhoneId);
            cv.put(ImsConfigContract.BasicConfigTable.CONFIG_ID, configId);
            cv.put(ImsConfigContract.BasicConfigTable.MIMETYPE_ID, mimeType);
            cv.put(ImsConfigContract.BasicConfigTable.DATA, value);

            return mContentResolver.update(
                    ImsConfigContract.getConfigUri(table, mPhoneId, configId), cv, null, null);
        }

        private Cursor getConfigFirstCursor(String table, int configId) throws ImsException {
            String[] projection = {
                    ImsConfigContract.BasicConfigTable.PHONE_ID,
                    ImsConfigContract.BasicConfigTable.CONFIG_ID,
                    ImsConfigContract.BasicConfigTable.MIMETYPE_ID,
                    ImsConfigContract.BasicConfigTable.DATA};
            Uri uri = ImsConfigContract.getConfigUri(table, mPhoneId, configId);
            Cursor c = mContentResolver.query(uri, projection, null, null, null);
            if (c != null) {
                if (c.getCount() == 1) {
                    c.moveToFirst();
                    return c;
                } else if (c.getCount() == 0) {
                    throw new ImsException("Config " + configId +
                            " shall exist in table: " + table,
                            ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
                } else {
                    throw new ImsException("Config " + configId +
                            " shall exist once in table: " + table,
                            ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
                }
            } else {
                throw new ImsException("Null cursor with config: " +
                        configId + " in table: " + table,
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
            }
        }

        private int getConfigValue(String table, int configId) throws ImsException {
            enforceConfigId(configId);

            Cursor c = getConfigFirstCursor(table, configId);
            int dataIndex = c.getColumnIndex(ImsConfigContract.BasicConfigTable.DATA);
            int mimeTypeIndex = c.getColumnIndex(ImsConfigContract.Master.MIMETYPE_ID);
            int mimeType = c.getInt(mimeTypeIndex);

            enforceDefaultValue(configId, c.getString(dataIndex));
            if (mimeType != ImsConfigContract.MimeType.INTEGER) {
                throw new ImsException("Config " + configId +
                        " shall be type " + ImsConfigContract.MimeType.INTEGER +
                        ", but " + mimeType, ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
            }
            int result = Integer.parseInt(c.getString(dataIndex));
            c.close();
            return result;
        }

        private String getConfigStringValue(String table, int configId) throws ImsException {
            enforceConfigId(configId);

            Cursor c = getConfigFirstCursor(table, configId);
            int dataIndex = c.getColumnIndex(ImsConfigContract.BasicConfigTable.DATA);
            int mimeTypeIndex = c.getColumnIndex(ImsConfigContract.Master.MIMETYPE_ID);
            int mimeType = c.getInt(mimeTypeIndex);

            enforceDefaultValue(configId, c.getString(dataIndex));
            if (mimeType != ImsConfigContract.MimeType.STRING) {
                throw new ImsException("Config " + configId +
                        " shall be type " + ImsConfigContract.MimeType.STRING +
                        ", but " + mimeType, ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
            }
            String result = c.getString(dataIndex);
            c.close();
            return result;
        }

        private void enforceDefaultValue(int configId, String data) throws ImsException {
            if (ImsConfigContract.VALUE_NO_DEFAULT.equals(data)) {
                throw new ImsException("No deafult value for config " +
                        configId, ImsReasonInfo.CODE_UNSPECIFIED);
            }
        }

        private void enforceConfigId(int configId) throws ImsException {
            if (!ImsConfigContract.Validator.isValidConfigId(configId)) {
                throw new ImsException("No deafult value for config " +
                        configId, ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
            }
        }
    }
}
