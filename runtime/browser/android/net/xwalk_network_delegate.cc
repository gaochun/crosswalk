// Copyright (c) 2013 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "xwalk/runtime/browser/android/net/xwalk_network_delegate.h"

#include "base/android/build_info.h"
#include "net/base/net_errors.h"
#include "net/base/completion_callback.h"
#include "net/url_request/url_request.h"
#include "xwalk/runtime/browser/android/xwalk_cookie_access_policy.h"

namespace xwalk {

XWalkNetworkDelegate::XWalkNetworkDelegate() {
}

XWalkNetworkDelegate::~XWalkNetworkDelegate() {
}

int XWalkNetworkDelegate::OnBeforeURLRequest(
    net::URLRequest* request,
    const net::CompletionCallback& callback,
    GURL* new_url) {
  return net::OK;
}

int XWalkNetworkDelegate::OnBeforeSendHeaders(
    net::URLRequest* request,
    const net::CompletionCallback& callback,
    net::HttpRequestHeaders* headers) {

  DCHECK(headers);
  headers->SetHeaderIfMissing(
      "X-Requested-With",
      base::android::BuildInfo::GetInstance()->package_name());
  return net::OK;
}

void XWalkNetworkDelegate::OnSendHeaders(
    net::URLRequest* request,
    const net::HttpRequestHeaders& headers) {
}

int XWalkNetworkDelegate::OnHeadersReceived(
    net::URLRequest* request,
    const net::CompletionCallback& callback,
    const net::HttpResponseHeaders* original_response_headers,
    scoped_refptr<net::HttpResponseHeaders>* override_response_headers) {
  return net::OK;
}

void XWalkNetworkDelegate::OnBeforeRedirect(net::URLRequest* request,
                                            const GURL& new_location) {
}

void XWalkNetworkDelegate::OnResponseStarted(net::URLRequest* request) {
}

void XWalkNetworkDelegate::OnRawBytesRead(const net::URLRequest& request,
                                          int bytes_read) {
}

void XWalkNetworkDelegate::OnCompleted(
    net::URLRequest* request, bool started) {
}

void XWalkNetworkDelegate::OnURLRequestDestroyed(net::URLRequest* request) {
}

void XWalkNetworkDelegate::OnPACScriptError(int line_number,
                                            const string16& error) {
}

net::NetworkDelegate::AuthRequiredResponse
XWalkNetworkDelegate::OnAuthRequired(
    net::URLRequest* request,
    const net::AuthChallengeInfo& auth_info,
    const AuthCallback& callback,
    net::AuthCredentials* credentials) {
  return AUTH_REQUIRED_RESPONSE_NO_ACTION;
}

bool XWalkNetworkDelegate::OnCanGetCookies(
    const net::URLRequest& request,
    const net::CookieList& cookie_list) {
  return XWalkCookieAccessPolicy::GetInstance()->OnCanGetCookies(
    request, cookie_list);
}

bool XWalkNetworkDelegate::OnCanSetCookie(const net::URLRequest& request,
                                          const std::string& cookie_line,
                                          net::CookieOptions* options) {
  return XWalkCookieAccessPolicy::GetInstance()->OnCanSetCookie(request,
                                                                cookie_line,
                                                                options);
}

bool XWalkNetworkDelegate::OnCanAccessFile(const net::URLRequest& request,
                                           const base::FilePath& path) const {
  return true;
}

bool XWalkNetworkDelegate::OnCanThrottleRequest(
    const net::URLRequest& request) const {
  return false;
}

int XWalkNetworkDelegate::OnBeforeSocketStreamConnect(
    net::SocketStream* stream,
    const net::CompletionCallback& callback) {
  return net::OK;
}

void XWalkNetworkDelegate::OnRequestWaitStateChange(
    const net::URLRequest& request,
    RequestWaitState state) {
}

}  // namespace xwalk
