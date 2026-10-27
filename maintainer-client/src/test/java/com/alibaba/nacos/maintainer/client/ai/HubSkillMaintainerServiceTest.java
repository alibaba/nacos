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

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.constants.Constants;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Hub SDK form transport, response and source-compatibility tests.
 *
 * @author nacos
 */
class HubSkillMaintainerServiceTest {
    
    private final ClientHttpProxy proxy = mock(ClientHttpProxy.class);
    
    private final SkillMaintainerServiceImpl service =
        new SkillMaintainerServiceImpl(new AiMaintainerHttpContext(proxy));
    
    @Test
    void sharePostsExactSourceAndLargeIds() throws Exception {
        successfulResponse();
        HubShareSkillRequest request = shareRequest();
        request.setExpectedSourceResourceId(9007199254740993L);
        HubShareResult result = service.shareHubSkill(request);
        assertEquals(9007199254740993L, result.getHubEntryId());
        assertEquals(HubShareOutcome.CREATED, result.getOutcome());
        HttpRequest http = capture("/share");
        assertEquals("source-a", http.getResource().getNamespace());
        assertEquals("find-skills", http.getResource().getResource());
        assertEquals("source-a", http.getParamValues().get("sourceNamespaceId"));
        assertEquals("find-skills", http.getParamValues().get("sourceName"));
        assertEquals("1.0.0", http.getParamValues().get("sourceVersion"));
        assertEquals("9007199254740993", http.getParamValues().get("expectedSourceResourceId"));
    }
    
    @Test
    void optionalExpectedSourceIdIsNotEncodedAsNull() throws Exception {
        successfulResponse();
        service.shareHubSkill(shareRequest());
        assertFalse(capture("/share").getParamValues().containsKey("expectedSourceResourceId"));
    }
    
    @Test
    void adjustPostsExpectedMetaVersionAndTargetVersion() throws Exception {
        successfulResponse();
        HubAdjustShareRequest request = new HubAdjustShareRequest();
        request.setHubEntryId(91L);
        request.setExpectedMetaVersion(8L);
        request.setTargetVersion("2.0.0");
        service.adjustHubShare(request);
        HttpRequest http = capture("/adjust");
        assertEquals("91", http.getParamValues().get("hubEntryId"));
        assertEquals("8", http.getParamValues().get("expectedMetaVersion"));
        assertEquals("2.0.0", http.getParamValues().get("targetVersion"));
    }
    
    @Test
    void unsharePostsStrictCasParameters() throws Exception {
        successfulResponse();
        HubUnshareRequest request = new HubUnshareRequest();
        request.setHubEntryId(91L);
        request.setExpectedMetaVersion(8L);
        service.unshareHubSkill(request);
        assertEquals("8", capture("/unshare").getParamValues().get("expectedMetaVersion"));
    }
    
    @Test
    void forceUnsharePreservesReason() throws Exception {
        successfulResponse();
        HubForceUnshareRequest request = new HubForceUnshareRequest();
        request.setHubEntryId(91L);
        request.setExpectedMetaVersion(8L);
        request.setReason("policy violation: 空格 & +");
        service.forceUnshareHubSkill(request);
        assertEquals(request.getReason(), capture("/force-unshare").getParamValues().get("reason"));
    }
    
    @Test
    void nullRequestsFailWithoutHttpCalls() {
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.shareHubSkill(null)).getErrCode());
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.adjustHubShare(null)).getErrCode());
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.unshareHubSkill(null)).getErrCode());
        assertEquals(400,
            assertThrows(NacosException.class, () -> service.forceUnshareHubSkill(null))
                .getErrCode());
        verifyNoInteractions(proxy);
    }
    
    @Test
    void preExistingImplementationsHaveControlledDefaultMethods() {
        SkillMaintainerService legacy = mock(SkillMaintainerService.class, CALLS_REAL_METHODS);
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.shareHubSkill(null)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.adjustHubShare(null)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.unshareHubSkill(null)).getErrCode());
        assertEquals(501,
            assertThrows(NacosException.class, () -> legacy.forceUnshareHubSkill(null))
                .getErrCode());
    }
    
    @Test
    void allAddedMethodsDeclareTheirSupportedVersion() throws Exception {
        assertEquals("3.3.0", SkillMaintainerService.class.getMethod("shareHubSkill",
            HubShareSkillRequest.class).getAnnotation(Since.class).value());
        assertEquals("3.3.0", SkillMaintainerService.class.getMethod("adjustHubShare",
            HubAdjustShareRequest.class).getAnnotation(Since.class).value());
        assertEquals("3.3.0", SkillMaintainerService.class.getMethod("unshareHubSkill",
            HubUnshareRequest.class).getAnnotation(Since.class).value());
        assertEquals("3.3.0", SkillMaintainerService.class.getMethod("forceUnshareHubSkill",
            HubForceUnshareRequest.class).getAnnotation(Since.class).value());
    }
    
    @Test
    void transportConflictIsNotRemappedToServerError() throws Exception {
        NacosException conflict = new NacosException(409, "stale metadata");
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenThrow(conflict);
        assertSame(conflict,
            assertThrows(NacosException.class, () -> service.shareHubSkill(shareRequest())));
    }
    
    @Test
    void missingResponseDataIsAControlledServerError() throws Exception {
        response(JacksonUtils.toJson(Result.success(null)));
        assertEquals(500,
            assertThrows(NacosException.class, () -> service.shareHubSkill(shareRequest()))
                .getErrCode());
    }
    
    @Test
    void malformedResponseIsAControlledServerError() throws Exception {
        response("not-json");
        assertEquals(500,
            assertThrows(NacosException.class, () -> service.shareHubSkill(shareRequest()))
                .getErrCode());
    }
    
    @Test
    void unsuccessfulResultIsNotReturnedAsSuccess() throws Exception {
        response(JacksonUtils.toJson(Result.failure(ErrorCode.ACCESS_DENIED, "denied")));
        assertEquals(500,
            assertThrows(NacosException.class, () -> service.shareHubSkill(shareRequest()))
                .getErrCode());
    }
    
    private HubShareSkillRequest shareRequest() {
        HubShareSkillRequest request = new HubShareSkillRequest();
        request.setSourceNamespaceId("source-a");
        request.setSourceName("find-skills");
        request.setSourceVersion("1.0.0");
        return request;
    }
    
    private void successfulResponse() throws Exception {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(9007199254740993L);
        result.setOutcome(HubShareOutcome.CREATED);
        response(JacksonUtils.toJson(Result.success(result)));
    }
    
    private void response(String json) throws Exception {
        HttpRestResult<String> rest = new HttpRestResult<>();
        rest.setData(json);
        when(proxy.executeSyncHttpRequest(any(HttpRequest.class))).thenReturn(rest);
    }
    
    private HttpRequest capture(String suffix) throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(proxy).executeSyncHttpRequest(captor.capture());
        HttpRequest request = captor.getValue();
        assertEquals("POST", request.getHttpMethod());
        assertEquals(Constants.AdminApiPath.AI_HUB_SKILL_ADMIN_PATH + suffix, request.getPath());
        return request;
    }
}
