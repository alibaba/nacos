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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.watch.ConfigWatchCenter;
import com.alibaba.nacos.config.server.service.watch.client.LongPollWatchClient;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LongPollingService.
 *
 * @author Nacos
 */
@Service
public class LongPollingService {
    
    /**
     * Add LongPollingClient.
     *
     * @param req          HttpServletRequest.
     * @param rsp          HttpServletResponse.
     * @param clientMd5Map clientMd5Map.
     */
    public void addLongPollingClient(HttpServletRequest req, HttpServletResponse rsp,
            Map<String, String> clientMd5Map) {
        String appName = req.getHeader(RequestUtil.CLIENT_APPNAME_HEADER);
        String tag = req.getHeader("Vipserver-Tag");
        
        // old: D w G w MD5 l
        // new: D w G w MD5 w T l
        // just support new: D w G w MD5 w T l
        final Set<String> namespaces = new HashSet<>();
        clientMd5Map.forEach((key, md5Sign) -> {
            final String[] configMetadata = MD5Util.splitConfigKey(key);
            if (configMetadata.length == 3) {
                namespaces.add(ParamUtils.processNamespace(configMetadata[3]));
            } else {
                namespaces.add(ParamUtils.processNamespace(Constants.DEFAULT_NAMESPACE));
            }
            
        });
        // Must be called by http thread, or send response.

        for (final String namespace : namespaces) {
            configWatchCenter.addWatchClient(LongPollWatchClient.builder().namespace(namespace).appName(appName)
                    .address(RequestUtil.getRemoteIp(req)).context(req.startAsync()).tag(tag)
                    .watchKey(clientMd5Map).build());
        }
    }
    
    public static boolean isSupportLongPolling(HttpServletRequest req) {
        return null != req.getHeader(LONG_POLLING_HEADER);
    }
    
    private final ConfigWatchCenter configWatchCenter;
    
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public LongPollingService(ConfigWatchCenter configWatchCenter) {
        this.configWatchCenter = configWatchCenter;
    }
    
    public static final String LONG_POLLING_HEADER = "Long-Pulling-Timeout";
    
    public static final String LONG_POLLING_NO_HANG_UP_HEADER = "Long-Pulling-Timeout-No-Hangup";
    
}
