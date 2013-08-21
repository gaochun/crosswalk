/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.xwalk.runtime.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.runtime.extension.CallbackContext;
import org.xwalk.runtime.extension.ExtensionResult;
import org.xwalk.runtime.extension.XWalkExtension;

import android.util.Log;


import java.util.HashMap;

/**
 * This class exposes methods in XWalk that can be called from JavaScript.
 */
public class App extends XWalkExtension {

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback context from which we were invoked.
     * @return                  A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        ExtensionResult.Status status = ExtensionResult.Status.OK;
        String result = "";

        try {
            if (action.equals("show")) {
                // This gets called from JavaScript onXWalkReady to show the webview.
                // I recommend we change the name of the Message as spinner/stop is not
                // indicative of what this actually does (shows the webview).
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        app.postMessage("spinner", "stop");
                    }
                });
            }
            else if (action.equals("loadUrl")) {
                this.loadUrl(args.getString(0), args.optJSONObject(1));
            }
            else if (action.equals("cancelLoadUrl")) {
                //this.cancelLoadUrl();
            }
            else if (action.equals("exitApp")) {
                this.exitApp();
            }
            callbackContext.sendExtensionResult(new ExtensionResult(status, result));
            return true;
        } catch (JSONException e) {
            callbackContext.sendExtensionResult(new ExtensionResult(ExtensionResult.Status.JSON_EXCEPTION));
            return false;
        }
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Load the url into the webview.
     *
     * @param url
     * @param props			Properties that can be passed in to the XWalk activity (i.e. loadingDialog, wait, ...)
     * @throws JSONException
     */
    public void loadUrl(String url, JSONObject props) throws JSONException {
        Log.d("App", "App.loadUrl("+url+","+props+")");
        int wait = 0;

        // If there are properties, then set them on the Activity
        HashMap<String, Object> params = new HashMap<String, Object>();
        if (props != null) {
            JSONArray keys = props.names();
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                if (key.equals("wait")) {
                    wait = props.getInt(key);
                }
                else {
                    Object value = props.get(key);
                    if (value == null) {

                    }
                    else if (value.getClass().equals(String.class)) {
                        params.put(key, (String)value);
                    }
                    else if (value.getClass().equals(Boolean.class)) {
                        params.put(key, (Boolean)value);
                    }
                    else if (value.getClass().equals(Integer.class)) {
                        params.put(key, (Integer)value);
                    }
                }
            }
        }

        // If wait property, then delay loading

        if (wait > 0) {
            try {
                synchronized(this) {
                    this.wait(wait);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.app.loadAppFromUrl(url);
    }

    /**
     * Exit the Android application.
     */
    public void exitApp() {
        this.app.postMessage("exit", null);
    }

}
