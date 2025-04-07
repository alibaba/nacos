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

package com.alibaba.nacos.console.paramcheck;

import com.alibaba.nacos.console.controller.NamespaceController;
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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Param Console ExtractorTest.
 *
 * @author 985492783@qq.com
 * @date 2023/11/9 17:07
 */
@ExtendWith(MockitoExtension.class)
class ParamExtractorTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private ParamCheckerFilter filter;
    
    @Test
    void testDefaultFilter() throws Exception {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        final Method check = NamespaceController.class.getMethod("getNamespaces");
        ExtractorManager.Extractor annotation = NamespaceController.class.getAnnotation(ExtractorManager.Extractor.class);
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
        assertEquals(ConsoleDefaultHttpParamExtractor.class, httpExtractor.getClass());
        Mockito.verify(httpExtractor, new Times(1)).extractParam(Mockito.any());
        managerMockedStatic.close();
        mockedStatic.close();
    }
    
}
