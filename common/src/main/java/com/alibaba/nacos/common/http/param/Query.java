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

package com.alibaba.nacos.common.http.param;

import com.alibaba.nacos.common.utils.MapUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Http Query object.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Query {
    
    private boolean isEmpty = true;
    
    public static final Query EMPTY = Query.newInstance();
    
    private Map<String, Object> params;
    
    private static final String DEFAULT_ENC = "UTF-8";
    
    public Query() {
        params = new LinkedHashMap<String, Object>();
    }
    
    public static Query newInstance() {
        return new Query();
    }
    
    /**
     * Add query parameter.
     *
     * @param key   key
     * @param value value
     * @return this query
     */
    public Query addParam(String key, Object value) {
        isEmpty = false;
        params.put(key, value);
        return this;
    }
    
    public Object getValue(String key) {
        return params.get(key);
    }
    
    /**
     * Add all parameters as query parameter.
     *
     * @param params parameters
     * @return this query
     */
    public Query initParams(Map<String, String> params) {
        if (MapUtil.isNotEmpty(params)) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                addParam(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }
    
    /**
     * Add query parameters from KV list. KV list: odd index is key, even index is value.
     *
     * @param list KV list
     */
    public void initParams(List<String> list) {
        if ((list.size() & 1) != 0) {
            throw new IllegalArgumentException("list size must be a multiple of 2");
        }
        for (int i = 0; i < list.size(); ) {
            addParam(list.get(i++), list.get(i++));
        }
    }
    
    /**
     * Print query as a http url param string. Like K=V&K=V.
     *
     * @return http url param string
     */
    public String toQueryUrl() {
        StringBuilder urlBuilder = new StringBuilder();
        Set<Map.Entry<String, Object>> entrySet = params.entrySet();
        int i = entrySet.size();
        for (Map.Entry<String, Object> entry : entrySet) {
            try {
                if (null != entry.getValue()) {
                    urlBuilder.append(entry.getKey()).append("=")
                            .append(URLEncoder.encode(String.valueOf(entry.getValue()), DEFAULT_ENC));
                    if (i > 1) {
                        urlBuilder.append("&");
                    }
                }
                i--;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        
        return urlBuilder.toString();
    }
    
    public void clear() {
        isEmpty = false;
        params.clear();
    }
    
    public boolean isEmpty() {
        return isEmpty;
    }
    
}
