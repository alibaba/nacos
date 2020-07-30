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

package com.alibaba.nacos.api.naming.remote;

/**
 * Retain all naming module request type constants.
 *
 * @author liuzunfei
 * @author xiweng.yy
 */
public class NamingRemoteConstants {
    
    public static final String REGISTER_INSTANCE = "registerInstance";
    
    public static final String DE_REGISTER_INSTANCE = "deregisterInstance";
    
    public static final String QUERY_SERVICE = "queryService";
    
    public static final String SUBSCRIBE_SERVICE = "subscribeService";
    
    public static final String NOTIFY_SUBSCRIBER = "notifySubscriber";
    
    public static final String LIST_SERVICE = "listService";
    
    public static final String FORWARD_INSTANCE = "forwardInstance";
    
    public static final String FORWARD_HEART_BEAT = "forwardHeartBeat";
}
