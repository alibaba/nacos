/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigOpenApiControllerTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    private ConfigOpenApiController configOpenApiController;
    
    @BeforeEach
    void setUp() {
        MockEnvironment mockEnvironment = new MockEnvironment();
        EnvUtil.setEnvironment(mockEnvironment);
        configOpenApiController = new ConfigOpenApiController(configQueryChainService);
    }
    
    @Test
    void testGetConfigExist() throws NacosApiException, UnsupportedEncodingException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setContent("test");
        response.setMd5("testMd5");
        response.setConfigType("text");
        response.setEncryptedDataKey(null);
        response.setLastModified(System.currentTimeMillis());
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId("test");
        configForm.setGroupName("test");
        Result<ConfigQueryResponse> actual = configOpenApiController.getConfig(configForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("test", actual.getData().getContent());
        assertEquals("testMd5", actual.getData().getMd5());
        assertEquals("text", actual.getData().getContentType());
        assertEquals(response.getLastModified(), actual.getData().getLastModified());
        assertFalse(actual.getData().isBeta());
    }
    
    @Test
    void testGetConfigNonExist() throws NacosApiException, UnsupportedEncodingException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setContent(null);
        response.setEncryptedDataKey(null);
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId("test");
        configForm.setGroupName("test");
        Result<ConfigQueryResponse> actual = configOpenApiController.getConfig(configForm);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getMsg(), actual.getMessage());
    }
    
    @Test
    void testGetConfigExistBeta() throws NacosApiException, UnsupportedEncodingException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setContent("test");
        response.setMd5("testMd5");
        response.setConfigType("text");
        response.setEncryptedDataKey(null);
        response.setLastModified(System.currentTimeMillis());
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY);
        response.setMatchedGray(new ConfigCacheGray());
        BetaGrayRule betaGrayRule = new BetaGrayRule("1.1.1.1", 1);
        ConfigGrayPersistInfo grayPersistInfo = GrayRuleManager.constructConfigGrayPersistInfo(betaGrayRule);
        response.getMatchedGray().resetGrayRule(JacksonUtils.toJson(grayPersistInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId("test");
        configForm.setGroupName("test");
        Result<ConfigQueryResponse> actual = configOpenApiController.getConfig(configForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("test", actual.getData().getContent());
        assertEquals("testMd5", actual.getData().getMd5());
        assertEquals("text", actual.getData().getContentType());
        assertEquals(response.getLastModified(), actual.getData().getLastModified());
        assertTrue(actual.getData().isBeta());
    }
    
    @Test
    void testGetConfigExistGray() throws NacosApiException, UnsupportedEncodingException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setContent("test");
        response.setMd5("testMd5");
        response.setConfigType("text");
        response.setEncryptedDataKey(null);
        response.setLastModified(System.currentTimeMillis());
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY);
        response.setMatchedGray(new ConfigCacheGray());
        TagGrayRule tagGrayRule = new TagGrayRule("1.1.1.1", 1);
        ConfigGrayPersistInfo grayPersistInfo = GrayRuleManager.constructConfigGrayPersistInfo(tagGrayRule);
        response.getMatchedGray().resetGrayRule(JacksonUtils.toJson(grayPersistInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId("test");
        configForm.setGroupName("test");
        Result<ConfigQueryResponse> actual = configOpenApiController.getConfig(configForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("test", actual.getData().getContent());
        assertEquals("testMd5", actual.getData().getMd5());
        assertEquals("text", actual.getData().getContentType());
        assertEquals(response.getLastModified(), actual.getData().getLastModified());
        assertFalse(actual.getData().isBeta());
        assertEquals("1.1.1.1", actual.getData().getTag());
    }
}