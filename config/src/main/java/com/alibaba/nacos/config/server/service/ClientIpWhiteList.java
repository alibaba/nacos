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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.AclInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * Client ip whitelist.
 *
 * @author Nacos
 */
@Service
public class ClientIpWhiteList {

    public static final String CLIENT_IP_WHITELIST_METADATA = "com.alibaba.nacos.metadata.clientIpWhitelist";

    private static final AtomicReference<List<String>> CLIENT_IP_WHITELIST = new AtomicReference<List<String>>(
            new ArrayList<String>());

    private static Boolean isOpen = false;
    
    /**
     * Judge whether specified client ip includes in the whitelist.
     *
     * @param clientIp clientIp string value.
     * @return Judge result.
     */
    public static boolean isLegalClient(String clientIp) {
        if (StringUtils.isBlank(clientIp)) {
            throw new IllegalArgumentException("clientIp is empty");
        }
        clientIp = clientIp.trim();
        
        if (CLIENT_IP_WHITELIST.get().contains(clientIp)) {
            return true;
        }
        return false;
    }
    
    /**
     * Whether start client ip whitelist.
     *
     * @return true: enable ; false disable
     */
    public static boolean isEnableWhitelist() {
        return isOpen;
    }
    
    /**
     * Load white lists based content parameter value.
     *
     * @param content content string value.
     */
    public static void load(String content) {
        if (StringUtils.isBlank(content)) {
            DEFAULT_LOG.warn("clientIpWhiteList is blank.close whitelist.");
            isOpen = false;
            CLIENT_IP_WHITELIST.get().clear();
            return;
        }
        DEFAULT_LOG.warn("[clientIpWhiteList] {}", content);
        try {
            AclInfo acl = JacksonUtils.toObj(content, AclInfo.class);
            isOpen = acl.getIsOpen();
            CLIENT_IP_WHITELIST.set(acl.getIps());
        } catch (Exception ioe) {
            DEFAULT_LOG.error("failed to load clientIpWhiteList, " + ioe.toString(), ioe);
        }
    }
}
