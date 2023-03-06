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

package com.alibaba.nacos.common.trace;

/**
 * The types of health check.
 *
 * @author yanda
 */
public enum HealthCheckType {
    /**
     * Instance heart beat timeout.
     */
    CLIENT_BEAT("client_beat"),
    /**
     * Http health check.
     */
    HTTP_HEALTH_CHECK("http"),
    /**
     * Mysql health check.
     */
    MYSQL_HEALTH_CHECK("mysql"),
    /**
     * Tcp super sense health check .
     */
    TCP_SUPER_SENSE("tcp");
    
    private String prefix;
    
    private HealthCheckType(String prefix) {
        this.prefix = prefix;
    }
    
    public String getPrefix() {
        return prefix;
    }
}
