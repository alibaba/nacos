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

package com.alibaba.nacos.plugin.auth.impl.visibility;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteVisibilityGrantServiceTest {
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    private RemoteVisibilityGrantService service;
    
    @BeforeEach
    void setUp() throws Exception {
        prepareRemoteServer();
        service = new RemoteVisibilityGrantService(nacosRestTemplate);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }
    
    @Test
    void testGrantForwardsRequestToRemoteVisibilityApiWithAuthorizationHeader()
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthConstants.AUTHORIZATION_HEADER, "Bearer token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(nacosRestTemplate.<String>postForm(any(String.class), any(Header.class),
            any(Query.class), any(Map.class), eq(String.class))).thenReturn(okText());
        
        service.grant("public", "skill", "demo", "bob", "rw");
        
        ArgumentCaptor<Header> headerCaptor = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Map<String, String>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(nacosRestTemplate).postForm(eq("http://127.0.0.1:8848/nacos/v3/auth/visibility"),
            headerCaptor.capture(), queryCaptor.capture(), bodyCaptor.capture(),
            eq(String.class));
        assertEquals("Bearer token",
            headerCaptor.getValue().getValue(AuthConstants.AUTHORIZATION_HEADER));
        assertEquals("public", queryCaptor.getValue().getValue("namespaceId"));
        assertEquals("skill", bodyCaptor.getValue().get("resourceType"));
        assertEquals("demo", bodyCaptor.getValue().get("resourceName"));
        assertEquals("bob", bodyCaptor.getValue().get("username"));
        assertEquals("rw", bodyCaptor.getValue().get("action"));
    }
    
    @Test
    void testRevokeForwardsRequestToRemoteVisibilityApiWithAccessTokenParameter()
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(Constants.ACCESS_TOKEN, "token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(nacosRestTemplate.<String>delete(any(String.class), any(Header.class),
            any(Query.class), eq(String.class))).thenReturn(okText());
        
        service.revoke("public", "agentspec", "agent-demo", "bob", "r");
        
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(nacosRestTemplate).delete(eq("http://127.0.0.1:8848/nacos/v3/auth/visibility"),
            any(Header.class), queryCaptor.capture(), eq(String.class));
        Query query = queryCaptor.getValue();
        assertEquals("public", query.getValue("namespaceId"));
        assertEquals("agentspec", query.getValue("resourceType"));
        assertEquals("agent-demo", query.getValue("resourceName"));
        assertEquals("bob", query.getValue("username"));
        assertEquals("r", query.getValue("action"));
        assertEquals("token", query.getValue(Constants.ACCESS_TOKEN));
    }
    
    @Test
    void testRemoteFailurePropagatesNacosException() throws Exception {
        when(nacosRestTemplate.<String>delete(any(String.class), any(Header.class),
            any(Query.class), eq(String.class))).thenReturn(
                new HttpRestResult<>(Header.newInstance(), 403, "forbidden", "access denied"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.revoke("public", "skill", "demo", "bob", "r"));
        
        assertEquals(403, exception.getErrCode());
        assertEquals("access denied", exception.getErrMsg());
    }
    
    @Test
    void testUnexpectedRemoteExceptionWrapsAsServerError() throws Exception {
        when(nacosRestTemplate.<String>postForm(any(String.class), any(Header.class),
            any(Query.class), any(Map.class), eq(String.class))).thenThrow(
                new IllegalStateException("boom"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.grant("public", "skill", "demo", "bob", "r"));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("boom", exception.getErrMsg());
    }
    
    @Test
    void testFindAuthorizedResourceNamesIsEmptyInConsoleRuntime() {
        List<String> actual =
            service.findAuthorizedResourceNames("bob", "public", "skill", "r");
        
        assertTrue(actual.isEmpty());
    }
    
    private static HttpRestResult<String> okText() {
        return new HttpRestResult<>(Header.newInstance(), 200, "ok", "success");
    }
    
    private static void prepareRemoteServer() throws Exception {
        setRemoteServerUtilField("serverAddresses", Collections.singletonList("127.0.0.1:8848"));
        setRemoteServerUtilField("index", new AtomicInteger());
        setRemoteServerUtilField("remoteServerContextPath", "/nacos");
    }
    
    private static void setRemoteServerUtilField(String fieldName, Object value) throws Exception {
        Field field = RemoteServerUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
