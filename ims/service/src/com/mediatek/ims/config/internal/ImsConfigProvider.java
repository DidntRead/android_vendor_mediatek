package com.mediatek.ims.config.internal;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.mediatek.ims.config.ImsConfigContract;
import static com.mediatek.ims.config.ImsConfigContract.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Provider class to handle DUI operation for IMS configuration databases, including
 * 1.) TABLE_CONFIG_SETTING: Basic settings for IMS configuration.
 * 2.) TABLE_DEFAULT: The default value of each configuration items, which will be loaded from
 *     xml resource file according to carrier's customization.
 * 3.) TABLE_PROVISION: The provisioned record from carrier.
 * 4.) TABLE_MASTER: The final result (cache) which combined with default & provisioned value.
 *     Items without setting any value will filled with value ImsConfigContract.VALUE_NO_DEFAULT,
 *     and application will get ImsException when calling getProvisioned(String)Value() API for
 *     such kind of configuration items.
 */
final public class ImsConfigProvider extends ContentProvider {
    static final boolean DEBUG = true;
    static final String TAG = "ImsConfig";
    static final String AUTHORITY = ImsConfigContract.AUTHORITY;

    SqlDatabaseHelper mDatabaseHelper = null;

    @Override
    public boolean onCreate() {
        mDatabaseHelper = new SqlDatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/imsconfig";
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int result = 0;
        Arguments args = new Arguments(OperationMode.MODE_DELETE, uri, selection, selectionArgs);
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        result = db.delete(args.table, args.selection, args.selectionArgs);
        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri result = null;
        Arguments args = new Arguments(OperationMode.MODE_INSERT, values, uri);
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        long newId = db.insertWithOnConflict(
                args.table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return prepareResultUri(args, newId);
    }

    private Uri prepareResultUri(Arguments args, long newId) {
        Uri result = Uri.parse("content://" + AUTHORITY + "/" + args.table + "/" + args.phoneId);
        switch (args.table) {
            case ImsConfigContract.TABLE_DEFAULT:
            case ImsConfigContract.TABLE_PROVISION:
            case ImsConfigContract.TABLE_MASTER:
                result = Uri.withAppendedPath(result, args.itemId);
                break;
            default:
                result = ContentUris.withAppendedId(result, newId);
                break;
        }
        if (!TextUtils.isEmpty(args.param)) {
            result = ContentUris.withAppendedId(result, Integer.parseInt(args.param));
        }
        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int count = 0;
        Arguments args = new Arguments(
                OperationMode.MODE_UPDATE, uri, values, selection, selectionArgs);
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        count = db.update(args.table, values, args.selection, args.selectionArgs);
        if (count > 0) {
            notifyChange(uri, args);
        }
        return count;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Cursor result = null;
        Arguments args = new Arguments(OperationMode.MODE_QUERY, uri, selection, selectionArgs);
        SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();

        result = db.query(args.table,
                projection,
                args.selection,
                args.selectionArgs,
                null,
                null,
                sortOrder);
        return result;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (mDatabaseHelper != null) {
            mDatabaseHelper.close();
        }
    }

    private void notifyChange(Uri uri, Arguments args) {
        //final long oldId = Binder.clearCallingIdentity();
        try {
            int itemId;
            switch (args.table) {
                case ImsConfigContract.TABLE_DEFAULT:
                case ImsConfigContract.TABLE_MASTER:
                case ImsConfigContract.TABLE_PROVISION:
                    itemId = ImsConfigContract.configNameToId(args.itemId);
                    break;
                case ImsConfigContract.TABLE_CONFIG_SETTING:
                case ImsConfigContract.TABLE_FEATURE:
                    itemId = Integer.parseInt(args.itemId);
                    break;
                default:
                    Log.e(TAG, "Invalid table " + args.table + " with uri " + uri);
                    return;
            }

            // For observers who don't have dedicated process, use broadcast mechanism.
            Intent intent = new Intent(ImsConfigContract.ACTION_CONFIG_UPDATE, uri);
            intent.putExtra(ImsConfigContract.EXTRA_PHONE_ID, Integer.parseInt(args.phoneId));
            intent.putExtra(ImsConfigContract.EXTRA_CONFIG_ID, itemId);
            getContext().sendBroadcast(intent);
            // Notify content observers
            getContext().getContentResolver().notifyChange(uri, null);

            if (DEBUG) {
                Log.d(TAG, "Update uri " + uri + " on phone " + args.phoneId);
            }
        } finally {
            //Binder.restoreCallingIdentity(oldId);
        }
    }

    // Process content://com.mediatek.ims.config.provider/$table/$phoneId/$itemId
    private static class Arguments {
        public String table = null;
        public String phoneId = null;
        public String itemId = null;
        public String param = null;
        public String selection = null;
        public String[] selectionArgs = null;

        private static final int INDEX_TABLE = 0;
        private static final int INDEX_PHONE_ID = 1;
        private static final int INDEX_ITEM_ID = 2;
        private static final int INDEX_PARAM_ID = 3;

        Arguments(int opMode, Uri uri, ContentValues cv, String selection, String[] selectionArgs) {
            String[] args = null;

            enforceValidUri(uri);
            this.table = getValidTable(uri);
            parseContentValue(uri, this.table, opMode, cv);
            enforceOpMode(opMode, uri, cv, selection, selectionArgs);

            int urlArgSize = uri.getPathSegments().size();
            switch (urlArgSize) {
                case 1: // Table-level
                    this.selection = selection;
                    this.selectionArgs = selectionArgs;
                    if (opMode == OperationMode.MODE_UPDATE ||
                            opMode == OperationMode.MODE_INSERT) {
                        this.phoneId = cv.getAsString(ImsConfigContract.ConfigSetting.PHONE_ID);
                    }
                    break;
                case 2: // Phone-level
                    this.phoneId = uri.getPathSegments().get(INDEX_PHONE_ID);
                    args = new String[1];
                    args[0] = this.phoneId;
                    this.selection = ImsConfigContract.ConfigSetting.PHONE_ID + " = ?";
                    if (!TextUtils.isEmpty(selection)) {
                        this.selection += " AND " + selection;
                        this.selectionArgs = join(args, selectionArgs);
                    } else {
                        this.selectionArgs = args;
                    }
                    break;
                case 3: // Item-level
                    this.phoneId = uri.getPathSegments().get(INDEX_PHONE_ID);
                    this.itemId = uri.getPathSegments().get(INDEX_ITEM_ID);
                    args = new String[2];
                    args[0] = this.phoneId;
                    switch (this.table) {
                        case ImsConfigContract.TABLE_CONFIG_SETTING:
                            args[1] = this.itemId;
                            this.selection = ImsConfigContract.ConfigSetting.PHONE_ID + " = ?" +
                                    " AND " + ImsConfigContract.ConfigSetting.SETTING_ID + " = ?";
                            if (!TextUtils.isEmpty(selection)) {
                                this.selection += " AND " + selection;
                                this.selectionArgs = join(args, selectionArgs);
                            } else {
                                this.selectionArgs = args;
                            }
                            break;
                        case ImsConfigContract.TABLE_DEFAULT:
                        case ImsConfigContract.TABLE_PROVISION:
                        case ImsConfigContract.TABLE_MASTER:
                            // Convert config name to id
                            args[1] = String.valueOf(ImsConfigContract.configNameToId(this.itemId));
                            this.selection = ImsConfigContract.BasicConfigTable.PHONE_ID + " = ?" +
                                    " AND " + ImsConfigContract.BasicConfigTable.CONFIG_ID + " = ?";
                            if (!TextUtils.isEmpty(selection)) {
                                this.selection += " AND " + selection;
                                this.selectionArgs = join(args, selectionArgs);
                            } else {
                                this.selectionArgs = args;
                            }
                            break;
                    }
                    break;
                case 4: // feature update / delete
                    this.phoneId = uri.getPathSegments().get(INDEX_PHONE_ID);
                    this.itemId = uri.getPathSegments().get(INDEX_ITEM_ID);
                    this.param = uri.getPathSegments().get(INDEX_PARAM_ID);
                    args = new String[3];
                    args[0] = this.phoneId;
                    args[1] = this.itemId;
                    args[2] = this.param;
                    switch (this.table) {
                        case ImsConfigContract.TABLE_FEATURE:

                            this.selection = ImsConfigContract.Feature.PHONE_ID + " = ?" +
                                    " AND " + ImsConfigContract.Feature.FEATURE_ID + " = ?" +
                                    " AND " + ImsConfigContract.Feature.NETWORK_ID + " = ?";
                            if (!TextUtils.isEmpty(selection)) {
                                this.selection += " AND " + selection;
                                this.selectionArgs = join(args, selectionArgs);
                            } else {
                                this.selectionArgs = args;
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid URI: " + uri);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid URI: " + uri);
            }
        }
        Arguments(int opMode, Uri uri, String selection, String[] selectionArgs) {
            this(opMode, uri, null, selection, selectionArgs);
        }
        Arguments(int opMode, ContentValues cv, Uri uri) {
            this(opMode, uri, cv, null, null);
        }
        Arguments(int opMode, Uri uri) {
            this(opMode, uri, null, null, null);
        }

        private static String[] join(String[]...arrays) {

            final List<String> output = new ArrayList<String>();
            for (String[] array : arrays) {
                output.addAll(Arrays.asList(array));
            }
            return output.toArray(new String[output.size()]);
        }

        private String getValidTable(Uri uri) {
            String table = uri.getPathSegments().get(INDEX_TABLE);
            enforceValidTable(table);
            return table;
        }

        private static void enforceOpMode(int opMode, Uri uri,
                ContentValues cv, String selection, String[] selectionArgs) {

        }

        private static void enforceValidTable(String table) {
            if (!Validator.isValidTable(table)) {
                throw new IllegalArgumentException("Bad table: " + table);
            }
        }

        private static void enforceValidUri(Uri uri) {
            if (uri == null) {
                throw new IllegalArgumentException("Bad request: null url");
            }
            if (uri.getPathSegments().size() == 0) {
                throw new IllegalArgumentException("Operate on entire database is not supported");
            }
        }

        private void parseContentValue(
                Uri uri, String table, int opMode, ContentValues cv) {
            if (opMode == OperationMode.MODE_QUERY || opMode == OperationMode.MODE_DELETE) {
                return;
            }
            enforceValidTable(table);

            this.phoneId = String.valueOf(
                    cv.getAsInteger(ImsConfigContract.ConfigSetting.PHONE_ID));
            if (TextUtils.isEmpty(this.phoneId)) {
                throw new IllegalArgumentException("Expect phone id in cv with " + uri);
            }

            int configId = 0;
            switch (table) {
                case ImsConfigContract.TABLE_CONFIG_SETTING:

                    int settingId = cv.getAsInteger(ImsConfigContract.ConfigSetting.SETTING_ID);
                    if (!Validator.isValidSettingId(settingId)) {
                        throw new IllegalArgumentException(
                                "Invalid setting id in cv: " + settingId + " with " + uri);
                    }
                    this.itemId = String.valueOf(settingId);
                    break;
                case ImsConfigContract.TABLE_FEATURE:
                    int featureId = cv.getAsInteger(ImsConfigContract.Feature.FEATURE_ID);
                    if (!Validator.isValidFeatureId(featureId)) {
                        throw new IllegalArgumentException(
                                "Invalid feature id in cv: " + featureId + " with " + uri);
                    }
                    this.itemId = String.valueOf(featureId);
                    int network = cv.getAsInteger(ImsConfigContract.Feature.NETWORK_ID);
                    if (!Validator.isValidNetwork(network)) {
                        throw new IllegalArgumentException(
                                "Invalid network in cv: " + network + " with " + uri);
                    }
                    this.param = String.valueOf(network);

                    int value = cv.getAsInteger(ImsConfigContract.Feature.VALUE);
                    if(!Validator.isValidFeatureValue(value)) {
                        throw new IllegalArgumentException(
                                "Invalid value in cv: " + value + " with " + uri);
                    }
                    break;
                case ImsConfigContract.TABLE_DEFAULT:
                    if (cv.containsKey(ImsConfigContract.Default.UNIT_ID)) {
                        int timeUnitId = cv.getAsInteger(ImsConfigContract.Default.UNIT_ID);
                        if (!Validator.isValidUnitId(timeUnitId)) {
                            throw new IllegalArgumentException(
                                    "Invalid time unit in cv: " + timeUnitId + " with " + uri);
                        }
                    }
                    configId = cv.getAsInteger(ImsConfigContract.BasicConfigTable.CONFIG_ID);
                    if (!Validator.isValidConfigId(configId)) {
                        throw new IllegalArgumentException(
                                "Invalid config id in cv: " + configId + " with " + uri);
                    }
                    this.itemId = ImsConfigContract.configIdToName(configId);
                case ImsConfigContract.TABLE_PROVISION:
                case ImsConfigContract.TABLE_MASTER:
                    int mimeTypeId = cv.getAsInteger(
                            ImsConfigContract.BasicConfigTable.MIMETYPE_ID);
                    if (!Validator.isValidMimeTypeId(mimeTypeId)) {
                        throw new IllegalArgumentException(
                                "Invalid mime type in cv: " + mimeTypeId + " with " + uri);
                    }
                    configId = cv.getAsInteger(ImsConfigContract.BasicConfigTable.CONFIG_ID);
                    if (!Validator.isValidConfigId(configId)) {
                        throw new IllegalArgumentException(
                                "Invalid config id in cv: " + configId + " with " + uri);
                    }
                    this.itemId = ImsConfigContract.configIdToName(configId);
                    break;
            }
        }
    }

    private static class OperationMode {
        static final int MODE_QUERY = 0;
        static final int MODE_INSERT = 1;
        static final int MODE_UPDATE = 2;
        static final int MODE_DELETE = 3;
    }
}
