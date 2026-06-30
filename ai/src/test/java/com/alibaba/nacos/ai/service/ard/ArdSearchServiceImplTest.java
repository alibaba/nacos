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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchQuery;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdSearchServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdSearchServiceImplTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpServerOperationService mcpServerOperationService;
    
    @Test
    void searchShouldReturnOnlineLatestSkill() throws Exception {
        ArdSearchServiceImpl service =
            new ArdSearchServiceImpl(resourceManager, mcpServerOperationService);
        QueryCondition condition = new QueryCondition();
        when(resourceManager.buildQueryCondition(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq(null), eq(null),
            eq(VisibilityConstants.ACTION_READ)))
            .thenReturn(condition);
        when(resourceManager.listMeta(condition, 1, 200)).thenReturn(pageOf(meta("api-helper")));
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(onlineVersion());
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdSearchServiceImpl.MEDIA_TYPE_SKILL))));
        
        assertEquals(1, response.getResults().size());
        ArdSearchResult result = response.getResults().get(0);
        assertEquals("api-helper", result.getDisplayName());
        assertEquals(ArdSearchServiceImpl.MEDIA_TYPE_SKILL, result.getType());
        assertEquals("nacos://public/skill/api-helper/1.0.0", result.getUrl());
        assertEquals("urn:air:nacos.local:public:skill:api-helper", result.getIdentifier());
        assertEquals("nacos-local", result.getSource());
        assertEquals("skill", result.getMetadata().get("resourceType"));
        assertTrue(response.getReferrals().isEmpty());
        verify(mcpServerOperationService, never())
            .listMcpServerWithPage(eq("public"), eq("api"), eq(Constants.MCP_LIST_SEARCH_BLUR),
                eq(1), eq(200));
    }
    
    @Test
    void searchShouldSkipNonOnlineLatestVersion() throws Exception {
        ArdSearchServiceImpl service =
            new ArdSearchServiceImpl(resourceManager, mcpServerOperationService);
        QueryCondition condition = new QueryCondition();
        when(resourceManager.buildQueryCondition(eq("public"),
            eq(Constants.Skills.RESOURCE_TYPE_SKILL), eq(null), eq(null),
            eq(VisibilityConstants.ACTION_READ)))
            .thenReturn(condition);
        when(resourceManager.listMeta(condition, 1, 200)).thenReturn(pageOf(meta("api-helper")));
        AiResourceVersion draft = onlineVersion();
        draft.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        when(resourceManager.findVersion("public", "api-helper",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(draft);
        
        ArdSearchResponse response = service.search(request("api",
            Map.of("type", (Object) List.of(ArdSearchServiceImpl.MEDIA_TYPE_SKILL))));
        
        assertTrue(response.getResults().isEmpty());
    }
    
    @Test
    void searchShouldRejectUnsupportedFederation() {
        ArdSearchServiceImpl service =
            new ArdSearchServiceImpl(resourceManager, mcpServerOperationService);
        ArdSearchRequest request = request("api",
            Map.of("type", (Object) List.of(ArdSearchServiceImpl.MEDIA_TYPE_SKILL)));
        request.setFederation("referrals");
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    @Test
    void searchShouldRejectUnknownFilterKey() {
        ArdSearchServiceImpl service =
            new ArdSearchServiceImpl(resourceManager, mcpServerOperationService);
        ArdSearchRequest request = request("api",
            Map.of("metadata.unknown", (Object) List.of("x")));
        
        assertThrows(NacosException.class, () -> service.search(request));
    }
    
    private ArdSearchRequest request(String text, Map<String, Object> filter) {
        ArdSearchQuery query = new ArdSearchQuery();
        query.setText(text);
        query.setFilter(filter);
        ArdSearchRequest request = new ArdSearchRequest();
        request.setNamespaceId("public");
        request.setQuery(query);
        request.setFederation("none");
        request.setPageSize(10);
        return request;
    }
    
    private Page<AiResource> pageOf(AiResource resource) {
        Page<AiResource> page = new Page<>();
        page.setPageItems(Collections.singletonList(resource));
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        return page;
    }
    
    private AiResource meta(String name) {
        AiResource meta = new AiResource();
        meta.setNamespaceId("public");
        meta.setName(name);
        meta.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setDesc("Generate API parameter tables");
        meta.setBizTags("[\"documentation\",\"api\"]");
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setVersionInfo("{\"labels\":{\"latest\":\"1.0.0\"}}");
        meta.setGmtModified(Timestamp.from(Instant.parse("2026-06-29T00:00:00Z")));
        return meta;
    }
    
    private AiResourceVersion onlineVersion() {
        AiResourceVersion version = new AiResourceVersion();
        version.setNamespaceId("public");
        version.setName("api-helper");
        version.setType(Constants.Skills.RESOURCE_TYPE_SKILL);
        version.setVersion("1.0.0");
        version.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        version.setDesc("Extract API parameters and generate Markdown tables");
        version.setGmtModified(Timestamp.from(Instant.parse("2026-06-29T01:00:00Z")));
        return version;
    }
}
