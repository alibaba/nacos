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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SDK Hub read typing, exact IDs, tag transport and controlled errors. */
class HubSkillReadMaintainerServiceTest {
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    private final SkillMaintainerServiceImpl service =
        new SkillMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    @Test
    void listTransportsTagsWithoutLossAndDeserializesTypedSummaries() throws Exception {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setTagsAll(Arrays.asList("search,tools", null, "coding"));
        Page<HubSkillSummary> page = new Page<>();
        HubSkillSummary summary = new HubSkillSummary();
        summary.setHubEntryId(9007199254740993L);
        summary.setDisplayName("original-name");
        page.setPageItems(Arrays.asList(summary));
        respond(Result.success(page));
        assertEquals(9007199254740993L,
            service.listHubSkills(request).getPageItems().get(0).getHubEntryId());
        HttpRequest http = capture();
        assertEquals("GET", http.getHttpMethod());
        assertEquals("/v3/admin/ai/hub/skills/list", http.getPath());
        assertEquals("search,tools", http.getParamValues().get("tagsAll[0]"));
        assertEquals("coding", http.getParamValues().get("tagsAll[2]"));
    }
    
    @Test
    void detailAndContentKeepLargeIdsAndSafeModels() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        detail.setHubEntryId(9007199254740993L);
        respond(Result.success(detail));
        assertEquals(detail.getHubEntryId(),
            service.getHubSkillDetail(detail.getHubEntryId()).getHubEntryId());
        assertEquals("9007199254740993", capture().getParamValues().get("hubEntryId"));
        HubSkillContent content = new HubSkillContent();
        content.setSkillMd("# pinned");
        respond(Result.success(content));
        assertEquals("# pinned", service.getHubSkillContent(1L).getSkillMd());
    }
    
    @Test
    void invalidLocalArgumentsNeverContactTransport() {
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.listHubSkills(null)).getErrCode());
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.getHubSkillDetail(null)).getErrCode());
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.getHubSkillContent(-1L)).getErrCode());
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery(new String(new char[1025]).replace('\0', 'x'));
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.listHubSkills(request)).getErrCode());
        verifyNoInteractions(proxy);
    }
    
    @Test
    void malformedReadResponsesProduceControlled500() throws Exception {
        HttpRestResult<String> response = new HttpRestResult<>();
        response.setData("not-json");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(response);
        assertEquals(500, assertThrows(NacosException.class,
            () -> service.listHubSkills(new HubSkillListRequest())).getErrCode());
        assertEquals(500,
            assertThrows(NacosException.class, () -> service.getHubSkillDetail(1L)).getErrCode());
        assertEquals(500,
            assertThrows(NacosException.class, () -> service.getHubSkillContent(1L)).getErrCode());
    }
    
    @Test
    void transportDenialIsPreserved() throws Exception {
        NacosException denied = new NacosException(403, "denied");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenThrow(denied);
        assertSame(denied,
            assertThrows(NacosException.class, () -> service.getHubSkillContent(1L)));
    }
    
    @Test
    void existingImplementationsKeepControlledDefaults() {
        SkillMaintainerService legacy = mock(SkillMaintainerService.class, CALLS_REAL_METHODS);
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.listHubSkills(null)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.getHubSkillDetail(1L)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.getHubSkillContent(1L)).getErrCode());
    }
    
    private void respond(Object value) throws Exception {
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setData(JacksonUtils.toJson(value));
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(result);
    }
    
    private HttpRequest capture() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(proxy).executeSyncHttpRequest(captor.capture());
        return captor.getValue();
    }
    
    @Test
    void downloadPreservesBinaryBytesAndValidatesResponseIdentity() throws Exception {
        HubSkillDownload download = new HubSkillDownload();
        download.setHubEntryId(9007199254740993L);
        download.setDisplayName("find-skills");
        download.setSharedVersion("1.0.0");
        download.setZipBytes(new byte[] {0, (byte) 255});
        respond(Result.success(download));
        org.junit.jupiter.api.Assertions.assertArrayEquals(download.getZipBytes(),
            service.getHubSkillDownload(download.getHubEntryId()).getZipBytes());
        assertEquals("/v3/admin/ai/hub/skills/download", capture().getPath());
        assertEquals(500, assertThrows(NacosException.class,
            () -> service.getHubSkillDownload(1L)).getErrCode());
    }
    
    @Test
    void downloadArgumentsAndLegacyImplementationFailWithControlledCodes() {
        assertEquals(400, assertThrows(NacosException.class,
            () -> service.getHubSkillDownload(null)).getErrCode());
        verifyNoInteractions(proxy);
        SkillMaintainerService legacy = mock(SkillMaintainerService.class, CALLS_REAL_METHODS);
        assertEquals(501, assertThrows(NacosException.class,
            () -> legacy.getHubSkillDownload(1L)).getErrCode());
    }
    
}
