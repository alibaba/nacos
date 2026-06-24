/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscription;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SkillClientSubscriptionServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillClientSubscriptionServiceImplTest {
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    private SkillClientSubscriptionServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new SkillClientSubscriptionServiceImpl(configQueryChainService);
    }
    
    @AfterEach
    void tearDown() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
    
    @Test
    void testListSubscriptionsReadsRuntimeConfigDump() throws NacosException {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        SkillSubscription subscription = new SkillSubscription();
        subscription.setName("doc-format");
        document.getSubscriptions().add(subscription);
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(JacksonUtils.toJson(document));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(com.alibaba.nacos.api.common.Constants.USERNAME, "alice");
        request.setParameter("subscriber", "bob");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));
        ArgumentCaptor<ConfigQueryChainRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        when(configQueryChainService.handle(requestCaptor.capture())).thenReturn(response);
        
        SkillSubscriptionDocument actual = service.listSubscriptions("public");
        
        assertEquals("public", actual.getNamespaceId());
        assertEquals("alice", actual.getSubscriber());
        assertEquals(Constants.Skills.SKILL_SUBSCRIPTION_GROUP, actual.getGroupId());
        assertEquals(ConfigSkillSubscriptionStorage.buildDataId("alice"), actual.getDataId());
        assertEquals("doc-format", actual.getSubscriptions().get(0).getName());
        ConfigQueryChainRequest queryRequest = requestCaptor.getValue();
        assertEquals(ConfigSkillSubscriptionStorage.buildDataId("alice"),
            queryRequest.getDataId());
        assertEquals(Constants.Skills.SKILL_SUBSCRIPTION_GROUP, queryRequest.getGroup());
        assertEquals("public", queryRequest.getTenant());
    }
    
    @Test
    void testListSubscriptionsReturnsEmptyDocumentWhenRuntimeConfigMissing()
        throws NacosException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(com.alibaba.nacos.api.common.Constants.USERNAME, "alice");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));
        when(configQueryChainService.handle(any())).thenReturn(response);
        
        SkillSubscriptionDocument actual = service.listSubscriptions(null);
        
        assertEquals("public", actual.getNamespaceId());
        assertEquals("alice", actual.getSubscriber());
        assertEquals(0, actual.getSubscriptions().size());
    }
    
    @Test
    void testListSubscriptionsReturnsConflictWhenRuntimeConfigDumping() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT);
        when(configQueryChainService.handle(any())).thenReturn(response);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.listSubscriptions("public"));
        
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
    }
}
