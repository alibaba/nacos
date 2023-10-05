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

/**
 * Nacos semantic attributes. Used to define the attributes of the OpenTelemetry trace span.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 **/
public final class NacosSemanticAttributes {
    
    public static final String FUNCTION_CURRENT_NAME = "nacos.function.current.name";
    
    public static final String FUNCTION_CALLED_NAME = "nacos.function.called.name";
    
    public static final String SERVICE_NAME = "nacos.service.name";
    
    public static final String SERVER_ADDRESS = "nacos.server.address";
    
    public static final String CONTENT = "nacos.content";
    
    public static final String CONFIG_TYPE = "nacos.config.type";
    
    public static final String NAMESPACE = "nacos.namespace";
    
    public static final String AGENT_NAME = "nacos.agent.name";
    
    public static final String DATA_ID = "nacos.data.id";
    
    public static final String GROUP = "nacos.group";
    
    public static final String TENANT = "nacos.tenant";
    
    public static final String INSTANCE = "nacos.instance";
    
    public static final String CLUSTER = "nacos.cluster";
    
    public static final String EVENT = "nacos.event";
    
    public static final String TAG = "nacos.tag";
    
    public static final String APPLICATION_NAME = "nacos.application.name";
    
    public static final String PAGE_NO = "nacos.page.no";
    
    public static final String PAGE_SIZE = "nacos.page.size";
    
    public static final String RPC_CLIENT_NAME = "nacos.rpc.client.name";
    
    public static final String SUBSCRIBE = "nacos.subscribe";
    
    public static final String TIMEOUT_MS = "nacos.timeout.ms";
    
    public static final class RequestAttributes {
        
        public static final String REQUEST_ID = "nacos.request.id";
        
        public static final String REQUEST_DATA_ID = "nacos.request.data.id";
        
        public static final String REQUEST_GROUP = "nacos.request.group";
        
        public static final String REQUEST_TENANT = "nacos.request.tenant";
        
        public static final String REQUEST_RESULT = "nacos.request.result";
        
    }
    
}
