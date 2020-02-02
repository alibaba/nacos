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

package com.alibaba.nacos.config.server.model.log;

import com.alibaba.nacos.config.server.model.ConfigInfo;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigRequest implements Serializable {

    private long id;
    private String dataId;
    private String group;
    private String tenant;
    private String srcIp;
    private String srcUser;
    private Timestamp time;
    private Map<String, Object> configAdvanceInfo;
    private ConfigInfo configInfo;

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

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getSrcIp() {
        return srcIp;
    }

    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }

    public String getSrcUser() {
        return srcUser;
    }

    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public Map<String, Object> getConfigAdvanceInfo() {
        return configAdvanceInfo;
    }

    public void setConfigAdvanceInfo(Map<String, Object> configAdvanceInfo) {
        this.configAdvanceInfo = configAdvanceInfo;
    }

    public ConfigInfo getConfigInfo() {
        return configInfo;
    }

    public void setConfigInfo(ConfigInfo configInfo) {
        this.configInfo = configInfo;
    }

    public static ConfigRequestBuilder builder() {
        return new ConfigRequestBuilder();
    }

    public static final class ConfigRequestBuilder {
        private long id;
        private String dataId;
        private String group;
        private String tenant;
        private String srcIp;
        private String srcUser;
        private Timestamp time;
        private Map<String, Object> configAdvanceInfo;
        private ConfigInfo configInfo;

        private ConfigRequestBuilder() {
        }

        public ConfigRequestBuilder id(long id) {
            this.id = id;
            return this;
        }

        public ConfigRequestBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public ConfigRequestBuilder group(String group) {
            this.group = group;
            return this;
        }

        public ConfigRequestBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public ConfigRequestBuilder srcIp(String srcIp) {
            this.srcIp = srcIp;
            return this;
        }

        public ConfigRequestBuilder srcUser(String srcUser) {
            this.srcUser = srcUser;
            return this;
        }

        public ConfigRequestBuilder time(Timestamp time) {
            this.time = time;
            return this;
        }

        public ConfigRequestBuilder configAdvanceInfo(Map<String, Object> configAdvanceInfo) {
            this.configAdvanceInfo = configAdvanceInfo;
            return this;
        }

        public ConfigRequestBuilder configInfo(ConfigInfo configInfo) {
            this.configInfo = configInfo;
            return this;
        }

        public ConfigRequest build() {
            ConfigRequest configRequest = new ConfigRequest();
            configRequest.setId(id);
            configRequest.setDataId(dataId);
            configRequest.setGroup(group);
            configRequest.setTenant(tenant);
            configRequest.setSrcIp(srcIp);
            configRequest.setSrcUser(srcUser);
            configRequest.setTime(time);
            configRequest.setConfigAdvanceInfo(configAdvanceInfo);
            configRequest.setConfigInfo(configInfo);
            return configRequest;
        }
    }

    @Override
    public String toString() {
        return "ConfigRequest{" +
                "id=" + id +
                ", dataId='" + dataId + '\'' +
                ", group='" + group + '\'' +
                ", tenant='" + tenant + '\'' +
                ", srcIp='" + srcIp + '\'' +
                ", srcUser='" + srcUser + '\'' +
                ", time=" + time +
                ", configAdvanceInfo=" + configAdvanceInfo +
                ", configInfo=" + configInfo +
                '}';
    }
}
