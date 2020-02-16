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

package com.alibaba.nacos.consistency.request;

import java.util.Collection;
import java.util.Properties;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class GetRequest {

    private String key;
    private Collection<String> keys;
    private String requestBody;
    private Properties context = new Properties();

    public GetRequest(String key, String requestBody) {
        this.key = key;
        this.requestBody = requestBody;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Collection<String> getKeys() {
        return keys;
    }

    public void setKeys(Collection<String> keys) {
        this.keys = keys;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Properties getContext() {
        return context;
    }

    public void setContext(Properties context) {
        this.context = context;
    }

    public void addValue(String key, String value) {
        this.context.put(key, value);
    }

    public String getValue(String key) {
        return (String) this.context.get(key);
    }

    @Override
    public String toString() {
        return "GetRequest{" +
                "key='" + key + '\'' +
                ", requestBody='" + requestBody + '\'' +
                ", context=" + context +
                '}';
    }

    public static GetRequestBuilder builder() {
        return new GetRequestBuilder();
    }

    public static final class GetRequestBuilder {
        private String key;
        private Collection<String> keys;
        private String requestBody;
        private Properties context = new Properties();

        private GetRequestBuilder() {
        }

        public GetRequestBuilder key(String key) {
            this.key = key;
            return this;
        }

        public GetRequestBuilder keys(Collection<String> keys) {
            this.keys = keys;
            return this;
        }

        public GetRequestBuilder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public GetRequestBuilder context(Properties context) {
            this.context.putAll(context);
            return this;
        }

        public GetRequestBuilder addValue(String key, String value) {
            this.context.put(key, value);
            return this;
        }

        public GetRequest build() {
            GetRequest getRequest = new GetRequest(key, requestBody);
            getRequest.setContext(context);
            getRequest.setKeys(keys);
            return getRequest;
        }
    }
}
