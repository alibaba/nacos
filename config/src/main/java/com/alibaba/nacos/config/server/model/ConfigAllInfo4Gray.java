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

package com.alibaba.nacos.config.server.model;

/**
 * ConfigAllInfo4Gray.
 *
 * @author Nacos
 */
public class ConfigAllInfo4Gray extends ConfigInfo {
    
    private static final long serialVersionUID = -7926709237557990936L;
    
    private String srcUser;
    
    private String srcIp;
    
    private long gmtCreate;
    
    private long gmtModified;
    
    private String grayName;
    
    private String grayRule;
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public long getGmtCreate() {
        return gmtCreate;
    }
    
    public void setGmtCreate(long gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
    
    public long getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(long gmtModified) {
        this.gmtModified = gmtModified;
    }
    
    public String getGrayName() {
        return grayName;
    }
    
    public void setGrayName(String grayName) {
        this.grayName = grayName;
    }
    
    public String getGrayRule() {
        return grayRule;
    }
    
    public void setGrayRule(String grayRule) {
        this.grayRule = grayRule;
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
