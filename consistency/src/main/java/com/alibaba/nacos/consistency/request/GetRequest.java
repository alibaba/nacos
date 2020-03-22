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

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * // TODO 请求体中需要携带的信息需要在考虑清楚下
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class GetRequest implements Serializable {

    private static final long serialVersionUID = -3588214197362245921L;

    private String group;

    private Object ctx;

    private Map<String, String> info;

    public static GetRequestBuilder builder() {
        return new GetRequestBuilder();
    }

    @Override
    public String toString() {
        return "GetRequest{" +
                "biz='" + group + '\'' +
                ", ctx=" + ctx +
                '}';
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public <T> T getCtx() {
        return (T) ctx;
    }

    public void setCtx(Object ctx) {
        this.ctx = ctx;
    }

    public Map<String, String> getInfo() {
        return info;
    }

    public void setInfo(Map<String, String> info) {
        if (this.info == null) {
            this.info = info;
        } else {
            this.info.putAll(info);
        }
    }

    public void addValue(String key, String value) {
        if (info == null) {
            info = new HashMap<>(4);
        }
        info.put(key, value);
    }

    public String getValue(String key) {
        if (info == null) {
            return null;
        }
        return info.get(key);
    }

    public static final class GetRequestBuilder {
        private String group;
        private Object ctx;
        private Map<String, String> info;

        private GetRequestBuilder() {
        }

        public GetRequestBuilder group(String group) {
            this.group = group;
            return this;
        }

        public GetRequestBuilder ctx(Object ctx) {
            this.ctx = ctx;
            return this;
        }

        public GetRequestBuilder info(Map<String, String> info) {
            if (this.info == null) {
                this.info = info;
            } else {
                this.info.putAll(info);
            }
            return this;
        }

        public GetRequestBuilder addInfo(String key, String value) {
            if (this.info == null) {
                info = new HashMap<>(4);
            }
            info.put(key, value);
            return this;
        }

        public GetRequest build() {
            GetRequest getRequest = new GetRequest();
            getRequest.setGroup(group);
            getRequest.setCtx(ctx);
            getRequest.setInfo(info);
            return getRequest;
        }
    }

}
