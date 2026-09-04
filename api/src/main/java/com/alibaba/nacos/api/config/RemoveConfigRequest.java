/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config;

import java.io.Serializable;

/**
 * Request object for removing configuration with extensible parameters.
 *
 * <p>This class provides a unified entry point for configuration removal,
 * replacing the existing {@code removeConfig} method with an extensible
 * request object that can carry additional parameters in the future.</p>
 *
 * @author nacos
 * @since 3.3.0
 */
public class RemoveConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Configuration dataId.
     */
    private String dataId;

    /**
     * Configuration group. If null or blank, defaults to DEFAULT_GROUP.
     */
    private String group;

    public RemoveConfigRequest() {
    }

    private RemoveConfigRequest(Builder builder) {
        this.dataId = builder.dataId;
        this.group = builder.group;
    }

    /**
     * Create a new builder instance.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
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

    @Override
    public String toString() {
        return "RemoveConfigRequest{dataId='" + dataId + "', group='" + group + "'}";
    }

    /**
     * Builder for {@link RemoveConfigRequest}.
     */
    public static final class Builder {

        private String dataId;

        private String group;

        private Builder() {
        }

        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public RemoveConfigRequest build() {
            return new RemoveConfigRequest(this);
        }
    }
}
