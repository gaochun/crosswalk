// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.core.xwview.test;

import android.graphics.Bitmap;
import android.test.suitebuilder.annotation.SmallTest;
import org.chromium.base.test.util.Feature;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Pair;

import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;
import org.chromium.net.test.util.TestWebServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xwalk.core.XWalkCookieManager;
import org.xwalk.core.XWalkClient;
import org.xwalk.core.XWalkContent;
import org.xwalk.core.XWalkView;

/**
 * Tests for the CookieManager.
 */
public class CookieManagerTest extends XWalkViewTestBase {

    private XWalkCookieManager mCookieManager = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCookieManager = new XWalkCookieManager();

        class TestXWalkClient extends XWalkClient {
            @Override
            public void onPageStarted(XWalkView view, String url, Bitmap favicon) {
                mTestContentsClient.onPageStarted(url);
            }

            @Override
            public void onPageFinished(XWalkView view, String url) {
                mTestContentsClient.didFinishLoad(url);
            }
        }
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getXWalkView().setXWalkClient(new TestXWalkClient());
            }
        });
    }

    @SmallTest
    @Feature({"AcceptFileSchemeCookies"})
    public void testAllowFileSchemeCookies() throws Throwable {
        assertFalse(mCookieManager.allowFileSchemeCookies());
        mCookieManager.setAcceptFileSchemeCookies(true);
        assertTrue(mCookieManager.allowFileSchemeCookies());
        mCookieManager.setAcceptFileSchemeCookies(false);
        assertFalse(mCookieManager.allowFileSchemeCookies());
    }

    @MediumTest
    @Feature({"AcceptCookie"})
    public void testAcceptCookie() throws Throwable {
        TestWebServer webServer = null;
        try {
            webServer = new TestWebServer(false);
            String path = "/cookie_test.html";
            String responseStr =
                    "<html><head><title>TEST!</title></head><body>HELLO!</body></html>";

            mCookieManager.setAcceptCookie(false);
            mCookieManager.removeAllCookie();
            assertFalse(mCookieManager.acceptCookie());
            assertFalse(mCookieManager.hasCookies());

            List<Pair<String, String>> responseHeaders = new ArrayList<Pair<String, String>>();
            responseHeaders.add(
                    Pair.create("Set-Cookie", "header-test1=header-value1; path=" + path));
            String url = webServer.setResponse(path, responseStr, responseHeaders);
            loadUrlSync(url);
            assertNull(mCookieManager.getCookie(url));

            mCookieManager.setAcceptCookie(true);
            assertTrue(mCookieManager.acceptCookie());

            url = webServer.setResponse(path, responseStr, null);
            loadUrlSync(url);
            setCookie("test2", "value2");
            waitForCookie(url);
            String cookie = mCookieManager.getCookie(url);
            assertNotNull(cookie);
            validateCookies(cookie, "test2");

            responseHeaders = new ArrayList<Pair<String, String>>();
            responseHeaders.add(
                    Pair.create("Set-Cookie", "header-test2=header-value2 path=" + path));
            url = webServer.setResponse(path, responseStr, responseHeaders);
            loadUrlSync(url);
            waitForCookie(url);
            cookie = mCookieManager.getCookie(url);
            assertNotNull(cookie);
            validateCookies(cookie, "test2", "header-test2");

            // clean up all cookies
            mCookieManager.removeAllCookie();
        } finally {
            if (webServer != null) webServer.shutdown();
        }
    }

    private void setCookie(final String name, final String value) throws Exception {
        String jsCommand = "javascript:void((function(){" +
                "var expirationDate = new Date();" +
                "expirationDate.setDate(expirationDate.getDate() + 5);" +
                "document.cookie='" + name + "=" + value +
                        "; expires=' + expirationDate.toUTCString();" +
                "})())";
        loadUrlSync(jsCommand);
    }

    private void waitForCookie(final String url) throws InterruptedException {
        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return mCookieManager.getCookie(url) != null;
            }
        }, 6000, 50));
    }

    private void validateCookies(String responseCookie, String... expectedCookieNames) {
        String[] cookies = responseCookie.split(";");
        Set<String> foundCookieNames = new HashSet<String>();
        for (String cookie : cookies) {
            foundCookieNames.add(cookie.substring(0, cookie.indexOf("=")).trim());
        }
        MoreAsserts.assertEquals(
                foundCookieNames, new HashSet<String>(Arrays.asList(expectedCookieNames)));
    }

    @MediumTest
    @Feature({"RemoveAllCookies"})
    public void testRemoveAllCookie() throws InterruptedException {
        // enable cookie
        mCookieManager.setAcceptCookie(true);
        assertTrue(mCookieManager.acceptCookie());

        // first there should be no cookie stored
        mCookieManager.removeAllCookie();
        mCookieManager.flushCookieStore();
        assertFalse(mCookieManager.hasCookies());

        String url = "http://www.example.com";
        String cookie = "name=test";
        mCookieManager.setCookie(url, cookie);
        assertEquals(cookie, mCookieManager.getCookie(url));

        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return mCookieManager.hasCookies();
            }
        }));

        // clean up all cookies
        mCookieManager.removeAllCookie();
        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return !mCookieManager.hasCookies();
            }
        }));
    }

    @MediumTest
    @Feature({"CookieExpiration"})
    @SuppressWarnings("deprecation")
    public void testCookieExpiration() throws InterruptedException {
        // enable cookie
        mCookieManager.setAcceptCookie(true);
        assertTrue(mCookieManager.acceptCookie());
        mCookieManager.removeAllCookie();
        assertFalse(mCookieManager.hasCookies());

        final String url = "http://www.example.com";
        final String cookie1 = "cookie1=peter";
        final String cookie2 = "cookie2=sue";
        final String cookie3 = "cookie3=marc";

        mCookieManager.setCookie(url, cookie1); // session cookie

        Date date = new Date();
        date.setTime(date.getTime() + 1000 * 600);
        String value2 = cookie2 + "; expires=" + date.toGMTString();
        mCookieManager.setCookie(url, value2); // expires in 10min

        long expiration = 3000;
        date = new Date();
        date.setTime(date.getTime() + expiration);
        String value3 = cookie3 + "; expires=" + date.toGMTString();
        mCookieManager.setCookie(url, value3); // expires in 3s

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(cookie1));
        assertTrue(allCookies.contains(cookie2));
        assertTrue(allCookies.contains(cookie3));

        mCookieManager.removeSessionCookie();
        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                String c = mCookieManager.getCookie(url);
                return !c.contains(cookie1) && c.contains(cookie2) && c.contains(cookie3);
            }
        }));

        Thread.sleep(expiration + 1000); // wait for cookie to expire
        mCookieManager.removeExpiredCookie();
        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                String c = mCookieManager.getCookie(url);
                return !c.contains(cookie1) && c.contains(cookie2) && !c.contains(cookie3);
            }
        }));

        mCookieManager.removeAllCookie();
        assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return mCookieManager.getCookie(url) == null;
            }
        }));
    }
}
