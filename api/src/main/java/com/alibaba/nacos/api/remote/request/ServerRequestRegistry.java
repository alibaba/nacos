/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.request;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRequestTypeConstants;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * server request registry.
 *
 * @author liuzunfei
 * @version $Id: ServerRequestRegistry.java, v 0.1 2020年08月06日 1:48 PM liuzunfei Exp $
 */
public class ServerRequestRegistry {
    
    private static final Map<String, Class> REGISTRY_RESPONSES = new HashMap<String, Class>();
    
    static {
        REGISTRY_RESPONSES.put(RequestTypeConstants.CONNECTION_RESET, ConnectResetRequest.class);
        REGISTRY_RESPONSES.put(ConfigRequestTypeConstants.CONFIG_CHANGE_NOTIFY, ConfigChangeNotifyRequest.class);
        REGISTRY_RESPONSES.put(NamingRemoteConstants.NOTIFY_SUBSCRIBER, NotifySubscriberRequest.class);
    }
    
    public static Class getClassByType(String type) {
        return REGISTRY_RESPONSES.get(type);
    }
}
