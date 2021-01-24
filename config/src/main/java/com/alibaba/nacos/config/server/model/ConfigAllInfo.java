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
 * ConfigAllInfo.
 *
 * @author Nacos
 */
public class ConfigAllInfo extends ConfigInfo {
    
    private static final long serialVersionUID = 296578467953931353L;
    
    private long createTime;
    
    private long modifyTime;
    
    private String createUser;
    
    private String createIp;
    
    private String desc;
    
    private String use;
    
    private String effect;
    
    private String schema;
    
    private String configTags;
    
    public ConfigAllInfo() {
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public long getModifyTime() {
        return modifyTime;
    }
    
    public void setModifyTime(long modifyTime) {
        this.modifyTime = modifyTime;
    }
    
    public String getCreateUser() {
        return createUser;
    }
    
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }
    
    public String getCreateIp() {
        return createIp;
    }
    
    public void setCreateIp(String createIp) {
        this.createIp = createIp;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getUse() {
        return use;
    }
    
    public void setUse(String use) {
        this.use = use;
    }
    
    public String getEffect() {
        return effect;
    }
    
    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
    }
    
    public String getConfigTags() {
        return configTags;
    }
    
    public void setConfigTags(String configTags) {
        this.configTags = configTags;
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
