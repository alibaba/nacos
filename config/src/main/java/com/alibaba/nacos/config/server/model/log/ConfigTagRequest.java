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

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigTagRequest implements Serializable {

    private String dataId;
    private String group;
    private String tenant;
    private ConfigInfo configInfo;
    private String tag;
    private String srcIp;
    private String srcUser;
    private Timestamp time;

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

    public ConfigInfo getConfigInfo() {
        return configInfo;
    }

    public void setConfigInfo(ConfigInfo configInfo) {
        this.configInfo = configInfo;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    public static ConfigTagRequestBuilder builder() {
        return new ConfigTagRequestBuilder();
    }

    public static final class ConfigTagRequestBuilder {
        private String dataId;
        private String group;
        private String tenant;
        private ConfigInfo configInfo;
        private String tag;
        private String srcIp;
        private String srcUser;
        private Timestamp time;

        private ConfigTagRequestBuilder() {
        }

        public ConfigTagRequestBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public ConfigTagRequestBuilder group(String group) {
            this.group = group;
            return this;
        }

        public ConfigTagRequestBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public ConfigTagRequestBuilder configInfo(ConfigInfo configInfo) {
            this.configInfo = configInfo;
            return this;
        }

        public ConfigTagRequestBuilder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public ConfigTagRequestBuilder srcIp(String srcIp) {
            this.srcIp = srcIp;
            return this;
        }

        public ConfigTagRequestBuilder srcUser(String srcUser) {
            this.srcUser = srcUser;
            return this;
        }

        public ConfigTagRequestBuilder time(Timestamp time) {
            this.time = time;
            return this;
        }

        public ConfigTagRequest build() {
            ConfigTagRequest configTagRequest = new ConfigTagRequest();
            configTagRequest.setDataId(dataId);
            configTagRequest.setGroup(group);
            configTagRequest.setTenant(tenant);
            configTagRequest.setConfigInfo(configInfo);
            configTagRequest.setTag(tag);
            configTagRequest.setSrcIp(srcIp);
            configTagRequest.setSrcUser(srcUser);
            configTagRequest.setTime(time);
            return configTagRequest;
        }
    }

    @Override
    public String toString() {
        return "ConfigTagRequest{" +
                "dataId='" + dataId + '\'' +
                ", group='" + group + '\'' +
                ", tenant='" + tenant + '\'' +
                ", configInfo=" + configInfo +
                ", tag='" + tag + '\'' +
                ", srcIp='" + srcIp + '\'' +
                ", srcUser='" + srcUser + '\'' +
                ", time=" + time +
                '}';
    }
}
