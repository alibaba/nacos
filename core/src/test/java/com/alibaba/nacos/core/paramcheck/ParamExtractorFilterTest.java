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

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
    public void testFilter() throws NoSuchMethodException, ServletException, IOException {
        try (MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class)) {
            mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenAnswer((k) -> k.getArgument(2));
            filter = new ParamCheckerFilter(methodsCache);
            
            final Method check = ParamExtractorTest.Controller.class.getMethod("testCheck");
            AbstractHttpParamExtractor httpExtractor = ExtractorManager.getHttpExtractor(
                    check.getAnnotation(ExtractorManager.Extractor.class));
            assertEquals(httpExtractor.getClass(), ParamExtractorTest.TestHttpChecker.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addParameter("dataId", "123456789");
            Mockito.when(methodsCache.getMethod(request)).thenReturn(check);
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            });
            
            assertEquals(response.getStatus(), 200);
            request.setParameter("dataId", "123456789*");
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            });
            assertEquals(response.getStatus(), 400);
        }
    }
}
