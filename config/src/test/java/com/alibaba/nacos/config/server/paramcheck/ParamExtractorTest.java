/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.paramcheck;

import com.alibaba.nacos.config.server.controller.ConfigController;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.ParamCheckerFilter;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Param Config ExtractorTest.
 *
 * @author 985492783@qq.com
 * @date 2023/11/9 16:04
 */
@ExtendWith(MockitoExtension.class)
class ParamExtractorTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private ParamCheckerFilter filter;
    
    @Test
    void testBlurFilter() throws Exception {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        final Method check = ConfigController.class.getMethod("fuzzySearchConfig", String.class, String.class, String.class, String.class,
                String.class, int.class, int.class);
        ExtractorManager.Extractor annotation = check.getAnnotation(ExtractorManager.Extractor.class);
        AbstractHttpParamExtractor httpExtractor = Mockito.spy(ExtractorManager.getHttpExtractor(annotation));
        
        MockedStatic<ExtractorManager> managerMockedStatic = Mockito.mockStatic(ExtractorManager.class);
        mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer((k) -> k.getArgument(2));
        filter = new ParamCheckerFilter(methodsCache);
        
        managerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(annotation)).thenReturn(httpExtractor);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addParameter("dataId", "testDataId*");
        Mockito.when(methodsCache.getMethod(request)).thenReturn(check);
        
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });
        assertEquals(400, response.getStatus());
        
        response = new MockHttpServletResponse();
        request.addParameter("search", "blur");
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });
        assertEquals(200, response.getStatus());
        assertEquals(ConfigBlurSearchHttpParamExtractor.class, httpExtractor.getClass());
        Mockito.verify(httpExtractor, new Times(2)).extractParam(Mockito.any());
        managerMockedStatic.close();
        mockedStatic.close();
    }
    
    @Test
    void testListenerFilter() throws Exception {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        final Method check = ConfigController.class.getMethod("listener", HttpServletRequest.class, HttpServletResponse.class);
        ExtractorManager.Extractor annotation = check.getAnnotation(ExtractorManager.Extractor.class);
        AbstractHttpParamExtractor httpExtractor = Mockito.spy(ExtractorManager.getHttpExtractor(annotation));
        
        MockedStatic<ExtractorManager> managerMockedStatic = Mockito.mockStatic(ExtractorManager.class);
        mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer((k) -> k.getArgument(2));
        filter = new ParamCheckerFilter(methodsCache);
        
        managerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(annotation)).thenReturn(httpExtractor);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Mockito.when(methodsCache.getMethod(request)).thenReturn(check);
        
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });
        assertEquals(ConfigListenerHttpParamExtractor.class, httpExtractor.getClass());
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
        managerMockedStatic.close();
        mockedStatic.close();
    }
    
    @Test
    void testDefaultFilter() throws Exception {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        final Method check = ConfigController.class.getMethod("getConfigAdvanceInfo", String.class, String.class, String.class);
        ExtractorManager.Extractor annotation = ConfigController.class.getAnnotation(ExtractorManager.Extractor.class);
        AbstractHttpParamExtractor httpExtractor = Mockito.spy(ExtractorManager.getHttpExtractor(annotation));
        
        MockedStatic<ExtractorManager> managerMockedStatic = Mockito.mockStatic(ExtractorManager.class);
        mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer((k) -> k.getArgument(2));
        filter = new ParamCheckerFilter(methodsCache);
        
        managerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(annotation)).thenReturn(httpExtractor);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Mockito.when(methodsCache.getMethod(request)).thenReturn(check);
        
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });
        assertEquals(ConfigDefaultHttpParamExtractor.class, httpExtractor.getClass());
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
        managerMockedStatic.close();
        mockedStatic.close();
    }
}
