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

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigTagRelationRequest {

    private long configId;
    private String configTags;
    private String dataId;
    private String group;
    private String tenant;

    public long getConfigId() {
        return configId;
    }

    public void setConfigId(long configId) {
        this.configId = configId;
    }

    public String getConfigTags() {
        return configTags;
    }

    public void setConfigTags(String configTags) {
        this.configTags = configTags;
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

    public static ConfigTagRelationRequestBuilder builder() {
        return new ConfigTagRelationRequestBuilder();
    }

    public static final class ConfigTagRelationRequestBuilder {
        private long configId;
        private String configTags;
        private String dataId;
        private String group;
        private String tenant;

        private ConfigTagRelationRequestBuilder() {
        }

        public ConfigTagRelationRequestBuilder configId(long configId) {
            this.configId = configId;
            return this;
        }

        public ConfigTagRelationRequestBuilder configTags(String configTags) {
            this.configTags = configTags;
            return this;
        }

        public ConfigTagRelationRequestBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public ConfigTagRelationRequestBuilder group(String group) {
            this.group = group;
            return this;
        }

        public ConfigTagRelationRequestBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public ConfigTagRelationRequest build() {
            ConfigTagRelationRequest configTagRelationRequest = new ConfigTagRelationRequest();
            configTagRelationRequest.setConfigId(configId);
            configTagRelationRequest.setConfigTags(configTags);
            configTagRelationRequest.setDataId(dataId);
            configTagRelationRequest.setGroup(group);
            configTagRelationRequest.setTenant(tenant);
            return configTagRelationRequest;
        }
    }
}
