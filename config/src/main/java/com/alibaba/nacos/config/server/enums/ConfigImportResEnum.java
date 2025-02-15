/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.enums;

/**
 * Config Import Result enumeration.
 *
 * @author Nacos
 */
public enum ConfigImportResEnum {
    
    /**
     * Indicates that the configuration was successfully imported.
     */
    SUCCESS("success"),
    
    /**
     * Indicates that there was a failure during the configuration import process.
     */
    FAIL("fail"),
    
    /**
     * Indicates that the configuration was skipped during the import process.
     */
    SKIP("skip"),
    
    /**
     * Indicates an unknown state or result of the configuration import.
     */
    UNKNOWN("unknown");
    
    private final String value;
    
    ConfigImportResEnum(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return this.value;
    }
}