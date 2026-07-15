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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.auth.util.AuthHeaderUtil;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.collections.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Http Client.
 *
 * @author nacos
 */
public class HttpClient {
    
    private static final int TIME_OUT_MILLIS = 10000;
    
    private static final int CON_TIME_OUT_MILLIS = 5000;
    
    private static final NacosRestTemplate APACHE_SYNC_NACOS_REST_TEMPLATE =
        HttpClientManager.getApacheRestTemplate();
    
    private static final String ENCODING = "UTF-8";
    
    private static final String NOFIX = "1";
    
    /**
     * Request http get method.
     *
     * @param url         url
     * @param headers     headers
     * @param paramValues params
     * @return {@link RestResult} as response
     */
    public static RestResult<String> httpGet(String url, List<String> headers,
        Map<String, String> paramValues) {
        return request(url, headers, paramValues, StringUtils.EMPTY, CON_TIME_OUT_MILLIS,
            TIME_OUT_MILLIS, "UTF-8",
            HttpMethod.GET);
    }
    
    /**
     * Do http request.
     *
     * @param url            request url
     * @param headers        request headers
     * @param paramValues    request params
     * @param body           request body
     * @param connectTimeout timeout of connection
     * @param readTimeout    timeout of request
     * @param encoding       charset of request
     * @param method         http method
     * @return {@link RestResult} as response
     */
    public static RestResult<String> request(String url, List<String> headers,
        Map<String, String> paramValues,
        String body, int connectTimeout, int readTimeout, String encoding, String method) {
        Header header = Header.newInstance();
        if (CollectionUtils.isNotEmpty(headers)) {
            header.addAll(headers);
        }
        header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        header.addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        header.addParam(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);
        header.addParam(HttpHeaderConsts.REQUEST_SOURCE_HEADER, EnvUtil.getLocalAddress());
        header.addParam(HttpHeaderConsts.ACCEPT_CHARSET, encoding);
        AuthHeaderUtil.addIdentityToHeader(header,
            NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(
                NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE));
        
        HttpClientConfig httpClientConfig =
            HttpClientConfig.builder().setConTimeOutMillis(connectTimeout)
                .setReadTimeOutMillis(readTimeout).build();
        Query query = Query.newInstance().initParams(paramValues);
        query.addParam(FieldsConstants.ENCODING, ENCODING);
        query.addParam(FieldsConstants.NOFIX, NOFIX);
        try {
            return APACHE_SYNC_NACOS_REST_TEMPLATE.exchange(url, httpClientConfig, header, query,
                body, method, String.class);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while request: {}, caused: {}", url, e);
            return RestResult.<String>builder().withCode(500).withMsg(e.toString()).build();
        }
    }
    
    /**
     * Translate parameter map.
     *
     * @param parameterMap parameter map
     * @return new parameter
     */
    public static Map<String, String> translateParameterMap(Map<String, String[]> parameterMap) {
        
        Map<String, String> map = new HashMap<>(16);
        for (String key : parameterMap.keySet()) {
            map.put(key, parameterMap.get(key)[0]);
        }
        return map;
    }
}
