package com.mediatek.ims.config;

import com.android.ims.ImsConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Class mapping setting strings in carrier configuration xml to ImsConfig item,
 * and mapping IMS configuration to class type, inorder to recover string data
 * back to its original class.
 */
public class ImsConfigSettings {
    private static HashMap<Integer, Setting> sImsConfigurations =
            new HashMap<Integer, Setting>();

    static {
        buildConfigSettings();
    }

    private static void buildConfigSettings() {
        sImsConfigurations.put(ImsConfig.ConfigConstants.VOCODER_AMRMODESET,
                new Setting(String.class, ImsConfigContract.MimeType.STRING));
        sImsConfigurations.put(ImsConfig.ConfigConstants.VOCODER_AMRWBMODESET,
                new Setting(String.class, ImsConfigContract.MimeType.STRING));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SIP_SESSION_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.MIN_SE,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.CANCELLATION_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.TDELAY,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SILENT_REDIAL_ENABLE,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SIP_T1_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SIP_T2_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SIP_TF_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.VLT_SETTING_ENABLED,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.LVC_SETTING_ENABLED,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.DOMAIN_NAME,
                new Setting(String.class, ImsConfigContract.MimeType.STRING));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SMS_FORMAT,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SMS_OVER_IP,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.PUBLISH_TIMER,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.PUBLISH_TIMER_EXTENDED,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.CAPABILITIES_CACHE_EXPIRATION,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.AVAILABILITY_CACHE_EXPIRATION,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.CAPABILITIES_POLL_INTERVAL,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.SOURCE_THROTTLE_PUBLISH,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.MAX_NUMENTRIES_IN_RCL,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.CAPAB_POLL_LIST_SUB_EXP,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.GZIP_FLAG,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.EAB_SETTING_ENABLED,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.VOICE_OVER_WIFI_ROAMING,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.VOICE_OVER_WIFI_MODE,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
        sImsConfigurations.put(ImsConfig.ConfigConstants.WFC_SETTING_ENABLED,
                new Setting(Integer.class, ImsConfigContract.MimeType.INTEGER));
    }

    public static class Setting {
        public Setting(Class _clazz, int _mimeType) {
            clazz = _clazz;
            mimeType = _mimeType;
        }
        public Class clazz;
        public int mimeType;
    }

    public static Map<Integer, Setting> getConfigSettings() {
        return sImsConfigurations;
    }

    public static int getMimeType(int configId) {
        Setting s = sImsConfigurations.get(configId);
        return s.mimeType;
    }
}
