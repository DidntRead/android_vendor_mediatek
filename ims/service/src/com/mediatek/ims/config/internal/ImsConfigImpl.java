package com.mediatek.ims;

import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.os.SystemProperties;
import java.util.Arrays;
import android.util.Log;

import com.android.ims.ImsConfig;
import com.android.ims.ImsConfigListener;
import com.android.ims.ImsException;
import com.android.ims.internal.IImsConfig;

import com.mediatek.ims.ImsRILAdapter;
import com.mediatek.ims.config.internal.ImsConfigStorage;


/**
 * Class handles IMS parameter provisioning by carrier.
 *
 *  @hide
 */
public class ImsConfigImpl extends IImsConfig.Stub {
    private static final String TAG = "ImsConfig";
    private static final boolean DEBUG = true;

    private Context mContext;
    private int mPhoneId;
    private ImsRILAdapter mRilAdapter;
    private ImsConfigStorage mStorage = null;
    private static final String PROPERTY_VOLTE_ENALBE = "persist.mtk.volte.enable";
    private static final String PROPERTY_WFC_ENALBE = "persist.mtk.wfc.enable";
    private static final String PROPERTY_IMS_VIDEO_ENALBE = "persist.mtk.ims.video.enable";
    private static boolean mVolteCapability = false;
    private static boolean mVilteCapability = false;
    private static boolean mWfcCapability = false;
    private boolean[] mImsCapabilityArr = new boolean[3];
    private ImsConfigImpl() {};

    /**
     *
     * Construction function for ImsConfigImpl.
     *
     * @param context the application context
     * @param phoneId the phone id this instance handle for
     *
     */
    public ImsConfigImpl(Context context, ImsRILAdapter imsRilAdapter, int phoneId) {
        mContext = context;
        mPhoneId = phoneId;
        mRilAdapter = imsRilAdapter;
        mStorage = new ImsConfigStorage(mContext, phoneId);
        Arrays.fill(mImsCapabilityArr, false);
        // handle ECC scenario, set VoLTE as true by default
        mImsCapabilityArr[0] = true;
    }

    /**
     * Gets the value for ims service/capabilities parameters from the master
     * value storage. Synchronous blocking call.
     *
     * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in Integer format.
     */
    @Override
    public int getProvisionedValue(int item) {
        try {
            int result = mStorage.getProvisionedValue(item);
            if (DEBUG) Log.d(TAG, "getProvisionedValue(" +
                    item + ") : " + result + " on phone" + mPhoneId);
            return result;
        } catch (ImsException e) {
            Log.e(TAG, "getProvisionedValue(" + item + ") failed, code: " + e.getCode());
            throw new RuntimeException(e);
        }
    }


    /**
     * Gets the value for ims service/capabilities parameters from the master"
     * value storage. Synchronous blocking call.
     *
     * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in String format.
     */
    @Override
    public String getProvisionedStringValue(int item) {
        try {
            String result = mStorage.getProvisionedStringValue(item);
            if (DEBUG) Log.d(TAG, "getProvisionedStringValue(" +
                    item + ") : " + result + " on phone " + mPhoneId);
            return result;
        } catch (ImsException e) {
            Log.e(TAG, "getProvisionedStringValue(" + item + ") failed, code: " + e.getCode());
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the value for IMS service/capabilities parameters by the operator device
     * management entity. It sets the config item value in the provisioned storage
     * from which the master value is derived. Synchronous blocking call.
     *
     * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in Integer format.
     */
    @Override
    public int setProvisionedValue(int item, int value) {
        try {
            if (DEBUG) Log.d(TAG, "setProvisionedValue(" +
                    item + ", " + value + ") on phone " + mPhoneId +
                    " from pid " + Binder.getCallingPid() + ", uid " + Binder.getCallingUid());
            mStorage.setProvisionedValue(item, value);
        } catch (ImsException e) {
            Log.e(TAG, "setProvisionedValue(" + item + ") failed, code: " + e.getCode());
            return ImsConfig.OperationStatusConstants.FAILED;
        }
        return ImsConfig.OperationStatusConstants.SUCCESS;
    }

    /**
     * Sets the value for IMS service/capabilities parameters by the operator device
     * management entity. It sets the config item value in the provisioned storage
     * from which the master value is derived.  Synchronous blocking call.
     *
     * @param item as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in String format.
     */
    @Override
    public int setProvisionedStringValue(int item, String value) {
        try {
            if (DEBUG) Log.d(TAG, "setProvisionedStringValue(" +
                    item + ", " + value + ") on phone " + mPhoneId +
                    " from pid " + Binder.getCallingPid() + ", uid " + Binder.getCallingUid());
            mStorage.setProvisionedStringValue(item, value);
        } catch (ImsException e) {
            Log.e(TAG, "setProvisionedValue(" + item + ") failed, code: " + e.getCode());
            return ImsConfig.OperationStatusConstants.FAILED;
        }
        return ImsConfig.OperationStatusConstants.SUCCESS;
    }

    /**
     * Gets the value of the specified IMS feature item for specified network type.
     * This operation gets the feature config value from the master storage (i.e. final
     * value) asynchronous non-blocking call.
     *
     * @param feature as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param listener feature value returned asynchronously through listener.
     */
    @Override
    public void getFeatureValue(int feature, int network, ImsConfigListener listener) {
        try {
            try {
                int value = mStorage.getFeatureValue(feature, network);
                if (DEBUG) Log.d(TAG, "getFeatureValue(" +
                      feature + ", " + network + ") : " + value + " on phone " + mPhoneId);
                listener.onGetFeatureResponse(
                        feature, network, value, ImsConfig.OperationStatusConstants.SUCCESS);
            } catch (ImsException e) {
                Log.e(TAG, "getFeatureValue(" + feature + ") failed, code: " + e.getCode());
                // Return OFF if failed
                listener.onGetFeatureResponse(
                        feature, network, ImsConfig.FeatureValueConstants.OFF,
                        ImsConfig.OperationStatusConstants.FAILED);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getFeatureValue(" + feature + ") remote failed!");
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the value for IMS feature item for specified network type.
     * This operation stores the user setting in setting db from which master db
     * is dervied.
     *
     * @param feature as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value as defined in com.android.ims.ImsConfig#FeatureValueConstants.
     * @param listener provided if caller needs to be notified for set result.
     */
    @Override
    public void setFeatureValue(int feature, int network, int value, ImsConfigListener listener) {
        try {
            try {
                if (DEBUG) Log.d(TAG, "setFeatureValue(" +
                      feature + ", " + network + ", " + value + ") on phone " + mPhoneId +
                      " from pid " + Binder.getCallingPid() + ", uid " + Binder.getCallingUid() +
                      ", listener " + listener);
                mStorage.setFeatureValue(feature, network, value);

                switch(feature) {
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE:
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI:
                        int oldVideoValue = SystemProperties.getInt(PROPERTY_IMS_VIDEO_ENALBE, 0);
                        if (value != oldVideoValue) {
                            if (value == ImsConfig.FeatureValueConstants.ON) {
                                SystemProperties.set(PROPERTY_IMS_VIDEO_ENALBE,"1");
                                mRilAdapter.turnOnImsVideo(null);
                            } else {
                                SystemProperties.set(PROPERTY_IMS_VIDEO_ENALBE,"0");
                                mRilAdapter.turnOffImsVideo(null);
                            }
                        }
                        break;
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI:
                        int oldWfcValue = SystemProperties.getInt(PROPERTY_WFC_ENALBE, 0);
                        int volteEnable = SystemProperties.getInt(PROPERTY_VOLTE_ENALBE, 0);
                        if (value != oldWfcValue) {
                            if (value == ImsConfig.FeatureValueConstants.ON) {
                                SystemProperties.set(PROPERTY_WFC_ENALBE,"1");
                                mRilAdapter.turnOnWfc(null);
                                if (volteEnable == 0){
                                    mRilAdapter.turnOnImsVoice(null);
                                }
                            } else {
                                SystemProperties.set(PROPERTY_WFC_ENALBE,"0");
                                mRilAdapter.turnOffWfc(null);
                                if (volteEnable == 0){
                                    mRilAdapter.turnOffImsVoice(null);
                                }
                            }
                        }
                        break;
                    case ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE:
                        int oldVoLTEValue = SystemProperties.getInt(PROPERTY_VOLTE_ENALBE, 0);                        
                        int wfcEnable = SystemProperties.getInt(PROPERTY_WFC_ENALBE, 0);
                        if (value != oldVoLTEValue) {
                            if (value == ImsConfig.FeatureValueConstants.ON) {
                                SystemProperties.set(PROPERTY_VOLTE_ENALBE,"1");                                
                                mRilAdapter.turnOnVolte(null);
                                if (wfcEnable == 0){
                                    mRilAdapter.turnOnImsVoice(null);
                                }
                            } else {
                                SystemProperties.set(PROPERTY_VOLTE_ENALBE,"0");
                                mRilAdapter.turnOffVolte(null);
                                if (wfcEnable == 0){
                                    mRilAdapter.turnOffImsVoice(null);
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
                if (listener != null) {
                    listener.onSetFeatureResponse(
                            feature, network, value, ImsConfig.OperationStatusConstants.SUCCESS);
                }
            } catch (ImsException e) {
                Log.e(TAG, "setFeatureValue(" + feature + ") failed, code: " + e.getCode());
                if (listener != null) {
                    // Return OFF if failed
                    listener.onSetFeatureResponse(
                            feature, network, ImsConfig.FeatureValueConstants.OFF,
                            ImsConfig.OperationStatusConstants.FAILED);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "setFeatureValue(" + feature + ") remote failed!");
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the value for IMS volte provisioned.
     * This should be the same as the operator provisioned value if applies.
     *
     * @return boolean
     */
    @Override
    public boolean getVolteProvisioned() {
        // Default true for ignoring provisioning result.
        boolean result = true;
        try {
            result = mStorage.getVolteProvisioned();
            if (DEBUG) Log.d(TAG, "getVolteProvisioned() : " + result);
        } catch (ImsException e) {
            Log.e(TAG, "getVolteProvisioned() failed!" + e);
        }
        return result;
    }

    /**
     * Gets the value for IMS wfc provisioned.
     * This should be the same as the operator provisioned value if applies.
     *
     * @return boolean
     */
    @Override
    public boolean getWfcProvisioned() {
        boolean result = true;
        try {
            result = mStorage.getWfcProvisioned();
            if (DEBUG) Log.d(TAG, "getWfcProvisioned() : " + result);
        } catch (ImsException e) {
            Log.e(TAG, "getWfcProvisioned() failed!" + e);
        }
        return result;
    }

    /**
     * Gets the value for IMS feature item for video call quality.
     *
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     */
    public void getVideoQuality(ImsConfigListener listener) {

    }

    /**
     * Sets the value for IMS feature item video quality.
     *
     * @param quality, defines the value of video quality.
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     */
     public void setVideoQuality(int quality, ImsConfigListener listener) {

     }

    /**
     * Sets the value for IMS capabilities
     *
     * @param volte in boolean format.
     * @param vilte in boolean format.
     * @param wfc in boolean format.
     * @return as true or false.
     */
    @Override
    public void setImsCapability(boolean volte, boolean vilte, boolean wfc) {
        mImsCapabilityArr[0] = volte;
        mImsCapabilityArr[1] = vilte;
        mImsCapabilityArr[2] = wfc;
    }

    /**
     * Gets the value for IMS capabilities.
     * @param capability.
     *
     * @return boolean
     */
    @Override
    public boolean getImsCapability(int capability) {
        return mImsCapabilityArr[capability];
    }
}
