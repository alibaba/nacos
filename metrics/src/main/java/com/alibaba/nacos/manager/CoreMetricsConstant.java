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

package com.alibaba.nacos.manager;

/**
 * Core Metrics Constant.
 * @author holdonbei
 */
public class CoreMetricsConstant {
    
    /**
     * metrics name.
     */
    public static final String N_NACOS_MONITOR = "nacos_monitor";
    
    /**
     * metrics name.
     */
    public static final String N_NACOS_CLIENT_TOTAL_CONNECTIONS = "nacos_client_total_connections";
    
    /**
     * metrics name.
     */
    public static final String N_NACOS_GRPC_REQUEST_COUNT = "nacos_grpc_request_count";
    
    /**
     * metrics tag key.
     */
    public static final String TK_MODULE = "module";
    
    /**
     * metrics tag key.
     */
    public static final String TK_NAME = "name";
    
    /**
     * metrics tag value.
     */
    public static final String TV_CORE = "core";
    
    /**
     * metrics tag value.
     */
    public static final String TV_CONFIG = "config";
    
    /**
     * metrics tag value.
     */
    public static final String TV_LONG_CONNECTION = "longConnection";
}