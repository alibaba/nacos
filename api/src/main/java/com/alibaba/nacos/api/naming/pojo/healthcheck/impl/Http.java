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

package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of health checker for HTTP.
 *
 * @author yangyi
 */
public class Http extends AbstractHealthChecker {
    public static final String TYPE = "HTTP";

    private String path = "";

    private String headers = "";

    private int expectedResponseCode = 200;

    public Http() {
        super(TYPE);
    }

    public int getExpectedResponseCode() {
        return expectedResponseCode;
    }

    public void setExpectedResponseCode(int expectedResponseCode) {
        this.expectedResponseCode = expectedResponseCode;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    @JsonIgnore
    public Map<String, String> getCustomHeaders() {
        if (StringUtils.isBlank(headers)) {
            return Collections.emptyMap();
        }
        Map<String, String> headerMap = new HashMap<String, String>(16);
        for (String s : headers.split(Constants.NAMING_HTTP_HEADER_SPILIER)) {
            String[] splits = s.split(":");
            if (splits.length != 2) {
                continue;
            }
            headerMap.put(StringUtils.trim(splits[0]), StringUtils.trim(splits[1]));
        }
        return headerMap;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, headers, expectedResponseCode);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Http)) {
            return false;
        }

        Http other = (Http) obj;

        if (!StringUtils.equals(type, other.getType())) {
            return false;
        }

        if (!StringUtils.equals(path, other.getPath())) {
            return false;
        }
        if (!StringUtils.equals(headers, other.getHeaders())) {
            return false;
        }
        return expectedResponseCode == other.getExpectedResponseCode();
    }

    @Override
    public Http clone() throws CloneNotSupportedException {
        Http config = new Http();
        config.setPath(this.getPath());
        config.setHeaders(this.getHeaders());
        config.setExpectedResponseCode(this.getExpectedResponseCode());
        return config;
    }
}
