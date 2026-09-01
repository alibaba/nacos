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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aCanonicalDefinitionConverter;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class A2aHistoricalDefinitionScannerTest {
    
    private static final String NAMESPACE_ID = "tenant-a";
    
    private static final String AGENT_NAME = "research-agent";
    
    private static final String SUMMARY_DATA_ID = "opaque-legacy-identifier";
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    private final Map<String, ConfigQueryChainResponse> source =
        new LinkedHashMap<String, ConfigQueryChainResponse>();
    
    private final List<ConfigQueryChainRequest> queries =
        new ArrayList<ConfigQueryChainRequest>();
    
    private ConfigInfo summaryConfig;
    
    private A2aHistoricalDefinitionScanner scanner;
    
    @BeforeEach
    void setUp() {
        scanner = new A2aHistoricalDefinitionScanner(configDetailService,
            configQueryChainService, new A2aCanonicalDefinitionConverter());
        summaryConfig = summaryConfig(summary("1.0.0", "1.0.0", "2.0.0"));
        page(summaryConfig, 201, 0);
        source.put(key(Constants.A2A.AGENT_GROUP, SUMMARY_DATA_ID),
            found(summaryConfig.getContent(), summaryConfig.getMd5()));
        source.put(key(Constants.A2A.AGENT_VERSION_GROUP, SUMMARY_DATA_ID + "-1.0.0"),
            found(JacksonUtils.toJson(card("1.0.0", "URL")), "version-md5-1"));
        source.put(key(Constants.A2A.AGENT_VERSION_GROUP, SUMMARY_DATA_ID + "-2.0.0"),
            found(JacksonUtils.toJson(card("2.0.0", "SERVICE")), "version-md5-2"));
        lenient().when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenAnswer(invocation -> {
                ConfigQueryChainRequest request = invocation.getArgument(0);
                queries.add(request);
                return source.getOrDefault(key(request.getGroup(), request.getDataId()), missing());
            });
    }
    
    @Test
    void shouldScanCompleteSnapshotUsingHistoricalCodecCoordinates() {
        Page<A2aHistoricalDefinitionSnapshot> result = scanner.scanPage(NAMESPACE_ID, 1, 100);
        
        assertEquals(201, result.getTotalCount());
        assertEquals(3, result.getPagesAvailable());
        assertEquals(1, result.getPageItems().size());
        A2aHistoricalDefinitionSnapshot snapshot = result.getPageItems().get(0);
        assertEquals(AGENT_NAME, snapshot.getSummary().getName());
        assertEquals(summaryConfig.getContent(), snapshot.getSummaryContent());
        assertEquals(summaryConfig.getMd5(), snapshot.getSummaryMd5());
        assertEquals(Arrays.asList("1.0.0", "2.0.0"),
            new ArrayList<String>(snapshot.getVersions().keySet()));
        assertEquals(SUMMARY_DATA_ID + "-1.0.0",
            snapshot.getVersions().get("1.0.0").getDataId());
        assertEquals(JacksonUtils.toJson(card("1.0.0", "URL")),
            snapshot.getVersions().get("1.0.0").getContent());
        assertEquals(64, snapshot.getSourceFingerprint().length());
        assertTrue(scanner.isCurrent(snapshot));
        assertTrue(queries.stream().anyMatch(request -> (SUMMARY_DATA_ID + "-1.0.0")
            .equals(request.getDataId())));
        assertFalse(queries.stream().anyMatch(request -> request.getDataId().contains(AGENT_NAME)));
    }
    
    @Test
    void shouldPreserveReportedPageCount() {
        page(summaryConfig, 2, 2);
        assertEquals(2, scanner.scanPage(NAMESPACE_ID, 1, 100).getPagesAvailable());
    }
    
    @Test
    void sourceFenceShouldDetectSummaryVersionAndRemovalChanges() {
        A2aHistoricalDefinitionSnapshot snapshot = scanner.scanPage(NAMESPACE_ID, 1, 10)
            .getPageItems().get(0);
        source.put(key(Constants.A2A.AGENT_VERSION_GROUP, SUMMARY_DATA_ID + "-2.0.0"),
            found(JacksonUtils.toJson(card("2.0.0", "SERVICE")), "changed-md5"));
        assertFalse(scanner.isCurrent(snapshot));
        
        source.put(key(Constants.A2A.AGENT_VERSION_GROUP, SUMMARY_DATA_ID + "-2.0.0"),
            found(JacksonUtils.toJson(card("2.0.0", "SERVICE")), "version-md5-2"));
        AgentCardVersionInfo updated = summary("2.0.0", "1.0.0", "2.0.0");
        source.put(key(Constants.A2A.AGENT_GROUP, SUMMARY_DATA_ID),
            found(JacksonUtils.toJson(updated), "summary-md5-2"));
        assertFalse(scanner.isCurrent(snapshot));
        
        source.put(key(Constants.A2A.AGENT_GROUP, SUMMARY_DATA_ID), missing());
        assertFalse(scanner.isCurrent(snapshot));
        assertFalse(scanner.isCurrent(null));
    }
    
    @Test
    void fingerprintShouldBeStableAcrossVersionListOrderButIncludeRawSummary() {
        A2aHistoricalDefinitionSnapshot first = scanner.scanPage(NAMESPACE_ID, 1, 10)
            .getPageItems().get(0);
        AgentCardVersionInfo reordered = summary("1.0.0", "2.0.0", "1.0.0");
        summaryConfig.setContent(JacksonUtils.toJson(reordered));
        summaryConfig.setMd5("summary-md5-reordered");
        A2aHistoricalDefinitionSnapshot second = scanner.scanPage(NAMESPACE_ID, 1, 10)
            .getPageItems().get(0);
        
        assertNotEquals(first.getSourceFingerprint(), second.getSourceFingerprint());
        assertEquals(Arrays.asList("2.0.0", "1.0.0"),
            new ArrayList<String>(second.getVersions().keySet()));
    }
    
    @Test
    void shouldRejectUnavailablePagesAndInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> scanner.scanPage(NAMESPACE_ID, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> scanner.scanPage(NAMESPACE_ID, 1, 0));
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(),
            anyString(), anyString(), any())).thenReturn(null);
        assertThrows(IllegalStateException.class,
            () -> scanner.scanPage(NAMESPACE_ID, 1, 1));
        
        Page<ConfigInfo> withoutItems = new Page<ConfigInfo>();
        withoutItems.setPageItems(null);
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(),
            anyString(), anyString(), any())).thenReturn(withoutItems);
        assertThrows(IllegalStateException.class,
            () -> scanner.scanPage(NAMESPACE_ID, 1, 1));
    }
    
    @Test
    void shouldStrictlyValidateSummaryIdentityAndJson() {
        summaryConfig.setContent("{");
        assertInvalidScan();
        summaryConfig.setContent("null");
        assertInvalidScan();
        summaryConfig = null;
        page((ConfigInfo) null, 1, 1);
        assertInvalidScan();
        
        summaryConfig = summaryConfig(summary("1.0.0", "1.0.0"));
        summaryConfig.setMd5(null);
        page(summaryConfig, 1, 1);
        assertInvalidScan();
        summaryConfig.setMd5("md5");
        summaryConfig.setTenant("other");
        assertInvalidScan();
    }
    
    @Test
    void shouldStrictlyValidateSummarySemantics() {
        AgentCardVersionInfo value = summary("1.0.0", "1.0.0");
        value.setName("bad/name");
        assertInvalidSummary(value);
        value = summary("1.0.0", "1.0.0");
        value.setRegistrationType("OTHER");
        assertInvalidSummary(value);
        value = summary("1.0.0", "1.0.0");
        value.setVersionDetails(Collections.emptyList());
        assertInvalidSummary(value);
        value = summary("1.0.0", "1.0.0");
        value.getVersionDetails().set(0, null);
        assertInvalidSummary(value);
        value = summary("1.0.0", "bad version");
        assertInvalidSummary(value);
        value = summary("1.0.0", "1.0.0", "1.0.0");
        assertInvalidSummary(value);
        value = summary("bad version", "1.0.0");
        assertInvalidSummary(value);
        value = summary("2.0.0", "1.0.0");
        assertInvalidSummary(value);
    }
    
    @Test
    void shouldStrictlyValidateEveryVersionConfig() {
        String coordinate = key(Constants.A2A.AGENT_VERSION_GROUP,
            SUMMARY_DATA_ID + "-1.0.0");
        source.put(coordinate, missing());
        assertInvalidScan();
        source.put(coordinate, unavailable());
        assertInvalidScan();
        source.put(coordinate, found("{", "md5"));
        assertInvalidScan();
        AgentCardDetailInfo mismatch = card("1.0.0", "URL");
        mismatch.setName("other-agent");
        source.put(coordinate, found(JacksonUtils.toJson(mismatch), "md5"));
        assertInvalidScan();
        mismatch = card("2.0.0", "URL");
        source.put(coordinate, found(JacksonUtils.toJson(mismatch), "md5"));
        assertInvalidScan();
        AgentCardDetailInfo invalid = card("1.0.0", "OTHER");
        source.put(coordinate, found(JacksonUtils.toJson(invalid), "md5"));
        assertInvalidScan();
    }
    
    private void assertInvalidSummary(AgentCardVersionInfo value) {
        summaryConfig.setContent(JacksonUtils.toJson(value));
        assertInvalidScan();
    }
    
    private void assertInvalidScan() {
        assertThrows(RuntimeException.class, () -> scanner.scanPage(NAMESPACE_ID, 1, 100));
    }
    
    private void page(ConfigInfo configInfo, int total, int pages) {
        Page<ConfigInfo> page = new Page<ConfigInfo>();
        page.setPageItems(configInfo == null ? Collections.singletonList(null)
            : Collections.singletonList(configInfo));
        page.setTotalCount(total);
        page.setPagesAvailable(pages);
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(),
            anyString(), anyString(), any())).thenReturn(page);
    }
    
    private ConfigInfo summaryConfig(AgentCardVersionInfo summary) {
        ConfigInfo result = new ConfigInfo(SUMMARY_DATA_ID, Constants.A2A.AGENT_GROUP,
            JacksonUtils.toJson(summary));
        result.setTenant(NAMESPACE_ID);
        result.setMd5("summary-md5-1");
        return result;
    }
    
    private AgentCardVersionInfo summary(String latest, String... versions) {
        AgentCardVersionInfo result = new AgentCardVersionInfo();
        result.setName(AGENT_NAME);
        result.setDescription("Research");
        result.setLatestPublishedVersion(latest);
        result.setRegistrationType("URL");
        List<AgentVersionDetail> details = new ArrayList<AgentVersionDetail>();
        for (String version : versions) {
            AgentVersionDetail detail = new AgentVersionDetail();
            detail.setVersion(version);
            detail.setLatest(version.equals(latest));
            details.add(detail);
        }
        result.setVersionDetails(details);
        return result;
    }
    
    private AgentCardDetailInfo card(String version, String registrationType) {
        AgentCardDetailInfo result = new AgentCardDetailInfo();
        result.setName(AGENT_NAME);
        result.setVersion(version);
        result.setDescription("Research " + version);
        result.setRegistrationType(registrationType);
        AgentInterface agentInterface = new AgentInterface();
        agentInterface.setUrl("https://example.com/" + version);
        agentInterface.setProtocolBinding("HTTP+JSON");
        agentInterface.setProtocolVersion("0.3");
        result.setSupportedInterfaces(Collections.singletonList(agentInterface));
        return result;
    }
    
    private ConfigQueryChainResponse found(String content, String md5) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        result.setContent(content);
        result.setMd5(md5);
        return result;
    }
    
    private ConfigQueryChainResponse missing() {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        return result;
    }
    
    private ConfigQueryChainResponse unavailable() {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT);
        return result;
    }
    
    private String key(String group, String dataId) {
        return group + '\u0000' + dataId;
    }
}
