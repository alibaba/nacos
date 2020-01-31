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

import java.sql.Timestamp;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigHistoryRequest {

    private long id;
    private long configId;
    private ConfigInfo configInfo;
    private String srcIp;
    private String srcUser;
    private Timestamp time;
    private String ops;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getConfigId() {
        return configId;
    }

    public void setConfigId(long configId) {
        this.configId = configId;
    }

    public ConfigInfo getConfigInfo() {
        return configInfo;
    }

    public void setConfigInfo(ConfigInfo configInfo) {
        this.configInfo = configInfo;
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

    public String getOps() {
        return ops;
    }

    public void setOps(String ops) {
        this.ops = ops;
    }

    public static ConfigHistoryRequestBuilder builder() {
        return new ConfigHistoryRequestBuilder();
    }

    public static final class ConfigHistoryRequestBuilder {
        private long id;
        private long configId;
        private ConfigInfo configInfo;
        private String srcIp;
        private String srcUser;
        private Timestamp time;
        private String ops;

        private ConfigHistoryRequestBuilder() {
        }

        public ConfigHistoryRequestBuilder id(long id) {
            this.id = id;
            return this;
        }

        public ConfigHistoryRequestBuilder configId(long id) {
            this.configId = id;
            return this;
        }

        public ConfigHistoryRequestBuilder configInfo(ConfigInfo configInfo) {
            this.configInfo = configInfo;
            return this;
        }

        public ConfigHistoryRequestBuilder srcIp(String srcIp) {
            this.srcIp = srcIp;
            return this;
        }

        public ConfigHistoryRequestBuilder srcUser(String srcUser) {
            this.srcUser = srcUser;
            return this;
        }

        public ConfigHistoryRequestBuilder time(Timestamp time) {
            this.time = time;
            return this;
        }

        public ConfigHistoryRequestBuilder ops(String ops) {
            this.ops = ops;
            return this;
        }

        public ConfigHistoryRequest build() {
            ConfigHistoryRequest configHistoryRequest = new ConfigHistoryRequest();
            configHistoryRequest.setId(id);
            configHistoryRequest.setConfigId(configId);
            configHistoryRequest.setConfigInfo(configInfo);
            configHistoryRequest.setSrcIp(srcIp);
            configHistoryRequest.setSrcUser(srcUser);
            configHistoryRequest.setTime(time);
            configHistoryRequest.setOps(ops);
            return configHistoryRequest;
        }
    }
}
