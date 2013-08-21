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

import java.util.LinkedList;

import org.xwalk.runtime.XWalkCoreProvider;

import android.app.Activity;
import android.util.Log;

/**
 * Holds the list of messages to be sent to the Runtime.
 */
public class NativeToJsMessageQueue {
    private static final String LOG_TAG = "JsMessageQueue";

    // This must match the default value in incubator-xwalk-js/lib/android/exec.js
    private static final int DEFAULT_BRIDGE_MODE = 2;
    
    // Set this to true to force extension results to be encoding as
    // JS instead of the custom format (useful for benchmarking).
    private static final boolean FORCE_ENCODE_USING_EVAL = false;

    // Disable URL-based exec() bridge by default since it's a bit of a
    // security concern.
    static final boolean ENABLE_LOCATION_CHANGE_EXEC_MODE = false;
        
    // Disable sending back native->JS messages during an exec() when the active
    // exec() is asynchronous. Set this to true when running bridge benchmarks.
    static final boolean DISABLE_EXEC_CHAINING = false;
    
    // Arbitrarily chosen upper limit for how much data to send to JS in one shot.
    // This currently only chops up on message boundaries. It may be useful
    // to allow it to break up messages.
    private static int MAX_PAYLOAD_SIZE = 50 * 1024 * 10240;
    
    /**
     * The index into registeredListeners to treat as active. 
     */
    private int activeListenerIndex;
    
    /**
     * When true, the active listener is not fired upon enqueue. When set to false,
     * the active listener will be fired if the queue is non-empty. 
     */
    private boolean paused;
    
    /**
     * The list of JavaScript statements to be sent to JavaScript.
     */
    private final LinkedList<JsMessage> queue = new LinkedList<JsMessage>();

    /**
     * The array of listeners that can be used to send messages to JS.
     */
    private final BridgeMode[] registeredListeners;    
    
    private final Activity activity;
    private final XWalkCoreProvider app;

    public NativeToJsMessageQueue(XWalkCoreProvider app, Activity activity) {
        this.activity = activity;
        this.app = app;
        registeredListeners = new BridgeMode[4];
        registeredListeners[0] = null;  // Polling. Requires no logic.
        registeredListeners[1] = new LoadUrlBridgeMode();
        registeredListeners[2] = new OnlineEventsBridgeMode();
        registeredListeners[3] = new PrivateApiBridgeMode();
        reset();
    }
    
    /**
     * Changes the bridge mode.
     */
    public void setBridgeMode(int value) {
        if (value < 0 || value >= registeredListeners.length) {
            Log.d(LOG_TAG, "Invalid NativeToJsBridgeMode: " + value);
        } else {
            if (value != activeListenerIndex) {
                Log.d(LOG_TAG, "Set native->JS mode to " + value);
                synchronized (this) {
                    activeListenerIndex = value;
                    BridgeMode activeListener = registeredListeners[value];
                    if (!paused && !queue.isEmpty() && activeListener != null) {
                        activeListener.onNativeToJsMessageAvailable();
                    }
                }
            }
        }
    }
    
    /**
     * Clears all messages and resets to the default bridge mode.
     */
    public void reset() {
        synchronized (this) {
            queue.clear();
            setBridgeMode(DEFAULT_BRIDGE_MODE);
        }
    }

    private int calculatePackedMessageLength(JsMessage message) {
        int messageLen = message.calculateEncodedLength();
        String messageLenStr = String.valueOf(messageLen);
        return messageLenStr.length() + messageLen + 1;        
    }
    
    private void packMessage(JsMessage message, StringBuilder sb) {
        int len = message.calculateEncodedLength();
        sb.append(len)
          .append(' ');
        message.encodeAsMessage(sb);
    }
    
    /**
     * Combines and returns queued messages combined into a single string.
     * Combines as many messages as possible, while staying under MAX_PAYLOAD_SIZE.
     * Returns null if the queue is empty.
     */
    public String popAndEncode() {
        synchronized (this) {
            if (queue.isEmpty()) {
                return null;
            }
            int totalPayloadLen = 0;
            int numMessagesToSend = 0;
            for (JsMessage message : queue) {
                int messageSize = calculatePackedMessageLength(message);
                if (numMessagesToSend > 0 && totalPayloadLen + messageSize > MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                    break;
                }
                totalPayloadLen += messageSize;
                numMessagesToSend += 1;
            }

            StringBuilder sb = new StringBuilder(totalPayloadLen);
            for (int i = 0; i < numMessagesToSend; ++i) {
                JsMessage message = queue.removeFirst();
                packMessage(message, sb);
            }
            
            if (!queue.isEmpty()) {
                // Attach a char to indicate that there are more messages pending.
                sb.append('*');
            }
            String ret = sb.toString();
            return ret;
        }
    }
    
    /**
     * Same as popAndEncode(), except encodes in a form that can be executed as JS.
     */
    private String popAndEncodeAsJs() {
        synchronized (this) {
            int length = queue.size();
            if (length == 0) {
                return null;
            }
            int totalPayloadLen = 0;
            int numMessagesToSend = 0;
            for (JsMessage message : queue) {
                int messageSize = message.calculateEncodedLength() + 50; // overestimate.
                if (numMessagesToSend > 0 && totalPayloadLen + messageSize > MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                    break;
                }
                totalPayloadLen += messageSize;
                numMessagesToSend += 1;
            }
            boolean willSendAllMessages = numMessagesToSend == queue.size();
            StringBuilder sb = new StringBuilder(totalPayloadLen + (willSendAllMessages ? 0 : 100));
            // Wrap each statement in a try/finally so that if one throws it does 
            // not affect the next.
            for (int i = 0; i < numMessagesToSend; ++i) {
                JsMessage message = queue.removeFirst();
                if (willSendAllMessages && (i + 1 == numMessagesToSend)) {
                    message.encodeAsJsMessage(sb);
                } else {
                    sb.append("try{");
                    message.encodeAsJsMessage(sb);
                    sb.append("}finally{");
                }
            }
            if (!willSendAllMessages) {
                sb.append("window.setTimeout(function(){xwalk.require('xwalk/plugin/android/polling').pollOnce();},0);");
            }
            for (int i = willSendAllMessages ? 1 : 0; i < numMessagesToSend; ++i) {
                sb.append('}');
            }
            String ret = sb.toString();
            return ret;
        }
    }   

    /**
     * Add a JavaScript statement to the list.
     */
    public void addJavaScript(String statement) {
        enqueueMessage(new JsMessage(statement));
    }

    /**
     * Add a JavaScript statement to the list.
     */
    public void addExtensionResult(ExtensionResult result, String callbackId) {
        if (callbackId == null) {
            Log.e(LOG_TAG, "Got extension result with no callbackId", new Throwable());
            return;
        }
        // Don't send anything if there is no result and there is no need to
        // clear the callbacks.
        boolean noResult = result.getStatus() == ExtensionResult.Status.NO_RESULT.ordinal();
        boolean keepCallback = result.getKeepCallback();
        if (noResult && keepCallback) {
            return;
        }
        JsMessage message = new JsMessage(result, callbackId);
        if (FORCE_ENCODE_USING_EVAL) {
            StringBuilder sb = new StringBuilder(message.calculateEncodedLength() + 50);
            message.encodeAsJsMessage(sb);
            message = new JsMessage(sb.toString());
        }

        enqueueMessage(message);
    }
    
    private void enqueueMessage(JsMessage message) {
        synchronized (this) {
            queue.add(message);
            if (!paused && registeredListeners[activeListenerIndex] != null) {
                registeredListeners[activeListenerIndex].onNativeToJsMessageAvailable();
            }
        }        
    }
    
    public void setPaused(boolean value) {
        if (paused && value) {
            // This should never happen. If a use-case for it comes up, we should
            // change pause to be a counter.
            Log.e(LOG_TAG, "nested call to setPaused detected.", new Throwable());
        }
        paused = value;
        if (!value) {
            synchronized (this) {
                if (!queue.isEmpty() && registeredListeners[activeListenerIndex] != null) {
                    registeredListeners[activeListenerIndex].onNativeToJsMessageAvailable();
                }
            }   
        }
    }
    
    public boolean getPaused() {
        return paused;
    }

    private interface BridgeMode {
        void onNativeToJsMessageAvailable();
    }
    
    /** Uses RuntimeView.loadAppFromUrl("javascript:") to execute messages. */
    private class LoadUrlBridgeMode implements BridgeMode {
        final Runnable runnable = new Runnable() {
            public void run() {
                String js = popAndEncodeAsJs();
                if (js != null) {
                    app.loadAppFromUrl("javascript:" + js);
                }
            }
        };
        
        public void onNativeToJsMessageAvailable() {
            activity.runOnUiThread(runnable);
        }
    }

    /** Uses online/offline events to tell the JS when to poll for messages. */
    private class OnlineEventsBridgeMode implements BridgeMode {
        boolean online = true;
        final Runnable runnable = new Runnable() {
            public void run() {
                if (!queue.isEmpty()) {
                    online = !online;
                    app.setNetworkAvailable(online);
                }
            }                
        };
        OnlineEventsBridgeMode() {
            app.setNetworkAvailable(true);
        }
        public void onNativeToJsMessageAvailable() {
            activity.runOnUiThread(runnable);
        }
    }
    
    /**
     * Evaluate JS directly with Runtime.
     */
    private class PrivateApiBridgeMode implements BridgeMode {
        public void onNativeToJsMessageAvailable() {
            String js = popAndEncodeAsJs();
            app.evaluateJavascript(js, null);
        }
    }    
    private static class JsMessage {
        final String jsPayloadOrCallbackId;
        final ExtensionResult extensionResult;
        JsMessage(String js) {
            if (js == null) {
                throw new NullPointerException();
            }
            jsPayloadOrCallbackId = js;
            extensionResult = null;
        }
        JsMessage(ExtensionResult extensionResult, String callbackId) {
            if (callbackId == null || extensionResult == null) {
                throw new NullPointerException();
            }
            jsPayloadOrCallbackId = callbackId;
            this.extensionResult = extensionResult;
        }
        
        int calculateEncodedLength() {
            if (extensionResult == null) {
                return jsPayloadOrCallbackId.length() + 1;
            }
            int statusLen = String.valueOf(extensionResult.getStatus()).length();
            int ret = 2 + statusLen + 1 + jsPayloadOrCallbackId.length() + 1;
            switch (extensionResult.getMessageType()) {
                case ExtensionResult.MESSAGE_TYPE_BOOLEAN: // f or t
                case ExtensionResult.MESSAGE_TYPE_NULL: // N
                    ret += 1;
                    break;
                case ExtensionResult.MESSAGE_TYPE_NUMBER: // n
                    ret += 1 + extensionResult.getMessage().length();
                    break;
                case ExtensionResult.MESSAGE_TYPE_STRING: // s
                    ret += 1 + extensionResult.getStrMessage().length();
                    break;
                case ExtensionResult.MESSAGE_TYPE_BINARYSTRING:
                    ret += 1 + extensionResult.getMessage().length();
                    break;
                case ExtensionResult.MESSAGE_TYPE_ARRAYBUFFER:
                    ret += 1 + extensionResult.getMessage().length();
                    break;
                case ExtensionResult.MESSAGE_TYPE_JSON:
                default:
                    ret += extensionResult.getMessage().length();
            }
            return ret;
        }
        
        void encodeAsMessage(StringBuilder sb) {
            if (extensionResult == null) {
                sb.append('J')
                  .append(jsPayloadOrCallbackId);
                return;
            }
            int status = extensionResult.getStatus();
            boolean noResult = status == ExtensionResult.Status.NO_RESULT.ordinal();
            boolean resultOk = status == ExtensionResult.Status.OK.ordinal();
            boolean keepCallback = extensionResult.getKeepCallback();

            sb.append((noResult || resultOk) ? 'S' : 'F')
              .append(keepCallback ? '1' : '0')
              .append(status)
              .append(' ')
              .append(jsPayloadOrCallbackId)
              .append(' ');
            switch (extensionResult.getMessageType()) {
                case ExtensionResult.MESSAGE_TYPE_BOOLEAN:
                    sb.append(extensionResult.getMessage().charAt(0)); // t or f.
                    break;
                case ExtensionResult.MESSAGE_TYPE_NULL: // N
                    sb.append('N');
                    break;
                case ExtensionResult.MESSAGE_TYPE_NUMBER: // n
                    sb.append('n')
                      .append(extensionResult.getMessage());
                    break;
                case ExtensionResult.MESSAGE_TYPE_STRING: // s
                    sb.append('s');
                    sb.append(extensionResult.getStrMessage());
                    break;
                case ExtensionResult.MESSAGE_TYPE_BINARYSTRING: // S
                    sb.append('S');
                    sb.append(extensionResult.getMessage());
                    break;                    
                case ExtensionResult.MESSAGE_TYPE_ARRAYBUFFER: // A
                    sb.append('A');
                    sb.append(extensionResult.getMessage());
                    break;
                case ExtensionResult.MESSAGE_TYPE_JSON:
                default:
                    sb.append(extensionResult.getMessage()); // [ or {
            }
        }
        
        void encodeAsJsMessage(StringBuilder sb) {
            if (extensionResult == null) {
                sb.append(jsPayloadOrCallbackId);
            } else {
                int status = extensionResult.getStatus();
                boolean success = (status == ExtensionResult.Status.OK.ordinal()) || (status == ExtensionResult.Status.NO_RESULT.ordinal());
                sb.append("xwalk.callbackFromNative('")
                  .append(jsPayloadOrCallbackId)
                  .append("',")
                  .append(success)
                  .append(",")
                  .append(status)
                  .append(",[")
                  .append(extensionResult.getMessage())
                  .append("],")
                  .append(extensionResult.getKeepCallback())
                  .append(");");
            }
        }
    }
}
