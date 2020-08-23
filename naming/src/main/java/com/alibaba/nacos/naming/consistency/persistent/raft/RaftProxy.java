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

package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.MapUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.HttpClientManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Raft http proxy.
 *
 * @author nacos
 */
@Component
public class RaftProxy {
    
    private final NacosRestTemplate restTemplate = HttpClientManager.getInstance().getNacosRestTemplate();
    
    /**
     * Proxy get method.
     *
     * @param server target server
     * @param api    api path
     * @param params parameters
     * @throws Exception any exception during request
     */
    public void proxyGet(String server, String api, Map<String, String> params) throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
        }
        String url = "http://" + server + ApplicationUtils.getContextPath() + api;
        RestResult<Object> result = restTemplate
                .get(url, Header.EMPTY, Query.newInstance().initParams(params), String.class);
        if (!result.ok()) {
            throw new IllegalStateException("leader failed, caused by: " + result.getMessage());
        }
    }
    
    /**
     * Proxy specified method.
     *
     * @param server target server
     * @param api    api path
     * @param params parameters
     * @param method http method
     * @throws Exception any exception during request
     */
    public void proxy(String server, String api, Map<String, String> params, HttpMethod method) throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
        }
        String url = "http://" + server + ApplicationUtils.getContextPath() + api;
        RestResult<String> result;
        switch (method) {
            case GET:
                result = restTemplate.get(url, Header.EMPTY, Query.newInstance().initParams(params), String.class);
                break;
            case POST:
                result = restTemplate.postForm(url, Header.EMPTY, Query.EMPTY, params, String.class);
                break;
            case DELETE:
                result = restTemplate.delete(url, Header.EMPTY, Query.newInstance().initParams(params), String.class);
                break;
            default:
                throw new RuntimeException("unsupported method:" + method);
        }
        
        if (!result.ok()) {
            throw new IllegalStateException("leader failed, caused by: " + result.getMessage());
        }
    }
    
    /**
     * Proxy post method with large body.
     *
     * @param server  target server
     * @param api     api path
     * @param content body
     * @param headers headers
     * @throws Exception any exception during request
     */
    public void proxyPostLarge(String server, String api, String content, Map<String, String> headers)
            throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
        }
        String url = "http://" + server + ApplicationUtils.getContextPath() + api;
        Header header = Header.newInstance();
        if (MapUtils.isNotEmpty(headers)) {
            header.addAll(headers);
        }
        RestResult<Object> result = restTemplate.postJson(url, header, content, String.class);
        if (!result.ok()) {
            throw new IllegalStateException("leader failed, caused by: " + result.getMessage());
        }
    }
}
