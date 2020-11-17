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

package com.alibaba.nacos.core.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;

/**
 * A request wrapper to override the parameters.
 *
 * <p>Referenced article is https://blog.csdn.net/xieyuooo/article/details/8447301
 *
 * @author nkorange
 * @since 0.8.0
 */
public class OverrideParameterRequestWrapper extends HttpServletRequestWrapper {
    
    private Map<String, String[]> params = new HashMap<>();
    
    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request The request to wrap
     * @throws IllegalArgumentException if the request is null
     */
    public OverrideParameterRequestWrapper(HttpServletRequest request) {
        super(request);
        this.params.putAll(request.getParameterMap());
    }
    
    public static OverrideParameterRequestWrapper buildRequest(HttpServletRequest request) {
        return new OverrideParameterRequestWrapper(request);
    }
    
    /**
     * build OverrideParameterRequestWrapper and addParameter.
     *
     * @param request origin HttpServletRequest
     * @param name    name
     * @param value   value
     * @return {@link OverrideParameterRequestWrapper}
     */
    public static OverrideParameterRequestWrapper buildRequest(HttpServletRequest request, String name, String value) {
        OverrideParameterRequestWrapper requestWrapper = new OverrideParameterRequestWrapper(request);
        requestWrapper.addParameter(name, value);
        return requestWrapper;
    }
    
    /**
     * build OverrideParameterRequestWrapper and addParameter.
     *
     * @param request          origin HttpServletRequest
     * @param appendParameters need to append to request
     * @return {@link OverrideParameterRequestWrapper}
     */
    public static OverrideParameterRequestWrapper buildRequest(HttpServletRequest request,
            Map<String, String[]> appendParameters) {
        OverrideParameterRequestWrapper requestWrapper = new OverrideParameterRequestWrapper(request);
        requestWrapper.params.putAll(appendParameters);
        return requestWrapper;
    }
    
    @Override
    public String getParameter(String name) {
        String[] values = params.get(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }
    
    @Override
    public Map<String, String[]> getParameterMap() {
        return params;
    }
    
    @Override
    public String[] getParameterValues(String name) {
        return params.get(name);
    }
    
    /**
     * addParameter.
     *
     * @param name  name
     * @param value value
     */
    public void addParameter(String name, String value) {
        if (value != null) {
            params.put(name, new String[] {value});
        }
    }
    
}
