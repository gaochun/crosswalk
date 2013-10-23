// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "xwalk/runtime/renderer/android/xwalk_render_process_observer.h"

#include "base/json/json_reader.h"
#include "base/values.h"
#include "content/public/common/url_constants.h"
#include "extensions/common/url_pattern.h"
#include "ipc/ipc_message_macros.h"
#include "third_party/WebKit/public/web/WebCache.h"
#include "third_party/WebKit/public/web/WebNetworkStateNotifier.h"
#include "third_party/WebKit/public/web/WebSecurityPolicy.h"
#include "xwalk/runtime/common/android/xwalk_render_view_messages.h"

namespace xwalk {

XWalkRenderProcessObserver::XWalkRenderProcessObserver()
  : webkit_initialized_(false) {
}

XWalkRenderProcessObserver::~XWalkRenderProcessObserver() {
}

bool XWalkRenderProcessObserver::OnControlMessageReceived(
    const IPC::Message& message) {
  bool handled = true;
  IPC_BEGIN_MESSAGE_MAP(XWalkRenderProcessObserver, message)
    IPC_MESSAGE_HANDLER(XWalkViewMsg_SetJsOnlineProperty, OnSetJsOnlineProperty)
    IPC_MESSAGE_HANDLER(XWalkViewMsg_SetPermissions, OnSetPermissions)
    IPC_MESSAGE_UNHANDLED(handled = false)
  IPC_END_MESSAGE_MAP()
  return handled;
}

void XWalkRenderProcessObserver::WebKitInitialized() {
  webkit_initialized_ = true;
}

void XWalkRenderProcessObserver::OnSetJsOnlineProperty(bool network_up) {
  if (webkit_initialized_)
    WebKit::WebNetworkStateNotifier::setOnLine(network_up);
}

void XWalkRenderProcessObserver::OnSetPermissions(std::string base_url,
                                                  std::string permissions) {
  if (base_url.empty() || permissions.empty())
    return;

  base::Value* permissions_value = base::JSONReader::Read(permissions);
  if (!permissions_value)
    return;

  base::ListValue* permission_list = NULL;
  if (!permissions_value->GetAsList(&permission_list))
    return;

  const char* schemes[] = {
    content::kHttpScheme,
    content::kHttpsScheme,
    chrome::kFileScheme,
    chrome::kChromeUIScheme,
  };
  size_t size = permission_list->GetSize();
  for (size_t i = 0; i < size; i ++) {
    std::string permission;
    if (!permission_list->GetString(i, &permission))
      continue;

    URLPattern allowedUrl(URLPattern::SCHEME_ALL);
    if (allowedUrl.Parse(permission) != URLPattern::PARSE_SUCCESS)
      continue;

    for (size_t j = 0; j < arraysize(schemes); ++j) {
      if (allowedUrl.MatchesScheme(schemes[j])) {
        WebKit::WebSecurityPolicy::addOriginAccessWhitelistEntry(
              WebKit::WebURL(GURL(base_url)),
              WebKit::WebString::fromUTF8(schemes[j]),
              WebKit::WebString::fromUTF8(allowedUrl.host()),
              allowedUrl.match_subdomains());
      }
    }
  }
}

}  // namespace xwalk
