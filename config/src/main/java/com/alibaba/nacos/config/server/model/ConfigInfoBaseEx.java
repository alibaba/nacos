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

package com.alibaba.nacos.config.server.model;

/**
 * ConfigInfoBaseEx.
 * And can't add field, to compatible with old interface(If adding a field, then it will occur compatibility problems).
 *
 * @author Nacos
 */
public class ConfigInfoBaseEx extends ConfigInfoBase {
    
    private static final long serialVersionUID = -1L;
    
    /**
     * Single message status code, when querying for batch.
     * And details of message status code, you can see Constants.java.
     */
    private int status;
    
    /**
     * Single message information, when querying for batch.
     */
    private String message;
    
    public ConfigInfoBaseEx() {
        super();
    }
    
    public ConfigInfoBaseEx(String dataId, String group, String content) {
        super(dataId, group, content);
    }
    
    public ConfigInfoBaseEx(String dataId, String group, String content, int status, String message) {
        super(dataId, group, content);
        this.status = status;
        this.message = message;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }
    
    @Override
    public String toString() {
        return "ConfigInfoBaseEx [status=" + status + ", message=" + message + ", dataId=" + getDataId() + ", group()="
                + getGroup() + ", content()=" + getContent() + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
