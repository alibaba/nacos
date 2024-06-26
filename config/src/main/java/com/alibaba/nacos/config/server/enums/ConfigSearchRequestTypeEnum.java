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

package com.alibaba.nacos.config.server.enums;

/**
 * Config search request type enum.
 *
 * @author hmydk
 */
public enum ConfigSearchRequestTypeEnum {
    
    /**
     * ip.
     */
    IP("IP"),
    
    /**
     * config.
     */
    CONFIG("config");
    
    
    private final String type;
    
    ConfigSearchRequestTypeEnum(String type) {
        this.type = type;
    }
    
    /**
     * check type is legal.
     *
     * @param type type
     * @return true or false
     */
    public static boolean checkTypeLegal(String type) {
        for (ConfigSearchRequestTypeEnum configSearchRequestTypeEnum : ConfigSearchRequestTypeEnum.values()) {
            if (configSearchRequestTypeEnum.getType().equals(type)) {
                return true;
            }
        }
        return false;
    }
    
    public String getType() {
        return type;
    }
}
