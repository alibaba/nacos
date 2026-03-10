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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.impl.remote.RemoteServerConnector;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillUploadServiceTest {
    
    @Mock
    RemoteServerConnector remoteServerConnector;
    
    @Mock
    CloseableHttpClient httpClient;
    
    MockedStatic<HttpClients> httpClientMock;
    
    SkillUploadService service;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() throws NacosException {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.auth.admin.enabled", "false");
        EnvUtil.setEnvironment(environment);
        service = new SkillUploadService(remoteServerConnector);
        httpClientMock = Mockito.mockStatic(HttpClients.class);
        httpClientMock.when(HttpClients::createDefault).thenReturn(httpClient);
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(8080);
        member.setState(NodeState.UP);
        when(remoteServerConnector.randomOneHealthyMember()).thenReturn(member);
        when(remoteServerConnector.getServerContextPath()).thenReturn("/nacos");
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
        httpClientMock.close();
    }
    
    @Test
    void testUploadSkillFromZip() throws IOException, NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenReturn(
                JacksonUtils.toJson(Result.success("testSkill")));
        String skillName = service.uploadSkillFromZip("public", zipBytes);
        assertEquals("testSkill", skillName);
    }
    
    @Test
    void testUploadSkillFromZipWithHttpResponseException() throws IOException {
        byte[] zipBytes = "test zip content".getBytes();
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenThrow(
                new HttpResponseException(403, "Forbidden"));
        assertThrows(NacosRuntimeException.class, () -> service.uploadSkillFromZip("public", zipBytes));
    }
    
    @Test
    void testUploadSkillFromZipWithIoException() throws IOException {
        byte[] zipBytes = "test zip content".getBytes();
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenThrow(
                new IOException("Connection refused"));
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
                () -> service.uploadSkillFromZip("public", zipBytes));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testUploadSkillFromZipWithNullNamespace() throws IOException, NacosException {
        byte[] zipBytes = "test zip content".getBytes();
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenReturn(
                JacksonUtils.toJson(Result.success("testSkill")));
        String skillName = service.uploadSkillFromZip(null, zipBytes);
        assertEquals("testSkill", skillName);
    }
}
