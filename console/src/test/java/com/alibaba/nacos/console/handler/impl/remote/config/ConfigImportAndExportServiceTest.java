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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NacosMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Collection;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigImportAndExportServiceTest {
    
    @Mock
    NacosMemberManager memberManager;
    @Mock
    ClusterHandler clusterHandler;
    
    @Mock
    CloseableHttpClient httpClient;
    
    MockedStatic<HttpClients> httpClientMock;
    
    ConfigImportAndExportService service;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() throws NacosException {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.auth.admin.enabled", "false");
        EnvUtil.setEnvironment(environment);
        service = new ConfigImportAndExportService(memberManager, clusterHandler);
        httpClientMock = Mockito.mockStatic(HttpClients.class);
        httpClientMock.when(HttpClients::createDefault).thenReturn(httpClient);
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(8080);
        member.setState(NodeState.UP);

        Collection nacosMembers = new HashSet<>();
        nacosMembers.add(member);
        when(memberManager.allMembers()).thenReturn(nacosMembers);
        when(clusterHandler.getNodeList(any())).thenReturn(nacosMembers);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
        httpClientMock.close();
    }
    
    @Test
    void importConfig() throws IOException, NacosException {
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn(MediaType.MULTIPART_FORM_DATA_VALUE);
        when(mockFile.getOriginalFilename()).thenReturn("file");
        InputStream mockInputStream = Mockito.mock(InputStream.class);
        when(mockFile.getInputStream()).thenReturn(mockInputStream);
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenReturn(
                JacksonUtils.toJson(Result.success()));
        Result<Map<String, Object>> actual = service.importConfig("user", "namespaceId", SameConfigPolicy.OVERWRITE,
                mockFile, "127.0.0.1", "app");
        assertEquals(0, actual.getCode());
    }
    
    @Test
    void importConfigWithRequestException() throws IOException {
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn(MediaType.MULTIPART_FORM_DATA_VALUE);
        when(mockFile.getOriginalFilename()).thenReturn("file");
        InputStream mockInputStream = Mockito.mock(InputStream.class);
        when(mockFile.getInputStream()).thenReturn(mockInputStream);
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenThrow(
                new HttpResponseException(403, "test"));
        assertThrows(NacosRuntimeException.class,
                () -> service.importConfig("user", "namespaceId", SameConfigPolicy.OVERWRITE, mockFile, "127.0.0.1",
                        "app"));
    }
    
    @Test
    void importConfigWithIoException() throws IOException {
        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        when(mockFile.getContentType()).thenReturn(MediaType.MULTIPART_FORM_DATA_VALUE);
        when(mockFile.getOriginalFilename()).thenReturn("file");
        InputStream mockInputStream = Mockito.mock(InputStream.class);
        when(mockFile.getInputStream()).thenReturn(mockInputStream);
        when(httpClient.execute(any(ClassicHttpRequest.class), any(BasicHttpClientResponseHandler.class))).thenThrow(
                new IOException("test"));
        assertThrows(NacosRuntimeException.class,
                () -> service.importConfig("user", "namespaceId", SameConfigPolicy.OVERWRITE, mockFile, "127.0.0.1",
                        "app"), "Import config to server failed.");
    }
    
    @Test
    void exportConfig() throws Exception {
        ResponseEntity<byte[]> mock = ResponseEntity.ok().body(new byte[0]);
        when(httpClient.execute(any(ClassicHttpRequest.class),
                any(ConfigImportAndExportService.ExportHttpClientResponseHandler.class))).thenAnswer(
                    invocation -> mock);
        ResponseEntity<byte[]> actual = service.exportConfig("dataId", "group", "namespaceId", "appName",
                Collections.singletonList(1L));
        assertEquals(mock, actual);
    }
    
    @Test
    void exportConfigWithRequestException() throws Exception {
        when(httpClient.execute(any(ClassicHttpRequest.class),
                any(ConfigImportAndExportService.ExportHttpClientResponseHandler.class))).thenThrow(
                    new HttpResponseException(403, "test"));
        assertThrows(NacosRuntimeException.class,
                () -> service.exportConfig("dataId", "group", "namespaceId", "appName", Collections.singletonList(1L)));
    }
    
    @Test
    void exportConfigWithIoException() throws Exception {
        when(httpClient.execute(any(ClassicHttpRequest.class),
                any(ConfigImportAndExportService.ExportHttpClientResponseHandler.class))).thenThrow(
                    new IOException("test"));
        assertThrows(NacosRuntimeException.class,
                () -> service.exportConfig("dataId", "group", "namespaceId", "appName", Collections.singletonList(1L)),
                "Export config to server failed.");
    }
    
    @Test
    void exportHttpClientResponseHandlerHandleResponse() throws ProtocolException, IOException, NacosException {
        // remove lenient warning
        memberManager.allMembers();
        clusterHandler.getNodeList("");
        ClassicHttpResponse mockResponse = Mockito.mock(ClassicHttpResponse.class);
        when(mockResponse.getHeader("Content-Disposition")).thenReturn(
                new BasicHeader("Content-Disposition", "testDisposition"));
        ByteArrayInputStream mockInputStream = new ByteArrayInputStream("test".getBytes());
        HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
        when(mockEntity.getContent()).thenReturn(mockInputStream);
        ConfigImportAndExportService.ExportHttpClientResponseHandler handler = new ConfigImportAndExportService.ExportHttpClientResponseHandler();
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        ResponseEntity<byte[]> actual = handler.handleResponse(mockResponse);
        assertTrue(actual.getStatusCode().is2xxSuccessful());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, actual.getHeaders().getContentType());
        assertEquals("testDisposition", actual.getHeaders().get("Content-Disposition").get(0));
        assertEquals("test", new String(actual.getBody()));
    }
    
    @Test
    void exportHttpClientResponseHandlerHandleResponseWithException() throws ProtocolException, NacosException {
        // remove lenient warning
        memberManager.allMembers();
        clusterHandler.getNodeList("");
        ClassicHttpResponse mockResponse = Mockito.mock(ClassicHttpResponse.class);
        when(mockResponse.getHeader("Content-Disposition")).thenThrow(new ProtocolException());
        ConfigImportAndExportService.ExportHttpClientResponseHandler handler = new ConfigImportAndExportService.ExportHttpClientResponseHandler();
        assertThrows(NacosRuntimeException.class, () -> handler.handleResponse(mockResponse));
    }
}