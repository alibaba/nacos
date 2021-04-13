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
import com.alibaba.nacos.api.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

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
    
    private static final long serialVersionUID = 551826315222362349L;
    
    private String path = "";
    
    private String headers = "";
    
    private int expectedResponseCode = 200;
    
    public Http() {
        super(Http.TYPE);
    }
    
    public int getExpectedResponseCode() {
        return this.expectedResponseCode;
    }
    
    public void setExpectedResponseCode(final int expectedResponseCode) {
        this.expectedResponseCode = expectedResponseCode;
    }
    
    public String getPath() {
        return this.path;
    }
    
    public void setPath(final String path) {
        this.path = path;
    }
    
    public String getHeaders() {
        return this.headers;
    }
    
    public void setHeaders(final String headers) {
        this.headers = headers;
    }
    
    @JsonIgnore
    public Map<String, String> getCustomHeaders() {
        if (StringUtils.isBlank(headers)) {
            return Collections.emptyMap();
        }
        final Map<String, String> headerMap = new HashMap<String, String>(16);
        for (final String s : headers.split(Constants.NAMING_HTTP_HEADER_SPLITTER)) {
            final String[] splits = s.split(":");
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
    public boolean equals(final Object obj) {
        if (!(obj instanceof Http)) {
            return false;
        }
        
        final Http other = (Http) obj;
        
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
        final Http config = new Http();
        config.setPath(getPath());
        config.setHeaders(getHeaders());
        config.setExpectedResponseCode(getExpectedResponseCode());
        return config;
    }
}
