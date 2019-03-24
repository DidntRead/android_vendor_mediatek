package com.mediatek.ims.config.op;

import com.android.ims.ImsConfig;
import com.mediatek.ims.config.ImsConfigPolicy;

public class Op06ConfigPolicy extends ImsConfigPolicy {
    public Op06ConfigPolicy() {
        super("Op06ConfigPolicy");
    }

    public boolean onSetDefaultValue(int configId, ImsConfigPolicy.DefaultConfig config) {
        boolean set = true;
        switch (configId) {
            case ImsConfig.ConfigConstants.WFC_SETTING_ENABLED:
                config.defVal = "0";
                break;
            default:
                set = false;
                break;
        }
        return set;
    }
}
