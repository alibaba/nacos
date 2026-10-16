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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class HubSkillStateMaintainerServiceTest {
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    private final SkillMaintainerServiceImpl service =
        new SkillMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    
    @Test
    void neverSharedSourceAcceptsOmittedNullDataAndPreservesIdentity() throws Exception {
        respond("{\"code\":0}");
        assertNull(service.getHubSkillSourceShare("ns-a", "find,skills"));
        HttpRequest request = capture();
        assertEquals("GET", request.getHttpMethod());
        assertEquals("/v3/admin/ai/hub/skills/source-share", request.getPath());
        assertEquals("ns-a", request.getParamValues().get("sourceNamespaceId"));
        assertEquals("find,skills", request.getParamValues().get("sourceName"));
    }
    
    @Test
    void governancePreservesFullJavaLongIdAndLockVersion() throws Exception {
        HubSkillShareState state = state(Long.MAX_VALUE);
        respond(JacksonUtils.toJson(Result.success(state)));
        HubSkillShareState actual = service.getHubSkillGovernanceState(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, actual.getHubEntryId());
        assertEquals(9007199254740993L, actual.getMetaVersion());
        HttpRequest request = capture();
        assertEquals("/v3/admin/ai/hub/skills/governance-state", request.getPath());
        assertEquals("9223372036854775807", request.getParamValues().get("hubEntryId"));
    }
    
    @Test
    void malformedGovernanceOrMissingDataReturnsControlled500() throws Exception {
        for (String json : new String[] {"not-json", "{\"code\":0}", "{\"code\":0,\"data\":{}}"}) {
            respond(json);
            assertEquals(500, assertThrows(NacosException.class,
                () -> service.getHubSkillGovernanceState(901L)).getErrCode());
        }
    }
    
    @Test
    void governanceRejectsStateForAnotherEntry() throws Exception {
        respond(JacksonUtils.toJson(Result.success(state(902L))));
        assertEquals(500, assertThrows(NacosException.class,
            () -> service.getHubSkillGovernanceState(901L)).getErrCode());
    }
    
    @Test
    void invalidLocalArgumentsDoNotContactTransport() {
        assertEquals(400, assertThrows(NacosException.class,
            () -> service.getHubSkillSourceShare("", "find-skills")).getErrCode());
        assertEquals(400, assertThrows(NacosException.class,
            () -> service.getHubSkillGovernanceState(0L)).getErrCode());
        verifyNoInteractions(proxy);
    }
    
    @Test
    void remoteDenialIsPreserved() throws Exception {
        NacosException denied = new NacosException(403, "denied");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenThrow(denied);
        assertSame(denied, assertThrows(NacosException.class,
            () -> service.getHubSkillGovernanceState(901L)));
    }
    
    @Test
    void legacyImplementationsRetainControlledDefaultMethods() {
        SkillMaintainerService legacy =
            mock(SkillMaintainerService.class, org.mockito.Answers.CALLS_REAL_METHODS);
        assertEquals(501, assertThrows(NacosException.class,
            () -> legacy.getHubSkillSourceShare("ns-a", "find-skills")).getErrCode());
        assertEquals(501, assertThrows(NacosException.class,
            () -> legacy.getHubSkillGovernanceState(901L)).getErrCode());
    }
    
    private HubSkillShareState state(long id) {
        HubSkillShareState state = new HubSkillShareState();
        state.setHubEntryId(id);
        state.setMetaVersion(9007199254740993L);
        state.setSharedVersion("1.0.0");
        state.setActive(true);
        return state;
    }
    
    private void respond(String json) throws Exception {
        HttpRestResult<String> response = new HttpRestResult<>();
        response.setData(json);
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(response);
    }
    
    private HttpRequest capture() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(proxy).executeSyncHttpRequest(captor.capture());
        return captor.getValue();
    }
}
