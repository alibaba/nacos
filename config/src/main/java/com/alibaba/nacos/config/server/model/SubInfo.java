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

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * SubInfo.
 *
 * @author Nacos
 */
public class SubInfo implements Serializable {
    
    private static final long serialVersionUID = -3900485932969066685L;
    
    private String appName;
    
    private String dataId;
    
    private String group;
    
    private String localIp;
    
    private Timestamp date;
    
    public String getAppName() {
        return appName;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public Timestamp getDate() {
        return new Timestamp(date.getTime());
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public void setDate(Timestamp date) {
        this.date = new Timestamp(date.getTime());
    }
    
    public String getLocalIp() {
        return localIp;
    }
    
    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }
    
}
