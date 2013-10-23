// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.runtime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.Class;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This internal class parses manifest.json of current app.
 */
class XWalkManifestParser {
    private final static String TAG = "XWalkManifestParser";
    private final static String PERMISSIONS_FIELD_NAME = "permissions";
    private final static String LOCAL_PATH_FIELD_NAME = "local_path";
    private final static String ASSETS_FILE_PATH = "file:///android_asset/";

    private Context mContext;
    private Activity mActivity;
    private XWalkRuntimeViewProvider mXwalkProvider;

    private String mPermissions = null;
    private String mLocalPath = null;

    public XWalkManifestParser(Context context, Activity activity, XWalkRuntimeViewProvider xwalkProvider) {
        mContext = context;
        mActivity = activity;
        mXwalkProvider = xwalkProvider;
    }

    public String getLocalPath() {
        return mLocalPath;
    }

    public String getPermissions() {
        return mPermissions;
    }

    public void parse(String manifestPath) {
        manifestPath = deletePrefix(manifestPath, ASSETS_FILE_PATH);

        String manifestString;
        try {
            manifestString = getAssetsFileContent(mActivity.getAssets(), manifestPath);
        } catch (IOException e) {
            handleException(e);
            Log.e(TAG, "Failed to read manifest.json");
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(manifestString);
            JSONArray permissions =  jsonObject.getJSONArray(PERMISSIONS_FIELD_NAME);
            mPermissions = permissions.toString();
            
            mLocalPath = jsonObject.getString(LOCAL_PATH_FIELD_NAME);
            mLocalPath = deletePrefix(mLocalPath, "./");
            mLocalPath = deletePrefix(mLocalPath, "/");
            mLocalPath = ASSETS_FILE_PATH + mLocalPath;
        } catch (JSONException e) {
            handleException(e);
            Log.e(TAG, "Failed to parse manifest.json");
        }
    }

    private String getAssetsFileContent(AssetManager assetManager, String fileName) throws IOException {
        String result = "";
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(fileName);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            result = new String(buffer);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return result;
    }

    private String deletePrefix(String str, String prefix) {
        if (str != null && prefix != null && str.startsWith(prefix))
            str = str.substring(prefix.length());

        return str;
    }

    private static void handleException(Exception e) {
        Log.e(TAG, "Error in calling methods " + e.toString());
    }
}
