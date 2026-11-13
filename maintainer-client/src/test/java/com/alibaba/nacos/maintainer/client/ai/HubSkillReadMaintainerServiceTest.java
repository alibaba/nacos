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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Shared reads reuse the ordinary namespace-aware SDK and Admin routes. */
class HubSkillReadMaintainerServiceTest {
    
    private static final String SHARED_NAMESPACE = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String ENTRY_ID = "9007199254740993";
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    
    private final SkillMaintainerServiceImpl service =
        new SkillMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    
    @Test
    void listUsesOrdinaryRouteAndPreservesSourceAwareSummary() throws Exception {
        Page<SkillSummary> page = new Page<>();
        SkillSummary summary = new SkillSummary();
        summary.setName(ENTRY_ID);
        summary.setHubEntryId(Long.valueOf(ENTRY_ID));
        summary.setDiscoverySource("HUB");
        summary.setDisplayName("original-name");
        summary.setDownloadCount(7L);
        page.setPageItems(Collections.singletonList(summary));
        respond(Result.success(page));
        SkillSummary result = service.listSkills(SHARED_NAMESPACE, "original", "blur", 1, 20)
            .getPageItems().get(0);
        assertEquals(Long.valueOf(ENTRY_ID), result.getHubEntryId());
        assertEquals("HUB", result.getDiscoverySource());
        assertEquals(7L, result.getDownloadCount());
        HttpRequest request = capture(false);
        assertEquals("/v3/admin/ai/skills/list", request.getPath());
        assertEquals(SHARED_NAMESPACE, request.getParamValues().get("namespaceId"));
        assertEquals("original", request.getParamValues().get("skillName"));
    }
    
    @Test
    void metadataUsesOrdinaryRouteWithOpaqueEntryName() throws Exception {
        SkillMeta meta = new SkillMeta();
        meta.setHubEntryId(Long.valueOf(ENTRY_ID));
        meta.setName(ENTRY_ID);
        meta.setVersion("v2");
        respond(Result.success(meta));
        assertEquals(meta.getHubEntryId(),
            service.getSkillMeta(SHARED_NAMESPACE, ENTRY_ID).getHubEntryId());
        HttpRequest request = capture(false);
        assertEquals("/v3/admin/ai/skills", request.getPath());
        assertEquals(SHARED_NAMESPACE, request.getParamValues().get("namespaceId"));
        assertEquals(ENTRY_ID, request.getParamValues().get("skillName"));
    }
    
    @Test
    void versionContentUsesOrdinaryTypedSkillRoute() throws Exception {
        Skill content = new Skill();
        content.setSkillMd("# pinned");
        respond(Result.success(content));
        assertEquals("# pinned",
            service.getSkillVersionDetail(SHARED_NAMESPACE, ENTRY_ID, "v2").getSkillMd());
        HttpRequest request = capture(false);
        assertEquals("/v3/admin/ai/skills/version", request.getPath());
        assertEquals(ENTRY_ID, request.getParamValues().get("skillName"));
        assertEquals("v2", request.getParamValues().get("version"));
    }
    
    @Test
    void downloadPreservesRawBytesAndResolvedIdentityOnOrdinaryRoute() throws Exception {
        byte[] bytes = new byte[] {0, (byte) 255};
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(downloadResponse(bytes, ENTRY_ID, "v2"));
        SkillDownload result = service.downloadSkillVersion(SHARED_NAMESPACE, ENTRY_ID, "v2");
        assertArrayEquals(bytes, result.getZipBytes());
        assertEquals("find-skills", result.getDisplayName());
        assertEquals("v2", result.getResolvedVersion());
        assertEquals(Long.valueOf(ENTRY_ID), result.getHubEntryId());
        HttpRequest request = capture(true);
        assertEquals("/v3/admin/ai/skills/version/download", request.getPath());
        assertEquals(SHARED_NAMESPACE, request.getParamValues().get("namespaceId"));
        assertEquals(ENTRY_ID, request.getParamValues().get("skillName"));
    }
    
    @Test
    void downloadsRejectMismatchedSharedResponseIdentity() throws Exception {
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(downloadResponse(new byte[] {1}, "17", "v2"));
        assertEquals(500, assertThrows(NacosException.class,
            () -> service.downloadSkillVersion(SHARED_NAMESPACE, ENTRY_ID, "v2")).getErrCode());
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(downloadResponse(new byte[] {1}, ENTRY_ID, "v3"));
        assertEquals(500, assertThrows(NacosException.class,
            () -> service.downloadSkillVersion(SHARED_NAMESPACE, ENTRY_ID, "v2")).getErrCode());
    }
    
    @Test
    void ordinaryDownloadSupportsExistingResponsesWithoutSharedHeaders() throws Exception {
        HttpRestResult<byte[]> response = new HttpRestResult<>();
        response.setData(new byte[] {1});
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(response);
        SkillDownload download = service.downloadSkillVersion("team", "skill", "v1");
        assertEquals("skill", download.getDisplayName());
        assertEquals("v1", download.getResolvedVersion());
        assertArrayEquals(response.getData(), download.getZipBytes());
    }
    
    @Test
    void downloadPrefersEncodedFilenameAndPreservesLiteralPlus() throws Exception {
        Header header = Header.newInstance();
        header.addParam("Content-Disposition", "attachment; filename=legacy.zip; "
            + "filename*=UTF-8''%E5%85%B1%E4%BA%AB+skill.zip");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(new HttpRestResult<>(header, 200, new byte[] {1}, "OK"));
        assertEquals("共享+skill", service.downloadSkillVersion("team", "skill", "v1")
            .getDisplayName());
    }
    
    @Test
    void malformedEncodedFilenameFallsBackToQuotedLegacyFilename() throws Exception {
        Header header = Header.newInstance();
        header.addParam("Content-Disposition", "attachment; filename=\"source skill.zip\"; "
            + "filename*=UTF-8''%invalid");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenReturn(new HttpRestResult<>(header, 200, new byte[] {1}, "OK"));
        assertEquals("source skill", service.downloadSkillVersion("team", "skill", "v1")
            .getDisplayName());
    }
    
    @Test
    void invalidDownloadAndLegacyImplementationReturnControlledCodes() {
        assertEquals(400, assertThrows(NacosException.class,
            () -> service.downloadSkillVersion(SHARED_NAMESPACE, "", "v2")).getErrCode());
        verifyNoInteractions(proxy);
        SkillMaintainerService legacy = mock(SkillMaintainerService.class, CALLS_REAL_METHODS);
        assertEquals(501, assertThrows(NacosException.class,
            () -> legacy.downloadSkillVersion(SHARED_NAMESPACE, ENTRY_ID, "v2")).getErrCode());
    }
    
    @Test
    void transportDenialIsPreserved() throws Exception {
        NacosException denied = new NacosException(403, "denied");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class), eq(byte[].class)))
            .thenThrow(denied);
        assertSame(denied, assertThrows(NacosException.class,
            () -> service.downloadSkillVersion(SHARED_NAMESPACE, ENTRY_ID, "v2")));
    }
    
    private HttpRestResult<byte[]> downloadResponse(byte[] bytes, String entryId, String version) {
        Header header = Header.newInstance();
        header.addParam("Content-Disposition", "attachment;filename=find-skills.zip");
        header.addParam("X-Nacos-Hub-Entry-Id", entryId);
        header.addParam("X-Nacos-Skill-Resolved-Version", version);
        return new HttpRestResult<>(header, 200, bytes, "OK");
    }
    
    private void respond(Object value) throws Exception {
        HttpRestResult<String> result = new HttpRestResult<>();
        result.setData(JacksonUtils.toJson(value));
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(result);
    }
    
    private HttpRequest capture(boolean binary) throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        if (binary) {
            verify(proxy).executeSyncHttpRequest(captor.capture(), eq(byte[].class));
        } else {
            verify(proxy).executeSyncHttpRequest(captor.capture());
        }
        return captor.getValue();
    }
}
