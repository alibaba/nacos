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

package com.alibaba.nacos.naming.paramcheck;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.ParamCheckerFilter;
import com.alibaba.nacos.naming.controllers.InstanceController;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * Param Naming ExtractorTest.
 *
 * @author 985492783@qq.com
 * @date 2023/11/9 17:18
 */
@RunWith(MockitoJUnitRunner.class)
public class ParamExtractorTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private ParamCheckerFilter filter;
    
    @Test
    public void testBatchUpdateFilter() throws Exception {
        AbstractHttpParamExtractor httpExtractor = testExtractor(methodsCache, InstanceController.class,
                "batchUpdateInstanceMetadata", HttpServletRequest.class);
        assertEquals(httpExtractor.getClass(), NamingInstanceMetadataBatchHttpParamExtractor.class);
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
    }
    
    @Test
    public void testListFilter() throws Exception {
        AbstractHttpParamExtractor httpExtractor = testExtractor(methodsCache, InstanceController.class, "list",
                HttpServletRequest.class);
        assertEquals(httpExtractor.getClass(), NamingInstanceListHttpParamExtractor.class);
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
    }
    
    @Test
    public void testMetadataFilter() throws Exception {
        AbstractHttpParamExtractor httpExtractor = testExtractor(methodsCache, InstanceController.class,
                "batchDeleteInstanceMetadata", HttpServletRequest.class);
        assertEquals(httpExtractor.getClass(), NamingInstanceMetadataBatchHttpParamExtractor.class);
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
    }
    
    @Test
    public void testDefaultFilter() throws Exception {
        AbstractHttpParamExtractor httpExtractor = testExtractor(methodsCache, InstanceController.class, "register",
                HttpServletRequest.class);
        assertEquals(httpExtractor.getClass(), NamingDefaultHttpParamExtractor.class);
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
    }
    
    /**
     * Create mock method about AbstractHttpParamExtractor to verify.
     * @param methodsCache methodsCache
     * @param clazz clazz
     * @param methodName methodName
     * @param parameterTypes parameterTypes
     * @return AbstractHttpParamExtractor
     */
    public AbstractHttpParamExtractor testExtractor(ControllerMethodsCache methodsCache, Class<?> clazz,
            String methodName, Class<?>... parameterTypes) throws NoSuchMethodException, ServletException, IOException {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        final Method check = clazz.getMethod(methodName, parameterTypes);
        ExtractorManager.Extractor annotation = check.getAnnotation(ExtractorManager.Extractor.class);
        if (annotation == null) {
            annotation = clazz.getAnnotation(ExtractorManager.Extractor.class);
        }
        AbstractHttpParamExtractor httpExtractor = Mockito.spy(ExtractorManager.getHttpExtractor(annotation));
        
        MockedStatic<ExtractorManager> managerMockedStatic = Mockito.mockStatic(ExtractorManager.class);
        mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer((k) -> k.getArgument(2));
        ParamCheckerFilter filter = new ParamCheckerFilter(methodsCache);
        
        ExtractorManager.Extractor finalAnnotation = annotation;
        managerMockedStatic.when(() -> ExtractorManager.getHttpExtractor(finalAnnotation)).thenReturn(httpExtractor);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Mockito.when(methodsCache.getMethod(request)).thenReturn(check);
        
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });
        managerMockedStatic.close();
        mockedStatic.close();
        return httpExtractor;
    }
}
