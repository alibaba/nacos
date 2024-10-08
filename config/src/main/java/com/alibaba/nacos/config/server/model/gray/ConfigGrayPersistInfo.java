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

package com.alibaba.nacos.config.server.model.gray;

/**
 * description.
 *
 * @author rong
 * @date 2024-03-14 10:57
 */
public class ConfigGrayPersistInfo {

    private String type;
    
    private String version;
    
    private String expr;
    
    private int priority;
    
    public ConfigGrayPersistInfo(String type, String version, String expr, int priority) {
        this.type = type;
        this.version = version;
        this.expr = expr;
        this.priority = priority;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getExpr() {
        return expr;
    }
    
    public void setExpr(String expr) {
        this.expr = expr;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
}
