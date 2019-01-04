/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.model.ACLInfo;
import com.alibaba.nacos.config.server.utils.JSONUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * Client ip whitelist
 *
 * @author Nacos
 */
@Service
public class ClientIpWhiteList {

    /**
     * 判断指定的ip在白名单中
     */
    static public boolean isLegalClient(String clientIp) {
        if (StringUtils.isBlank(clientIp)) {
            throw new IllegalArgumentException();
        }
        clientIp = clientIp.trim();
        if (CLIENT_IP_WHITELIST.get().contains(clientIp)) {
            return true;
        }
        return false;
    }

    /**
     * whether start client ip whitelist
     *
     * @return true: enable ; false disable
     */
    static public boolean isEnableWhitelist() {
        return isOpen;
    }

    /**
     * 传入内容，重新加载客户端ip白名单
     */
    static public void load(String content) {
        if (StringUtils.isBlank(content)) {
            defaultLog.warn("clientIpWhiteList is blank.close whitelist.");
            isOpen = false;
            CLIENT_IP_WHITELIST.get().clear();
            return;
        }
        defaultLog.warn("[clientIpWhiteList] {}", content);
        try {
            ACLInfo acl = (ACLInfo)JSONUtils.deserializeObject(content, ACLInfo.class);
            isOpen = acl.getIsOpen();
            CLIENT_IP_WHITELIST.set(acl.getIps());
        } catch (Exception ioe) {
            defaultLog.error(
                "failed to load clientIpWhiteList, " + ioe.toString(), ioe);
        }
    }

    // =======================

    static public final String CLIENT_IP_WHITELIST_METADATA = "com.alibaba.nacos.metadata.clientIpWhitelist";

    static final AtomicReference<List<String>> CLIENT_IP_WHITELIST = new AtomicReference<List<String>>(
        new ArrayList<String>());
    static Boolean isOpen = false;
}
