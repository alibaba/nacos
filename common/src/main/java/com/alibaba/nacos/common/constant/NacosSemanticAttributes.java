/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.common.constant;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Nacos semantic attributes. Used to define the attributes of the OpenTelemetry trace span.
 **/
public final class NacosSemanticAttributes {
    
    public static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("nacos.server.address");
    
    public static final AttributeKey<String> FUNCTION_CURRENT_NAME = AttributeKey.stringKey(
            "nacos.function.current.name");
    
    public static final AttributeKey<String> FUNCTION_CALLED_NAME = AttributeKey.stringKey(
            "nacos.function.called.name");
    
    public static final AttributeKey<String> CONTENT = AttributeKey.stringKey("nacos.content");
    
    public static final AttributeKey<String> NAMESPACE = AttributeKey.stringKey("nacos.namespace");
    
    public static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("nacos.agent.name");
    
    public static final AttributeKey<String> DATA_ID = AttributeKey.stringKey("nacos.data.id");
    
    public static final AttributeKey<String> GROUP = AttributeKey.stringKey("nacos.group");
    
    public static final AttributeKey<String> TENANT = AttributeKey.stringKey("nacos.tenant");
    
    public static final AttributeKey<String> TAG = AttributeKey.stringKey("nacos.tag");
    
    public static final AttributeKey<String> APPLICATION_NAME = AttributeKey.stringKey("nacos.application.name");
    
    public static final AttributeKey<String> RPC_CLIENT_NAME = AttributeKey.stringKey("nacos.rpc.client.name");
    
    public static final AttributeKey<Long> TIMEOUT_MS = AttributeKey.longKey("nacos.timeout.ms");
    
    public static final class RequestAttributes {
        
        public static final AttributeKey<String> REQUEST_ID = AttributeKey.stringKey("nacos.request.id");
        
        public static final AttributeKey<String> REQUEST_DATA_ID = AttributeKey.stringKey("nacos.request.data.id");
        
        public static final AttributeKey<String> REQUEST_GROUP = AttributeKey.stringKey("nacos.request.group");
        
        public static final AttributeKey<String> REQUEST_TENANT = AttributeKey.stringKey("nacos.request.tenant");
        
    }
    
}
