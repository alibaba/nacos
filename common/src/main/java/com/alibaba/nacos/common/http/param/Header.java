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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Http header.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Header {
    
    public static final Header EMPTY = Header.newInstance();
    
    private final Map<String, String> header;
    
    private final Map<String, List<String>> originalResponseHeader;
    
    private Header() {
        header = new LinkedHashMap<String, String>();
        originalResponseHeader = new LinkedHashMap<String, List<String>>();
        addParam(HttpHeaderConsts.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        addParam(HttpHeaderConsts.ACCEPT_CHARSET, "UTF-8");
        addParam(HttpHeaderConsts.ACCEPT_ENCODING, "gzip");
        addParam(HttpHeaderConsts.CONTENT_ENCODING, "gzip");
    }
    
    public static Header newInstance() {
        return new Header();
    }
    
    public Header addParam(String key, String value) {
        header.put(key, value);
        return this;
    }
    
    public Header setContentType(String contentType) {
        if (contentType == null) {
            contentType = MediaType.APPLICATION_JSON;
        }
        return addParam(HttpHeaderConsts.CONTENT_TYPE, contentType);
    }
    
    public Header build() {
        return this;
    }
    
    public String getValue(String key) {
        return header.get(key);
    }
    
    public Map<String, String> getHeader() {
        return header;
    }
    
    public Iterator<Map.Entry<String, String>> iterator() {
        return header.entrySet().iterator();
    }
    
    /**
     * Transfer to KV part list. The odd index is key and the even index is value.
     *
     * @return KV string list
     */
    public List<String> toList() {
        List<String> list = new ArrayList<String>(header.size() * 2);
        Iterator<Map.Entry<String, String>> iterator = iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            list.add(entry.getKey());
            list.add(entry.getValue());
        }
        return list;
    }
    
    /**
     * Add all KV list to header. The odd index is key and the even index is value.
     *
     * @param list KV list
     * @return header
     */
    public Header addAll(List<String> list) {
        if ((list.size() & 1) != 0) {
            throw new IllegalArgumentException("list size must be a multiple of 2");
        }
        for (int i = 0; i < list.size(); ) {
            header.put(list.get(i++), list.get(i++));
        }
        return this;
    }
    
    /**
     * Add all parameters to header.
     *
     * @param params parameters
     */
    public void addAll(Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            addParam(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * set original format response header.
     *
     * <p>Currently only corresponds to the response header of JDK.
     *
     * @param headers original response header
     */
    public void setOriginalResponseHeader(Map<String, List<String>> headers) {
        this.originalResponseHeader.putAll(headers);
        for (Map.Entry<String, List<String>> entry : this.originalResponseHeader.entrySet()) {
            addParam(entry.getKey(), entry.getValue().get(0));
        }
    }
    
    /**
     * get original format response header.
     *
     * <p>Currently only corresponds to the response header of JDK.
     *
     * @return Map original response header
     */
    public Map<String, List<String>> getOriginalResponseHeader() {
        return this.originalResponseHeader;
    }
    
    public String getCharset() {
        String acceptCharset = getValue(HttpHeaderConsts.ACCEPT_CHARSET);
        if (acceptCharset == null) {
            String contentType = getValue(HttpHeaderConsts.CONTENT_TYPE);
            acceptCharset = StringUtils.isNotBlank(contentType) ? analysisCharset(contentType) : Constants.ENCODE;
        }
        return acceptCharset;
    }
    
    private String analysisCharset(String contentType) {
        String[] values = contentType.split(";");
        String charset = Constants.ENCODE;
        if (values.length == 0) {
            return charset;
        }
        for (String value : values) {
            if (value.startsWith("charset=")) {
                charset = value.substring("charset=".length());
            }
        }
        return charset;
    }
    
    public void clear() {
        header.clear();
        originalResponseHeader.clear();
    }
    
    @Override
    public String toString() {
        return "Header{" + "headerToMap=" + header + '}';
    }
}

