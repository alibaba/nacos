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
public class ConfigBetaRequest implements Serializable {

    private String dataId;
    private String group;
    private String tenant;
    private ConfigInfo configInfo;
    private String betaIps;
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

    public String getBetaIps() {
        return betaIps;
    }

    public void setBetaIps(String betaIps) {
        this.betaIps = betaIps;
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

    public static ConfigBetaRequestBuilder builder() {
        return new ConfigBetaRequestBuilder();
    }

    public static final class ConfigBetaRequestBuilder {
        private String dataId;
        private String group;
        private String tenant;
        private ConfigInfo configInfo;
        private String betaIps;
        private String srcIp;
        private String srcUser;
        private Timestamp time;

        private ConfigBetaRequestBuilder() {
        }

        public ConfigBetaRequestBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public ConfigBetaRequestBuilder group(String group) {
            this.group = group;
            return this;
        }

        public ConfigBetaRequestBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public ConfigBetaRequestBuilder configInfo(ConfigInfo configInfo) {
            this.configInfo = configInfo;
            return this;
        }

        public ConfigBetaRequestBuilder betaIps(String betaIps) {
            this.betaIps = betaIps;
            return this;
        }

        public ConfigBetaRequestBuilder srcIp(String srcIp) {
            this.srcIp = srcIp;
            return this;
        }

        public ConfigBetaRequestBuilder srcUser(String srcUser) {
            this.srcUser = srcUser;
            return this;
        }

        public ConfigBetaRequestBuilder time(Timestamp time) {
            this.time = time;
            return this;
        }

        public ConfigBetaRequest build() {
            ConfigBetaRequest configBetaRequest = new ConfigBetaRequest();
            configBetaRequest.setDataId(dataId);
            configBetaRequest.setGroup(group);
            configBetaRequest.setTenant(tenant);
            configBetaRequest.setConfigInfo(configInfo);
            configBetaRequest.setBetaIps(betaIps);
            configBetaRequest.setSrcIp(srcIp);
            configBetaRequest.setSrcUser(srcUser);
            configBetaRequest.setTime(time);
            return configBetaRequest;
        }
    }

    @Override
    public String toString() {
        return "ConfigBetaRequest{" +
                "dataId='" + dataId + '\'' +
                ", group='" + group + '\'' +
                ", tenant='" + tenant + '\'' +
                ", configInfo=" + configInfo +
                ", betaIps='" + betaIps + '\'' +
                ", srcIp='" + srcIp + '\'' +
                ", srcUser='" + srcUser + '\'' +
                ", time=" + time +
                '}';
    }
}
