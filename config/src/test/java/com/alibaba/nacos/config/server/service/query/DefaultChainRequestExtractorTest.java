/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultChainRequestExtractorTest {
    
    @InjectMocks
    private DefaultChainRequestExtractor defaultChainRequestExtractor;
    
    @Mock
    private HttpServletRequest request;
    
    private MockedStatic<RequestUtil> requestUtilMockedStatic;
    
    @BeforeEach
    public void setUp() {
        requestUtilMockedStatic = Mockito.mockStatic(RequestUtil.class);
        Mockito.reset(request);
    }
    
    @AfterEach
    public void tearDown() {
        requestUtilMockedStatic.close();
    }
    
    @Test
    public void extractWithAllParametersShouldReturnCorrectConfigQueryChainRequest() {
        when(request.getParameter("dataId")).thenReturn("dataId");
        when(request.getParameter("group")).thenReturn("group");
        when(request.getParameter("tenant")).thenReturn("tenant");
        when(request.getParameter("tag")).thenReturn("tag");
        when(request.getHeader(VIPSERVER_TAG)).thenReturn("autoTag");
        requestUtilMockedStatic.when(() -> RequestUtil.getRemoteIp(request)).thenReturn("127.0.0.1");
        
        ConfigQueryChainRequest result = defaultChainRequestExtractor.extract(request);
        
        assertEquals("dataId", result.getDataId());
        assertEquals("group", result.getGroup());
        assertEquals("tenant", result.getTenant());
        assertEquals("tag", result.getTag());
        assertEquals("127.0.0.1", result.getAppLabels().get(BetaGrayRule.CLIENT_IP_LABEL));
        assertEquals("tag", result.getAppLabels().get(TagGrayRule.VIP_SERVER_TAG_LABEL));
    }
    
    @Test
    public void extractWithEmptyTenantShouldReturnCorrectConfigQueryChainRequest() {
        when(request.getParameter("dataId")).thenReturn("dataId");
        when(request.getParameter("group")).thenReturn("group");
        when(request.getParameter("tenant")).thenReturn("");
        when(request.getParameter("tag")).thenReturn("tag");
        when(request.getHeader(VIPSERVER_TAG)).thenReturn("autoTag");
        requestUtilMockedStatic.when(() -> RequestUtil.getRemoteIp(request)).thenReturn("127.0.0.1");
        
        ConfigQueryChainRequest result = defaultChainRequestExtractor.extract(request);
        
        assertEquals("dataId", result.getDataId());
        assertEquals("group", result.getGroup());
        assertEquals("", result.getTenant());
        assertEquals("tag", result.getTag());
        assertEquals("127.0.0.1", result.getAppLabels().get(BetaGrayRule.CLIENT_IP_LABEL));
        assertEquals("tag", result.getAppLabels().get(TagGrayRule.VIP_SERVER_TAG_LABEL));
    }
    
    @Test
    public void extractWithEmptyTagAndAutoTagShouldReturnCorrectConfigQueryChainRequest() {
        when(request.getParameter("dataId")).thenReturn("dataId");
        when(request.getParameter("group")).thenReturn("group");
        when(request.getParameter("tenant")).thenReturn("tenant");
        when(request.getParameter("tag")).thenReturn("");
        when(request.getHeader(VIPSERVER_TAG)).thenReturn("");
        requestUtilMockedStatic.when(() -> RequestUtil.getRemoteIp(request)).thenReturn("127.0.0.1");
        
        ConfigQueryChainRequest result = defaultChainRequestExtractor.extract(request);
        
        assertEquals("dataId", result.getDataId());
        assertEquals("group", result.getGroup());
        assertEquals("tenant", result.getTenant());
        assertEquals("", result.getTag());
        assertEquals("127.0.0.1", result.getAppLabels().get(BetaGrayRule.CLIENT_IP_LABEL));
        assertNull(result.getAppLabels().get(TagGrayRule.VIP_SERVER_TAG_LABEL));
    }
    
    @Test
    public void extractWithAutoTagShouldReturnCorrectConfigQueryChainRequest() {
        when(request.getParameter("dataId")).thenReturn("dataId");
        when(request.getParameter("group")).thenReturn("group");
        when(request.getParameter("tenant")).thenReturn("tenant");
        when(request.getParameter("tag")).thenReturn("");
        when(request.getHeader(VIPSERVER_TAG)).thenReturn("autoTag");
        when(RequestUtil.getRemoteIp(request)).thenReturn("127.0.0.1");
        
        ConfigQueryChainRequest result = defaultChainRequestExtractor.extract(request);
        
        assertEquals("dataId", result.getDataId());
        assertEquals("group", result.getGroup());
        assertEquals("tenant", result.getTenant());
        assertEquals("", result.getTag());
        assertEquals("127.0.0.1", result.getAppLabels().get(BetaGrayRule.CLIENT_IP_LABEL));
        assertEquals("autoTag", result.getAppLabels().get(TagGrayRule.VIP_SERVER_TAG_LABEL));
    }
    
    @Test
    public void extractWithConfigQueryRequestShouldReturnCorrectConfigQueryChainRequest() {
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId("dataId");
        configQueryRequest.setGroup("group");
        configQueryRequest.setTenant("tenant");
        configQueryRequest.setTag("tag");
        
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        ConfigQueryChainRequest result = defaultChainRequestExtractor.extract(configQueryRequest, requestMeta);
        
        assertEquals("dataId", result.getDataId());
        assertEquals("group", result.getGroup());
        assertEquals("tenant", result.getTenant());
        assertEquals("tag", result.getTag());
        assertEquals("127.0.0.1", result.getAppLabels().get(BetaGrayRule.CLIENT_IP_LABEL));
        assertEquals("tag", result.getAppLabels().get(TagGrayRule.VIP_SERVER_TAG_LABEL));
    }
}