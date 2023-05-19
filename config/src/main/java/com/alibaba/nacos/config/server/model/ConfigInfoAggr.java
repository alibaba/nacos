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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;

/**
 * ConfigInfoAggr.
 *
 * @author leiwen.zh
 */
public class ConfigInfoAggr implements Serializable {
    
    private static final long serialVersionUID = -3845825581059306364L;
    
    @JsonSerialize(using = ToStringSerializer.class)
    private long id;
    
    private String dataId;
    
    private String group;
    
    private String datumId;
    
    private String tenant;
    
    private String appName;
    
    private String content;
    
    public ConfigInfoAggr(String dataId, String group, String datumId, String content) {
        this.dataId = dataId;
        this.group = group;
        this.datumId = datumId;
        this.content = content;
    }
    
    public ConfigInfoAggr(String dataId, String group, String datumId, String appName, String content) {
        this.dataId = dataId;
        this.group = group;
        this.datumId = datumId;
        this.appName = appName;
        this.content = content;
    }
    
    public ConfigInfoAggr() {
    
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getDatumId() {
        return datumId;
    }
    
    public void setDatumId(String datumId) {
        this.datumId = datumId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((content == null) ? 0 : content.hashCode());
        result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
        result = prime * result + ((datumId == null) ? 0 : datumId.hashCode());
        result = prime * result + ((group == null) ? 0 : group.hashCode());
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ConfigInfoAggr other = (ConfigInfoAggr) obj;
        if (content == null) {
            if (other.content != null) {
                return false;
            }
        } else if (!content.equals(other.content)) {
            return false;
        }
        if (dataId == null) {
            if (other.dataId != null) {
                return false;
            }
        } else if (!dataId.equals(other.dataId)) {
            return false;
        }
        if (datumId == null) {
            if (other.datumId != null) {
                return false;
            }
        } else if (!datumId.equals(other.datumId)) {
            return false;
        }
        if (group == null) {
            if (other.group != null) {
                return false;
            }
        } else if (!group.equals(other.group)) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return "ConfigInfoAggr [dataId=" + dataId + ", group=" + group + ", datumId=" + datumId + ", content=" + content
                + "]";
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
}
