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
 * Request object for publishing configuration with extensible parameters.
 *
 * <p>This class unifies all publish-related parameters into a single request
 * object, replacing the multiple overloaded {@code publishConfig} methods.
 * It supports CAS (Compare-And-Swap) publish via {@link #casMd5}, which is
 * essential for encrypted configurations where the plaintext content cannot
 * be retrieved via {@code getConfig}.</p>
 *
 * <p>To perform a CAS publish on an encrypted config:</p>
 * <ol>
 *     <li>Call {@code getConfigWithResult} to obtain the {@link ConfigQueryResult}</li>
 *     <li>Use {@code result.getMd5()} as the {@code casMd5} in this request</li>
 *     <li>Publish with the new encrypted content</li>
 * </ol>
 *
 * @author nacos
 * @since 3.3.0
 */
public class PublishConfigRequest implements Serializable {

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
     * Configuration content to publish.
     */
    private String content;

    /**
     * Configuration type (json, yaml, properties, text, etc.).
     * If null, defaults to the default config type.
     */
    private String type;

    /**
     * CAS MD5 for compare-and-swap publish.
     *
     * <p>When set, the server will only accept the publish if the current
     * config's MD5 matches this value. This prevents concurrent modifications
     * from overwriting each other. For encrypted configs, obtain this value
     * from {@link ConfigQueryResult#getMd5()}.</p>
     */
    private String casMd5;

    public PublishConfigRequest() {
    }

    private PublishConfigRequest(Builder builder) {
        this.dataId = builder.dataId;
        this.group = builder.group;
        this.content = builder.content;
        this.type = builder.type;
        this.casMd5 = builder.casMd5;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCasMd5() {
        return casMd5;
    }

    public void setCasMd5(String casMd5) {
        this.casMd5 = casMd5;
    }

    @Override
    public String toString() {
        return "PublishConfigRequest{dataId='" + dataId + "', group='" + group + "', type='"
            + type + "', casMd5='" + casMd5 + "', content length="
            + (content != null ? content.length() : 0) + "}";
    }

    /**
     * Builder for {@link PublishConfigRequest}.
     */
    public static final class Builder {

        private String dataId;

        private String group;

        private String content;

        private String type;

        private String casMd5;

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

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Set the CAS MD5 for compare-and-swap publish.
         *
         * <p>For encrypted configs, obtain this from
         * {@link ConfigQueryResult#getMd5()} returned by
         * {@code getConfigWithResult}.</p>
         *
         * @param casMd5 expected MD5 of the current config content
         * @return builder
         */
        public Builder casMd5(String casMd5) {
            this.casMd5 = casMd5;
            return this;
        }

        public PublishConfigRequest build() {
            return new PublishConfigRequest(this);
        }
    }
}
