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

package com.alibaba.nacos.common.constant;

/**
 * Nacos header constants.
 *
 * @author ly
 */
public interface HttpHeaderConsts {
    
    String CLIENT_VERSION_HEADER = "Client-Version";
    String USER_AGENT_HEADER = "User-Agent";
    String REQUEST_SOURCE_HEADER = "Request-Source";
    String CONTENT_TYPE = "Content-Type";
    String CONTENT_LENGTH = "Content-Length";
    String ACCEPT_CHARSET = "Accept-Charset";
    String ACCEPT_ENCODING = "Accept-Encoding";
    String CONTENT_ENCODING = "Content-Encoding";
    String CONNECTION = "Requester";
    String REQUEST_ID = "RequestId";
    String REQUEST_MODULE = "Request-Module";
    
}
