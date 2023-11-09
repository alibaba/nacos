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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
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
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * ParamCheckerFilterTest.
 *
 * @author 985492783@qq.com
 * @date 2023/11/7 20:29
 */
@RunWith(MockitoJUnitRunner.class)
public class ParamExtractorFilterTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private ParamCheckerFilter filter;
    
    @Test
    public void testFilter() throws NacosException, ServletException, IOException, NoSuchMethodException {
        AbstractHttpParamExtractor httpExtractor = testExtractor(methodsCache, ParamExtractorTest.Controller.class,
                "testCheck");
        assertEquals(httpExtractor.getClass(), ParamExtractorTest.TestHttpChecker.class);
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
    }
    
    /**
     * Create mock method about AbstractHttpParamExtractor to verify.
     *
     * @param methodsCache   methodsCache
     * @param clazz          clazz
     * @param methodName     methodName
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
