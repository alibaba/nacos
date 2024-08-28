/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.enums;

/**
 * The type of API.
 *
 * @author zhangyukun
 */
public enum ApiType {
    /**
     * console API.
     */
    CONSOLE_API("CONSOLE_API"),
    /**
     * server API.
     */
    OPEN_API("OPEN_API");
    
    private final String description;
    
    ApiType(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
