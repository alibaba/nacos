/*
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
 */

package com.alibaba.nacos.plugin.auth.constant;

/**
 * The type of Nacos API.
 *
 * @author zhangyukun
 * @author xiweng.yy
 */
public enum ApiType {
    
    /**
     * Admin API which nacos maintainer or administrator used.
     */
    ADMIN_API("ADMIN_API"),
    /**
     * Console API which nacos console used.
     */
    CONSOLE_API("CONSOLE_API"),
    /**
     * Open API which client used or basic data operation.
     */
    OPEN_API("OPEN_API"),
    /**
     * Inner API which used between nacos servers.
     */
    INNER_API("INNER_API");
    
    private final String description;
    
    ApiType(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
