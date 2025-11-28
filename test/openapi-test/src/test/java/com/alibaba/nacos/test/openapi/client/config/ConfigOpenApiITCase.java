/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.openapi.client.config;

import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link com.alibaba.nacos.config.server.controller.v3.ConfigOpenApiController}.
 *
 * @author xiweng.yy
 */
public class ConfigOpenApiITCase {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOpenApiITCase.class);
    
    private static final String LOCAL_ADDRESS = "http://127.0.0.1:8848";
    
    private static final String CONFIG_CONTROLLER_PATH = "/nacos" + Constants.CONFIG_V3_CLIENT_API_PATH;
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final String DATA_ID = "test_data_id";
    
    private static final String GROUP = "test_group";
    
    private CloseableHttpClient httpClient;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClientBuilder.create().build();
        nacosRestTemplate = new NacosRestTemplate(LOGGER,
                new DefaultHttpClientRequest(httpClient, RequestConfig.DEFAULT));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        nacosRestTemplate.close();
    }
    
    @Test
    public void testGetConfig() throws Exception {
        HttpRestResult<String> result = getConfig(DATA_ID, GROUP, DEFAULT_NAMESPACE);
        LOGGER.debug("getConfig result: {}", JacksonUtils.toJson(result));
        assertTrue(result.ok());
        assertNotNull(result.getData());
        Result<ConfigQueryResponse> actual = JacksonUtils.toObj(result.getData(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
    }
    
    private HttpRestResult<String> getConfig(String dataId, String group, String namespace) throws Exception {
        String url = LOCAL_ADDRESS + CONFIG_CONTROLLER_PATH;
        
        Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", group)
                .addParam("namespaceId", namespace);
        
        return nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
    }
}