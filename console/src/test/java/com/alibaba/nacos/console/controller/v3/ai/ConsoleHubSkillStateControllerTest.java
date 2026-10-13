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

import com.alibaba.nacos.console.proxy.ai.HubSkillStateProxy;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsoleHubSkillStateControllerTest {
    
    private final HubSkillStateProxy proxy = mock(HubSkillStateProxy.class);
    private final MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new ConsoleHubSkillStateController(proxy))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    private static final String PATH = "/v3/console/ai/hub/skills";
    
    @Test
    void consoleUsesDeploymentProxyForSourceAndGovernance() throws Exception {
        mvc.perform(get(PATH + "/source-share").param("sourceNamespaceId", "ns-a")
            .param("sourceName", "find-skills")).andExpect(status().isOk());
        mvc.perform(get(PATH + "/governance-state").param("hubEntryId", "9223372036854775807"))
            .andExpect(status().isOk());
        verify(proxy).bySource("ns-a", "find-skills");
        verify(proxy).governanceState(Long.MAX_VALUE);
    }
    
    @Test
    void malformedOrOverflowingIdDoesNotReachRemoteProxy() throws Exception {
        for (String id : new String[] {"x", "0", "9223372036854775808"}) {
            mvc.perform(get(PATH + "/governance-state").param("hubEntryId", id))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(proxy);
    }
}
