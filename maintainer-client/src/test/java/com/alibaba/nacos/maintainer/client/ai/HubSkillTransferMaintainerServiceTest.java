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

import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Transfer SDK parameter typing, backward compatibility and controlled failures. */
class HubSkillTransferMaintainerServiceTest {
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    private final SkillMaintainerServiceImpl service =
        new SkillMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    
    @Test
    void hubImportUsesPostAndPreservesLargeEntryId() throws Exception {
        HubSkillTransferResult result = new HubSkillTransferResult();
        result.setTargetResourceId(9007199254740993L);
        respond(JacksonUtils.toJson(Result.success(result)));
        assertEquals(9007199254740993L, service.importHubSkill(request()).getTargetResourceId());
        HttpRequest http = capture();
        assertEquals("POST", http.getHttpMethod());
        assertEquals("/v3/admin/ai/hub/skills/import", http.getPath());
        assertEquals("9007199254740993", http.getParamValues().get("hubEntryId"));
        assertEquals("new-skill", http.getParamValues().get("targetName"));
    }
    
    @Test
    void directCopyTransportsConcreteSourceWithoutHubId() throws Exception {
        respond(JacksonUtils.toJson(Result.success(new HubSkillTransferResult())));
        HubSkillTransferRequest request = request();
        request.setHubEntryId(null);
        request.setSourceNamespaceId("source-ns");
        request.setSourceName("original");
        request.setSourceVersion("1.0.0");
        service.copyHubSkill(request);
        HttpRequest http = capture();
        assertEquals("/v3/admin/ai/hub/skills/copy", http.getPath());
        assertEquals("1.0.0", http.getParamValues().get("sourceVersion"));
        assertEquals(null, http.getParamValues().get("hubEntryId"));
    }
    
    @Test
    void invalidArgumentsCannotReachTransport() {
        assertThrows(NacosException.class, () -> service.importHubSkill(null));
        HubSkillTransferRequest request = request();
        request.setSourceName("");
        assertThrows(NacosException.class, () -> service.importHubSkill(request));
        request.setSourceName(null);
        request.setTargetVersion("latest");
        assertThrows(NacosException.class, () -> service.importHubSkill(request));
        verifyNoInteractions(proxy);
    }
    
    @Test
    void malformedOrMissingDataProducesControlled500() throws Exception {
        for (String body : new String[] {"not-json", "{}", "null", "{\"code\":0,\"data\":null}"}) {
            respond(body);
            NacosException error =
                assertThrows(NacosException.class, () -> service.importHubSkill(request()));
            assertEquals(500, error.getErrCode());
            assertEquals("Invalid Hub transfer response", error.getErrMsg());
        }
    }
    
    @Test
    void transportDenialIsPreserved() throws Exception {
        NacosException error = new NacosException(403, "denied");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenThrow(error);
        assertSame(error,
            assertThrows(NacosException.class, () -> service.importHubSkill(request())));
    }
    
    @Test
    void oldImplementationsRetainControlledUnsupportedDefaults() {
        SkillMaintainerService legacy = mock(SkillMaintainerService.class, CALLS_REAL_METHODS);
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.importHubSkill(null)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.copyHubSkill(null)).getErrCode());
    }
    
    private HubSkillTransferRequest request() {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setHubEntryId(9007199254740993L);
        request.setTargetNamespaceId("target-ns");
        request.setTargetName("new-skill");
        return request;
    }
    
    private void respond(String body) throws Exception {
        HttpRestResult<String> response = new HttpRestResult<>();
        response.setData(body);
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(response);
    }
    
    private HttpRequest capture() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(proxy).executeSyncHttpRequest(captor.capture());
        return captor.getValue();
    }
}
