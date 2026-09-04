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
 * Request object for getting configuration with extensible parameters.
 *
 * <p>This class provides a unified entry point for configuration queries,
 * supporting both basic parameters (dataId, group, timeout) and advanced
 * features such as local MD5 for 304-based cache validation.</p>
 *
 * <p>When {@link #localMd5} is set, the server will compare it with the
 * current configuration's MD5. If they match, the server returns a 304
 * Not-Modified response without sending the content, reducing network
 * bandwidth and server CPU/IO overhead.</p>
 *
 * @author nacos
 * @since 3.3.0
 */
public class GetConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Configuration dataId.
     */
    private String dataId;

    /**
     * Configuration group. If null or blank, defaults to DEFAULT_GROUP.
     */
    private String group;

    /**
     * Read timeout in milliseconds.
     */
    private long timeoutMs;

    /**
     * Local cached MD5 of the configuration content.
     *
     * <p>Used for 304-based conditional GET. When set and matching the
     * server-side MD5, the server returns 304 without content.</p>
     */
    private String localMd5;

    public GetConfigRequest() {
    }

    private GetConfigRequest(Builder builder) {
        this.dataId = builder.dataId;
        this.group = builder.group;
        this.timeoutMs = builder.timeoutMs;
        this.localMd5 = builder.localMd5;
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

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getLocalMd5() {
        return localMd5;
    }

    public void setLocalMd5(String localMd5) {
        this.localMd5 = localMd5;
    }

    @Override
    public String toString() {
        return "GetConfigRequest{dataId='" + dataId + "', group='" + group + "', timeoutMs="
            + timeoutMs + ", localMd5='" + localMd5 + "'}";
    }

    /**
     * Builder for {@link GetConfigRequest}.
     */
    public static final class Builder {

        private String dataId;

        private String group;

        private long timeoutMs;

        private String localMd5;

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

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Set the local cached MD5 for 304 conditional GET.
         *
         * @param localMd5 local MD5 of the cached config content
         * @return builder
         */
        public Builder localMd5(String localMd5) {
            this.localMd5 = localMd5;
            return this;
        }

        public GetConfigRequest build() {
            return new GetConfigRequest(this);
        }
    }
}
