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

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xwalk.runtime.XWalkCoreProvider;

public class CallbackContext {
    private static final String LOG_TAG = "CallbackContext";

    private String callbackId;
    private XWalkCoreProvider app;
    private boolean finished;
    private int changingThreads;

    public CallbackContext(String callbackId, XWalkCoreProvider app) {
        this.callbackId = callbackId;
        this.app = app;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    public boolean isChangingThreads() {
        return changingThreads > 0;
    }
    
    public String getCallbackId() {
        return callbackId;
    }

    public void sendExtensionResult(ExtensionResult extensionResult) {
        synchronized (this) {
            if (finished) {
                Log.w(LOG_TAG, "Attempted to send a second callback for ID: " + callbackId + "\nResult was: " + extensionResult.getMessage());
                return;
            } else {
                finished = !extensionResult.getKeepCallback();
            }
        }
        app.sendExtensionResult(extensionResult, callbackId);
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success(JSONObject message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK, message));
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success(String message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK, message));
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success(JSONArray message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK, message));
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success(byte[] message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK, message));
    }
    
    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success(int message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK, message));
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message           The message to add to the success result.
     */
    public void success() {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.OK));
    }

    /**
     * Helper for error callbacks that just returns the Status.ERROR by default
     *
     * @param message           The message to add to the error result.
     */
    public void error(JSONObject message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.ERROR, message));
    }

    /**
     * Helper for error callbacks that just returns the Status.ERROR by default
     *
     * @param message           The message to add to the error result.
     * @param callbackId        The callback id used when calling back into JavaScript.
     */
    public void error(String message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.ERROR, message));
    }

    /**
     * Helper for error callbacks that just returns the Status.ERROR by default
     *
     * @param message           The message to add to the error result.
     * @param callbackId        The callback id used when calling back into JavaScript.
     */
    public void error(int message) {
        sendExtensionResult(new ExtensionResult(ExtensionResult.Status.ERROR, message));
    }
}
