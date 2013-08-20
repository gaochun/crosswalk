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
package org.xwalk.runtime.extension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONException;
import org.xmlpull.v1.XmlPullParserException;
import org.xwalk.runtime.XWalkCoreProvider;

import android.app.Activity;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.webkit.WebResourceResponse;

/**
 * XWalkExtensionManager is exposed to JavaScript in the XWalk Runtime.
 *
 * Calling native extension code can be done by calling XWalkExtensionManager.exec(...)
 * from JavaScript.
 */
public class XWalkExtensionManager {
    private static String TAG = "XWalkExtensionManager";

    // List of service entries
    private final HashMap<String, ExtensionEntry> entries = new HashMap<String, ExtensionEntry>();

    private final Activity activity;
    private final XWalkCoreProvider app;

    // Flag to track first time through
    private boolean firstRun;

    // Map URL schemes like foo: to extensions that want to handle those schemes
    // This would allow how all URLs are handled to be offloaded to a extension
    protected HashMap<String, String> urlMap = new HashMap<String, String>();

    /**
     * Constructor.
     *
     * @param app
     * @param ctx
     */
    public XWalkExtensionManager(XWalkCoreProvider app, Activity activity) {
        this.activity = activity;
        this.app = app;
        this.firstRun = true;
    }

    /**
     * Init when loading a new HTML page into webview.
     */
    public void init() {
        Log.d(TAG, "init()");

        // If first time, then load extensions from config.xml file
        if (this.firstRun) {
            this.loadExtensions();
            this.firstRun = false;
        }

        // Stop extensions on current HTML page and discard extension objects
        else {
            this.onPause(false);
            this.onDestroy();
            this.clearExtensionObjects();
        }

        // Start up all extensions that have onload specified
        this.startupExtensions();
    }

    /**
     * Load extensions from res/xml/config.xml
     */
    public void loadExtensions() {
        int id = activity.getResources().getIdentifier("config", "xml", activity.getPackageName());
        if(id == 0)
        {
            id = activity.getResources().getIdentifier("extensions", "xml", activity.getPackageName());
            Log.i(TAG, "Using extensions.xml instead of config.xml.  extensions.xml will eventually be deprecated");
        }
        if (id == 0) {
            this.extensionConfigurationMissing();
            //We have the error, we need to exit without crashing!
            return;
        }
        XmlResourceParser xml = activity.getResources().getXml(id);
        int eventType = -1;
        String service = "", extensionClass = "", paramType = "";
        boolean onload = false;
        boolean insideFeature = false;
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                String strNode = xml.getName();
                //This is for the old scheme
                if (strNode.equals("extension")) {
                    service = xml.getAttributeValue(null, "name");
                    extensionClass = xml.getAttributeValue(null, "value");
                    Log.d(TAG, "<extension> tags are deprecated, please use <features> instead.");
                    onload = "true".equals(xml.getAttributeValue(null, "onload"));
                }
                //What is this?
                else if (strNode.equals("url-filter")) {
                    this.urlMap.put(xml.getAttributeValue(null, "value"), service);
                }
                else if (strNode.equals("feature")) {
                    //Check for supported feature sets  aka. extensions (Accelerometer, Geolocation, etc)
                    //Set the bit for reading params
                    insideFeature = true;
                    service = xml.getAttributeValue(null, "name");
                }
                else if (insideFeature && strNode.equals("param")) {
                    paramType = xml.getAttributeValue(null, "name");
                    if (paramType.equals("service")) // check if it is using the older service param
                        service = xml.getAttributeValue(null, "value");
                    else if (paramType.equals("package") || paramType.equals("android-package"))
                        extensionClass = xml.getAttributeValue(null,"value");
                    else if (paramType.equals("onload"))
                        onload = "true".equals(xml.getAttributeValue(null, "value"));
                }
            }
            else if (eventType == XmlResourceParser.END_TAG)
            {
                String strNode = xml.getName();
                if (strNode.equals("feature") || strNode.equals("extension"))
                {
                    ExtensionEntry entry = new ExtensionEntry(service, extensionClass, onload);
                    this.addService(entry);

                    //Empty the strings to prevent extension loading bugs
                    service = "";
                    extensionClass = "";
                    insideFeature = false;
                }
            }
            try {
                eventType = xml.next();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Delete all extension objects.
     */
    public void clearExtensionObjects() {
        for (ExtensionEntry entry : this.entries.values()) {
            entry.extension = null;
        }
    }

    /**
     * Create extensions objects that have onload set.
     */
    public void startupExtensions() {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.onload) {
                entry.createExtension(this.app, activity);
            }
        }
    }

    /**
     * Receives a request for execution and fulfills it by finding the appropriate
     * Java class and calling it's execute method.
     *
     * XWalkExtensionManager.exec can be used either synchronously or async. In either case, a JSON encoded
     * string is returned that will indicate if any errors have occurred when trying to find
     * or execute the class denoted by the clazz argument.
     *
     * @param service       String containing the service to run
     * @param action        String containing the action that the class is supposed to perform. This is
     *                      passed to the extension execute method and it is up to the extension developer
     *                      how to deal with it.
     * @param callbackId    String containing the id of the callback that is execute in JavaScript if
     *                      this is an async extension call.
     * @param rawArgs       An Array literal string containing any arguments needed in the
     *                      extension execute method.
     * @return Whether the task completed synchronously.
     */
    public boolean exec(String service, String action, String callbackId, String rawArgs) {
        XWalkExtension extension = this.getExtension(service);
        if (extension == null) {
            Log.d(TAG, "exec() call to unknown extension: " + service);
            ExtensionResult cr = new ExtensionResult(ExtensionResult.Status.CLASS_NOT_FOUND_EXCEPTION);
            app.sendExtensionResult(cr, callbackId);
            return true;
        }
        try {
            CallbackContext callbackContext = new CallbackContext(callbackId, app);
            boolean wasValidAction = extension.execute(action, rawArgs, callbackContext);
            if (!wasValidAction) {
                ExtensionResult cr = new ExtensionResult(ExtensionResult.Status.INVALID_ACTION);
                app.sendExtensionResult(cr, callbackId);
                return true;
            }
            return callbackContext.isFinished();
        } catch (JSONException e) {
            ExtensionResult cr = new ExtensionResult(ExtensionResult.Status.JSON_EXCEPTION);
            app.sendExtensionResult(cr, callbackId);
            return true;
        }
    }

    @Deprecated
    public boolean exec(String service, String action, String callbackId, String jsonArgs, boolean async) {
        return exec(service, action, callbackId, jsonArgs);
    }

    /**
     * Get the extension object that implements the service.
     * If the extension object does not already exist, then create it.
     * If the service doesn't exist, then return null.
     *
     * @param service       The name of the service.
     * @return              XWalkExtension or null
     */
    public XWalkExtension getExtension(String service) {
        ExtensionEntry entry = this.entries.get(service);
        if (entry == null) {
            return null;
        }
        XWalkExtension extension = entry.extension;
        if (extension == null) {
            extension = entry.createExtension(this.app, activity);
        }
        return extension;
    }

    /**
     * Add a extension class that implements a service to the service entry table.
     * This does not create the extension object instance.
     *
     * @param service           The service name
     * @param className         The extension class name
     */
    public void addService(String service, String className) {
        ExtensionEntry entry = new ExtensionEntry(service, className, false);
        this.addService(entry);
    }

    /**
     * Add a extension class that implements a service to the service entry table.
     * This does not create the extension object instance.
     *
     * @param entry             The extension entry
     */
    public void addService(ExtensionEntry entry) {
        this.entries.put(entry.service, entry);
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.extension != null) {
                entry.extension.onPause(multitasking);
            }
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking      Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.extension != null) {
                entry.extension.onResume(multitasking);
            }
        }
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    public void onDestroy() {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.extension != null) {
                entry.extension.onDestroy();
            }
        }
    }

    /**
     * Send a message to all extensions.
     *
     * @param id                The message id
     * @param data              The message data
     * @return
     */
    public Object postMessage(String id, Object data) {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.extension != null) {
                Object obj = entry.extension.onMessage(id, data);
                if (obj != null) {
                    return obj;
                }
            }
        }
        return null;
    }

    /**
     * Called when the activity receives a new intent.
     */
    public void onNewIntent(Intent intent) {
        for (ExtensionEntry entry : this.entries.values()) {
            if (entry.extension != null) {
                entry.extension.onNewIntent(intent);
            }
        }
    }

    /**
     * Called when the URL of the webview changes.
     *
     * @param url               The URL that is being changed to.
     * @return                  Return false to allow the URL to load, return true to prevent the URL from loading.
     */
    public boolean onOverrideUrlLoading(String url) {
        Iterator<Entry<String, String>> it = this.urlMap.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, String> pairs = it.next();
            if (url.startsWith(pairs.getKey())) {
                return this.getExtension(pairs.getValue()).onOverrideUrlLoading(url);
            }
        }
        return false;
    }

    /**
     * Called when the WebView is loading any resource, top-level or not.
     *
     * Uses the same url-filter tag as onOverrideUrlLoading.
     *
     * @param url               The URL of the resource to be loaded.
     * @return                  Return a WebResourceResponse with the resource, or null if the WebView should handle it.
     */
    public WebResourceResponse shouldInterceptRequest(String url) {
        Iterator<Entry<String, String>> it = this.urlMap.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, String> pairs = it.next();
            if (url.startsWith(pairs.getKey())) {
                return this.getExtension(pairs.getValue()).shouldInterceptRequest(url);
            }
        }
        return null;
    }

    /**
     * Called when the app navigates or refreshes.
     */
    public void onReset() {
        Iterator<ExtensionEntry> it = this.entries.values().iterator();
        while (it.hasNext()) {
        	XWalkExtension extension = it.next().extension;
            if (extension != null) {
                extension.onReset();
            }
        }
    }


    private void extensionConfigurationMissing() {
        Log.e(TAG, "=====================================================================================");
        Log.e(TAG, "ERROR: config.xml is missing.  Add res/xml/extensions.xml to your project.");
        Log.e(TAG, "=====================================================================================");
    }
}
