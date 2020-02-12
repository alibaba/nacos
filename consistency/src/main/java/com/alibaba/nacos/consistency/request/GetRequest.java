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

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class GetRequest {

    private String key;
    private String requestBody;

    public GetRequest(String key, String requestBody) {
        this.key = key;
        this.requestBody = requestBody;
    }

    public String getKey() {
        return key;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public static GetRequestBuilder builder() {
        return new GetRequestBuilder();
    }

    public static final class GetRequestBuilder {
        private String key;
        private String requestBody;

        private GetRequestBuilder() {
        }

        public GetRequestBuilder key(String key) {
            this.key = key;
            return this;
        }

        public GetRequestBuilder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public GetRequest build() {
            return new GetRequest(key, requestBody);
        }
    }
}
