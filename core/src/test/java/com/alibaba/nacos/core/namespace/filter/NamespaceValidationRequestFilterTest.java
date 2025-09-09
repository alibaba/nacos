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
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.InstanceRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
    private AbstractRpcParamExtractor paramExtractor;

    @BeforeEach
    void setUp() {
        namespaceValidationFilter = new NamespaceValidationRequestFilter(namespaceOperationService);
    }

    @Test
    void testFilterWithGlobalConfigDisabled() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(false);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When global config is disabled, filter should return null (skip validation)
            assertNull(response);
        }
    }

    @Test
    void testFilterWithoutNamespaceValidationAnnotation() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithoutNamespaceValidationAnnotation.class);

            // When no @NamespaceValidation annotation is found, should return null
            assertNull(response);
        }
    }

    @Test
    void testFilterWithNamespaceValidationDisabled() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithDisabledValidation.class);

            // When @NamespaceValidation(enable=false), should return null
            assertNull(response);
        }
    }

    @Test
    void testFilterWithoutExtractorAnnotation() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        try (MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class)) {
            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidationButNoExtractor.class);

            // When no extractor annotation is found, should return null
            assertNull(response);
        }
    }

    @Test
    void testFilterWithExtractorReturningEmptyList() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenReturn(paramExtractor);
            when(paramExtractor.extractParam(request)).thenReturn(Collections.emptyList());

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When extractor returns empty list, should return null
            assertNull(response);
        }
    }

    @Test
    void testFilterWithNullNamespaceParam() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId(null);
        List<ParamInfo> paramInfoList = Arrays.asList(paramInfo);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenReturn(paramExtractor);
            when(paramExtractor.extractParam(request)).thenReturn(paramInfoList);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When namespace is null, should skip validation and return null
            assertNull(response);
        }
    }

    @Test
    void testFilterWithExistingNamespace() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId("existing-namespace");
        List<ParamInfo> paramInfoList = Collections.singletonList(paramInfo);

        when(namespaceOperationService.namespaceExists("existing-namespace")).thenReturn(true);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenReturn(paramExtractor);
            when(paramExtractor.extractParam(request)).thenReturn(paramInfoList);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When namespace exists, should return null (pass validation)
            assertNull(response);
        }
    }

    @Test
    void testFilterWithNonExistingNamespace() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId("non-existing-namespace");
        List<ParamInfo> paramInfoList = Arrays.asList(paramInfo);

        when(namespaceOperationService.namespaceExists("non-existing-namespace")).thenReturn(false);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenReturn(paramExtractor);
            when(paramExtractor.extractParam(request)).thenReturn(paramInfoList);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When namespace doesn't exist, should return error response
            assertNotNull(response);
            assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), response.getErrorCode());
            assertEquals("Namespace 'non-existing-namespace' does not exist. Please create the namespace first.",
                    response.getMessage());
        }
    }

    @Test
    void testFilterWithMultipleParamInfos() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        ParamInfo paramInfo1 = new ParamInfo();
        paramInfo1.setNamespaceId("existing-namespace");
        ParamInfo paramInfo2 = new ParamInfo();
        paramInfo2.setNamespaceId("non-existing-namespace");
        List<ParamInfo> paramInfoList = Arrays.asList(paramInfo1, paramInfo2);

        when(namespaceOperationService.namespaceExists("existing-namespace")).thenReturn(true);
        when(namespaceOperationService.namespaceExists("non-existing-namespace")).thenReturn(false);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenReturn(paramExtractor);
            when(paramExtractor.extractParam(request)).thenReturn(paramInfoList);

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When one namespace doesn't exist, should return error response
            assertNotNull(response);
            assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), response.getErrorCode());
            assertEquals("Namespace 'non-existing-namespace' does not exist. Please create the namespace first.",
                    response.getMessage());
        }
    }

    @Test
    void testFilterWithExceptionInMainFlow() throws NacosException {
        NamespaceValidationConfig mockConfig = Mockito.mock(NamespaceValidationConfig.class);
        when(mockConfig.isNamespaceValidationEnabled()).thenReturn(true);

        try (
                MockedStatic<NamespaceValidationConfig> mockedStatic = mockStatic(NamespaceValidationConfig.class);
                MockedStatic<ExtractorManager> extractorManagerMock = mockStatic(ExtractorManager.class)
        ) {

            mockedStatic.when(NamespaceValidationConfig::getInstance).thenReturn(mockConfig);
            extractorManagerMock.when(() -> ExtractorManager.getRpcExtractor(any())).thenThrow(new RuntimeException("Extractor error"));

            Response response = namespaceValidationFilter.filter(request, requestMeta, MockWithEnabledValidation.class);

            // When exception occurs in main flow, should return null (caught by try-catch)
            assertNull(response);
        }
    }

    static class MockWithoutNamespaceValidationAnnotation extends RequestHandler<Request, Response> {
        @Override
        @ExtractorManager.Extractor(rpcExtractor = InstanceRequestParamExtractor.class)
        public Response handle(Request request, RequestMeta meta) throws NacosException {
            return new Response() {
            };
        }
    }

    static class MockWithDisabledValidation extends RequestHandler<InstanceRequest, InstanceResponse> {

        @Override
        @NamespaceValidation(enable = false)
        @ExtractorManager.Extractor(rpcExtractor = InstanceRequestParamExtractor.class)
        public InstanceResponse handle(InstanceRequest request, RequestMeta meta) throws NacosException {
            return new InstanceResponse();
        }
    }

    static class MockWithEnabledValidationButNoExtractor extends RequestHandler<InstanceRequest, InstanceResponse> {

        @Override
        @NamespaceValidation
        public InstanceResponse handle(InstanceRequest request, RequestMeta meta) throws NacosException {
            return new InstanceResponse();
        }
    }

    static class MockWithEnabledValidation extends RequestHandler<InstanceRequest, InstanceResponse> {

        @Override
        @NamespaceValidation
        @ExtractorManager.Extractor(rpcExtractor = InstanceRequestParamExtractor.class)
        public InstanceResponse handle(InstanceRequest request, RequestMeta meta) throws NacosException {
            return new InstanceResponse();
        }
    }

}