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

import com.alibaba.nacos.api.ai.model.skills.SkillSubscription;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SkillSubscriptionServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class SkillSubscriptionServiceImplTest {
    
    @Mock
    private SkillSubscriptionStorage storage;
    
    @Mock
    private SkillOperationService skillOperationService;
    
    private SkillSubscriptionServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new SkillSubscriptionServiceImpl(storage, skillOperationService);
    }
    
    @AfterEach
    void tearDown() {
        com.alibaba.nacos.core.context.RequestContextHolder.removeContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
    
    @Test
    void testSubscribeMergesAndSortsByName() throws NacosException {
        SkillSubscription existed = subscription("z-skill");
        SkillSubscriptionDocument existedDocument = document(existed);
        when(storage.get(eq("public"), eq("anonymous"))).thenReturn(existedDocument);
        
        SkillSubscription first = subscription("b-skill");
        SkillSubscription second = subscription("a-skill");
        SkillSubscriptionDocument actual = service.subscribe("public", Arrays.asList(first,
            second));
        
        assertEquals("a-skill", actual.getSubscriptions().get(0).getName());
        assertEquals("b-skill", actual.getSubscriptions().get(1).getName());
        assertEquals("z-skill", actual.getSubscriptions().get(2).getName());
        verify(skillOperationService).getSkillDetail("public", "a-skill");
        verify(skillOperationService).getSkillDetail("public", "b-skill");
        
        ArgumentCaptor<SkillSubscriptionDocument> captor =
            ArgumentCaptor.forClass(SkillSubscriptionDocument.class);
        verify(storage).save(eq("public"), eq("anonymous"), captor.capture());
        assertEquals(3, captor.getValue().getSubscriptions().size());
    }
    
    @Test
    void testUnsubscribeRemovesNames() throws NacosException {
        SkillSubscriptionDocument existedDocument = document(subscription("a-skill"),
            subscription("b-skill"));
        when(storage.get(anyString(), anyString())).thenReturn(existedDocument);
        
        SkillSubscriptionDocument actual = service.unsubscribe(null,
            Collections.singletonList("a-skill"));
        
        assertEquals("public", actual.getNamespaceId());
        assertEquals("anonymous", actual.getSubscriber());
        assertEquals(1, actual.getSubscriptions().size());
        assertEquals("b-skill", actual.getSubscriptions().get(0).getName());
    }
    
    @Test
    void testUnsubscribeUsesRequestUsernameParameter() throws NacosException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(com.alibaba.nacos.api.common.Constants.USERNAME, "nacos");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));
        SkillSubscriptionDocument existedDocument = document(subscription("doc-format"));
        when(storage.get(eq("public"), eq("nacos"))).thenReturn(existedDocument);
        
        SkillSubscriptionDocument actual = service.unsubscribe("public",
            Collections.singletonList("doc-format"));
        
        assertEquals("nacos", actual.getSubscriber());
        assertEquals(0, actual.getSubscriptions().size());
        ArgumentCaptor<SkillSubscriptionDocument> captor =
            ArgumentCaptor.forClass(SkillSubscriptionDocument.class);
        verify(storage).save(eq("public"), eq("nacos"), captor.capture());
        assertEquals(0, captor.getValue().getSubscriptions().size());
    }
    
    @Test
    void testUnsubscribeRejectsEmptyNames() {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.unsubscribe("public", Collections.singletonList(" ")));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        verifyNoInteractions(storage);
    }
    
    @Test
    void testListSubscriptionsUsesRequestUsernameParameter() throws NacosException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(com.alibaba.nacos.api.common.Constants.USERNAME, "nacos");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));
        SkillSubscriptionDocument existedDocument = document(subscription("doc-format"));
        when(storage.get(eq("public"), eq("nacos"))).thenReturn(existedDocument);
        
        SkillSubscriptionDocument actual = service.listSubscriptions("public");
        
        assertEquals("nacos", actual.getSubscriber());
        assertEquals(1, actual.getSubscriptions().size());
        assertEquals("doc-format", actual.getSubscriptions().get(0).getName());
        verify(storage).get(eq("public"), eq("nacos"));
    }
    
    @Test
    void testListSubscriptionsUsesRequestUsernameHeader() throws NacosException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(com.alibaba.nacos.api.common.Constants.USERNAME, "nacos");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));
        SkillSubscriptionDocument existedDocument = document(subscription("doc-format"));
        when(storage.get(eq("public"), eq("nacos"))).thenReturn(existedDocument);
        
        SkillSubscriptionDocument actual = service.listSubscriptions("public");
        
        assertEquals("nacos", actual.getSubscriber());
        assertEquals(1, actual.getSubscriptions().size());
        assertEquals("doc-format", actual.getSubscriptions().get(0).getName());
        verify(storage).get(eq("public"), eq("nacos"));
    }
    
    private SkillSubscriptionDocument document(SkillSubscription... subscriptions) {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        document.setSubscriptions(Arrays.asList(subscriptions));
        return document;
    }
    
    private SkillSubscription subscription(String name) {
        SkillSubscription subscription = new SkillSubscription();
        subscription.setName(name);
        return subscription;
    }
}
