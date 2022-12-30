/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.utils.url;

/**
 * Http url components.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/29 20:28
 */
public class HttpUrlComponents {
    
    private final String scheme;
    
    private final String host;
    
    private final String port;
    
    private final String path;
    
    HttpUrlComponents(String scheme, String host, String port, String path) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
    }
    
    public String toString() {
        return scheme + "://" + host + ":" + port + path;
    }
    
    public String getScheme() {
        return scheme;
    }
    
    public String getHost() {
        return host;
    }
    
    public String getPort() {
        return port;
    }
    
    public String getPath() {
        return path;
    }
}
