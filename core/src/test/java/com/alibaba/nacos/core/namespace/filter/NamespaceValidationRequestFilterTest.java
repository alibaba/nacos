/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.namespace.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link NamespaceValidationRequestFilter} unit test.
 *
 * @author FangYuan
 * @since 2025-08-12 16:24:00
 */
@ExtendWith(MockitoExtension.class)
class NamespaceValidationRequestFilterTest {

    private NamespaceValidationRequestFilter namespaceValidationFilter;

    @Mock
    private NamespaceOperationService namespaceOperationService;

    @Mock
    private Request request;

    @Mock
    private RequestMeta requestMeta;

    @Mock
    private AbstractNamingRequest namingRequest;

    @BeforeEach
    void setUp() {
        namespaceValidationFilter = new NamespaceValidationRequestFilter(namespaceOperationService);
    }

    @Test
    void testFilterWithoutNamespaceValidationAnnotation() throws NacosException {
        Response actual = namespaceValidationFilter.filter(request, requestMeta, MockWithoutAnnotation.class);
        assertNull(actual);
    }

    @Test
    void testFilterWithNamespaceValidationDisabled() throws NacosException {
        Response actual = namespaceValidationFilter.filter(request, requestMeta, MockWithDisabledValidation.class);
        assertNull(actual);
    }

    @Test
    void testFilterWithNonNamingRequest() throws NacosException {
        Response actual = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);
        assertNull(actual);
    }

    @Test
    void testFilterWithNamingRequestAndNamespaceExists() throws NacosException {
        when(namingRequest.getNamespace()).thenReturn("test-namespace");
        when(namespaceOperationService.isNamespaceExist("test-namespace")).thenReturn(true);

        Response actual = namespaceValidationFilter.filter(namingRequest, requestMeta, MockWithEnabledValidation.class);
        assertNull(actual);
    }

    @Test
    void testFilterWithNamingRequestAndNamespaceNotExists() throws NacosException {
        when(namingRequest.getNamespace()).thenReturn("non-existent-namespace");
        when(namespaceOperationService.isNamespaceExist("non-existent-namespace")).thenReturn(false);

        Response actual = namespaceValidationFilter.filter(namingRequest, requestMeta, MockWithEnabledValidation.class);
        assertNotNull(actual);
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getErrorCode());
        assertEquals("Namespace 'non-existent-namespace' does not exist. Please create the namespace first.",
                actual.getMessage());
    }

    @Test
    void testFilterWithNamingRequestAndNamespaceCheckThrowsNacosApiException() throws NacosException {
        when(namingRequest.getNamespace()).thenReturn("public");
        when(namespaceOperationService.isNamespaceExist("public"))
                .thenThrow(new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.NAMESPACE_ALREADY_EXIST,
                        "namespaceId [public] is default namespace id and already exist."));

        Response actual = namespaceValidationFilter.filter(namingRequest, requestMeta, MockWithEnabledValidation.class);
        assertNull(actual);
    }

    @Test
    void testFilterWithNamingRequestAndNamespaceCheckThrowsException() throws NacosException {
        when(namingRequest.getNamespace()).thenReturn("test-namespace");
        when(namespaceOperationService.isNamespaceExist("test-namespace"))
                .thenThrow(new RuntimeException("Database error"));

        Response actual = namespaceValidationFilter.filter(namingRequest, requestMeta, MockWithEnabledValidation.class);
        assertNotNull(actual);
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getErrorCode());
        assertEquals("Namespace 'test-namespace' does not exist. Please create the namespace first.",
                actual.getMessage());
    }

    @Test
    void testFilterWithGlobalConfigDisabled() throws NacosException {
        InstanceRequest request = new InstanceRequest();
        request.setNamespace("test-namespace");
        RequestMeta meta = new RequestMeta();

        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(false);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, meta, MockWithEnabledValidation.class);

            // When global config is disabled, filter should return null (skip validation)
            assertNull(response);
        }
    }

    @Test
    void testFilterWithGlobalConfigEnabled() throws NacosException {
        InstanceRequest request = new InstanceRequest();
        request.setNamespace("non-existent-namespace");
        RequestMeta meta = new RequestMeta();

        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);
        when(namespaceOperationService.isNamespaceExist("non-existent-namespace")).thenReturn(false);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, meta, MockWithEnabledValidation.class);

            // When global config is enabled and namespace doesn't exist, should return error response
            assertNotNull(response);
            assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), response.getErrorCode());
            assertEquals("Namespace 'non-existent-namespace' does not exist. Please create the namespace first.",
                    response.getMessage());
        }
    }

    @Test
    void testFilterWithGlobalConfigEnabledAndNamespaceExists() throws NacosException {
        InstanceRequest request = new InstanceRequest();
        request.setNamespace("existing-namespace");
        RequestMeta meta = new RequestMeta();

        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);
        when(namespaceOperationService.isNamespaceExist("existing-namespace")).thenReturn(true);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, meta, MockWithEnabledValidation.class);

            // When global config is enabled and namespace exists, should return null (pass validation)
            assertNull(response);
        }
    }

    static class MockWithoutAnnotation extends RequestHandler<Request, Response> {
        @Override
        public Response handle(Request request, RequestMeta meta) throws NacosException {
            return new Response() {
            };
        }
    }

    static class MockWithEnabledValidation extends RequestHandler<InstanceRequest, InstanceResponse> {

        @NamespaceValidation
        @Override
        public InstanceResponse handle(InstanceRequest request, RequestMeta meta) throws NacosException {
            return new InstanceResponse();
        }
    }

    static class MockWithDisabledValidation extends RequestHandler<Request, Response> {
        @NamespaceValidation(enable = false)
        @Override
        public Response handle(Request request, RequestMeta meta) throws NacosException {
            return new Response() {
            };
        }
    }
}