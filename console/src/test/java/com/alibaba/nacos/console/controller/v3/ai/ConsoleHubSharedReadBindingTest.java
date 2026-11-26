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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleHubSharedReadBindingTest {
    
    static Stream<Class<?>> controllers() {
        return Stream.of(ConsoleMcpController.class, ConsoleAgentController.class,
            ConsolePromptController.class, ConsoleAgentSpecController.class);
    }
    
    @ParameterizedTest
    @MethodSource("controllers")
    void sharedParamsSelectIdentityOnlyMethodWithoutWeakeningOrdinaryReads(Class<?> controller)
        throws Exception {
        int bindings = 0;
        for (Method shared : controller.getDeclaredMethods()) {
            if (!shared.getName().endsWith("Shared")) {
                continue;
            }
            GetMapping mapping = shared.getAnnotation(GetMapping.class);
            if (mapping == null) {
                continue;
            }
            bindings++;
            Secured secured = shared.getAnnotation(Secured.class);
            assertEquals(SignType.SPECIFIED, secured.signType());
            assertEquals(AiConstants.Hub.ENTRY_RESOURCE, secured.resource());
            assertTrue(Arrays.asList(secured.tags()).contains(Constants.Tag.ONLY_IDENTITY));
            Method ordinary =
                controller.getDeclaredMethod(shared.getName().replaceFirst("Shared$", ""),
                    shared.getParameterTypes());
            assertFalse(Arrays.asList(ordinary.getAnnotation(Secured.class).tags())
                .contains(Constants.Tag.ONLY_IDENTITY));
            String path = mapping.value().length == 0 ? "/probe" : "/probe" + mapping.value()[0];
            RequestMappingInfo regular =
                RequestMappingInfo.paths(path).methods(RequestMethod.GET).build();
            RequestMappingInfo specific = RequestMappingInfo.paths(path).methods(RequestMethod.GET)
                .params(mapping.params()).build();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setParameter("namespaceId", AiConstants.Hub.NAMESPACE_ID);
            ServletRequestPathUtils.parseAndCache(request);
            assertNotNull(specific.getMatchingCondition(request));
            assertTrue(specific.compareTo(regular, request) < 0);
            request.setParameter("namespaceId", "ordinary-source");
            assertNull(specific.getMatchingCondition(request));
            assertNotNull(regular.getMatchingCondition(request));
        }
        assertTrue(bindings > 0);
    }
    
    @ParameterizedTest
    @MethodSource("controllers")
    void conflictingNamespaceStopsBeforeDelegation(Class<?> controller) throws Exception {
        Object instance = mock(controller, CALLS_REAL_METHODS);
        for (Method shared : controller.getDeclaredMethods()) {
            if (!shared.getName().endsWith("Shared")
                || shared.getAnnotation(GetMapping.class) == null) {
                continue;
            }
            for (String namespace : new String[] {"ordinary-source",
                "__nacos_ai_hub__,ordinary-source",
                "ordinary-source,__nacos_ai_hub__"}) {
                Object[] arguments = new Object[shared.getParameterCount()];
                Object form = shared.getParameterTypes()[0].getDeclaredConstructor().newInstance();
                form.getClass().getMethod("setNamespaceId", String.class).invoke(form, namespace);
                arguments[0] = form;
                InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> shared.invoke(instance, arguments));
                assertTrue(failure.getCause() instanceof NacosApiException, shared.getName());
                NacosApiException error = (NacosApiException) failure.getCause();
                assertEquals(400, error.getErrCode());
                assertEquals(20002, error.getDetailErrCode());
            }
        }
    }
}
