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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.controller.HubSkillAdminController;
import com.alibaba.nacos.ai.controller.HubSkillStateAdminController;
import com.alibaba.nacos.ai.controller.HubSkillTransferAdminController;
import com.alibaba.nacos.ai.controller.SkillAdminController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class HubSkillReadRouteRegistrationTest {
    
    static Stream<Arguments> audiences() {
        return Stream.of(
            Arguments.of(Constants.Hub.ADMIN_PATH,
                new Object[] {mock(HubSkillAdminController.class),
                    mock(HubSkillStateAdminController.class),
                    mock(HubSkillTransferAdminController.class),
                    mock(SkillAdminController.class)}),
            Arguments.of(Constants.Hub.CONSOLE_PATH,
                new Object[] {mock(ConsoleHubSkillController.class),
                    mock(ConsoleHubSkillStateController.class),
                    mock(ConsoleHubSkillTransferController.class),
                    mock(ConsoleSkillController.class)}));
    }
    
    @ParameterizedTest
    @MethodSource("audiences")
    void removedDedicatedReadRoutesAreNotRegistered(String hubPath, Object[] controllers)
        throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controllers).build();
        RequestMappingHandlerMapping mappings =
            mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerMapping.class);
        assertNotNull(mappings.getHandler(request("POST", hubPath + "/share")));
        for (String suffix : new String[] {"list", "detail", "content", "download"}) {
            String path = hubPath + "/" + suffix;
            assertNull(mappings.getHandler(request("GET", path)), path);
        }
    }
    
    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addParameter("hubEntryId", "1");
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }
}
