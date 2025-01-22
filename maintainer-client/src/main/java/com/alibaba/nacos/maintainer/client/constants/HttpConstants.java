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

package com.alibaba.nacos.maintainer.client.constants;

import com.alibaba.nacos.maintainer.client.utils.StringUtils;

/**
 * Nacos header constants.
 *
 * @author ly
 */
public class HttpConstants {
    
    public static final String CLIENT_VERSION_HEADER = "Client-Version";
    
    public static final String USER_AGENT_HEADER = "User-Agent";
    
    public static final String REQUEST_SOURCE_HEADER = "Request-Source";
    
    public static final String CONTENT_TYPE = "Content-Type";
    
    public static final String CONTENT_LENGTH = "Content-Length";
    
    public static final String ACCEPT_CHARSET = "Accept-Charset";
    
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    
    public static final String CONTENT_ENCODING = "Content-Encoding";
    
    public static final String CONNECTION = "Requester";
    
    public static final String REQUEST_ID = "RequestId";
    
    public static final String REQUEST_MODULE = "Request-Module";
    
    public static final String APP_FILED = "app";
    
    public static final String CLIENT_IP = "clientIp";
    
    public static final String ENCODE = "UTF-8";
    
    public static final String PERSIST_ENCODE = getPersistEncode();
    
    public static final String NACOS_PERSIST_ENCODE_KEY = "nacosPersistEncodingKey";
    
    public static final String DEFAULT_NACOS_ENCODE = "UTF-8";
    
    static String getPersistEncode() {
        String persistEncode = System.getenv(NACOS_PERSIST_ENCODE_KEY);
        if (StringUtils.isBlank(persistEncode)) {
            persistEncode = System.getProperty(NACOS_PERSIST_ENCODE_KEY);
            if (StringUtils.isBlank(persistEncode)) {
                persistEncode = DEFAULT_NACOS_ENCODE;
            }
        }
        return persistEncode;
    }
}
