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
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aHistoricalDefinitionScanner {
    
    private final ConfigDetailService configDetailService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final A2aCanonicalDefinitionConverter definitionConverter;
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    public A2aHistoricalDefinitionScanner(ConfigDetailService configDetailService,
        ConfigQueryChainService configQueryChainService,
        A2aCanonicalDefinitionConverter definitionConverter,
        AgentIdCodecHolder agentIdCodecHolder) {
        this.configDetailService = configDetailService;
        this.configQueryChainService = configQueryChainService;
        this.definitionConverter = definitionConverter;
        this.agentIdCodecHolder = agentIdCodecHolder;
    }
    
    /**
     * Scan one bounded page of historical summary Configs and load every referenced Version.
     *
     * @param namespaceId namespace identifier
     * @param pageNo one-based page number
     * @param pageSize bounded page size
     * @return complete historical snapshots
     */
    public Page<A2aHistoricalDefinitionSnapshot> scanPage(String namespaceId, int pageNo,
        int pageSize) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        if (pageNo < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Historical A2A scan page must be positive");
        }
        Page<ConfigInfo> source = configDetailService.findConfigInfoPage(
            Constants.A2A.SEARCH_BLUR, pageNo, pageSize, Constants.ALL_PATTERN,
            Constants.A2A.AGENT_GROUP, namespaceId, null);
        if (source == null || source.getPageItems() == null) {
            throw new IllegalStateException("Historical A2A summary page is unavailable");
        }
        List<A2aHistoricalDefinitionSnapshot> snapshots =
            new ArrayList<A2aHistoricalDefinitionSnapshot>(source.getPageItems().size());
        for (ConfigInfo configInfo : source.getPageItems()) {
            snapshots.add(load(namespaceId, configInfo));
        }
        Page<A2aHistoricalDefinitionSnapshot> result =
            new Page<A2aHistoricalDefinitionSnapshot>();
        result.setPageNumber(pageNo);
        result.setTotalCount(source.getTotalCount());
        result.setPagesAvailable(resolvePages(source, pageSize));
        result.setPageItems(snapshots);
        return result;
    }
    
    /**
     * Load one historical Agent from its known public identity for write-after reconciliation.
     *
     * <p>The public identity comes from the successful historical mutation. The encoded Config
     * data id is used only as a storage coordinate and is never decoded into business identity.</p>
     *
     * @param namespaceId namespace identifier
     * @param agentName known public Agent name
     * @return complete source snapshot, or empty after a historical delete
     */
    public Optional<A2aHistoricalDefinitionSnapshot> scanOne(String namespaceId,
        String agentName) {
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        String dataId = agentIdCodecHolder.encode(agentName);
        SourceValue source = query(namespaceId, dataId, Constants.A2A.AGENT_GROUP);
        if (source == null) {
            return Optional.empty();
        }
        A2aHistoricalDefinitionSnapshot snapshot = load(namespaceId,
            toConfigInfo(dataId, source));
        if (!agentName.equals(snapshot.getSummary().getName())) {
            throw new IllegalStateException(
                "Historical A2A summary public identity does not match write hint");
        }
        return Optional.of(snapshot);
    }
    
    /**
     * Re-read the exact historical coordinates and compare the operation-scoped source fence.
     *
     * @param snapshot previously loaded source
     * @return whether summary and all Version Configs remain unchanged
     */
    public boolean isCurrent(A2aHistoricalDefinitionSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        SourceValue summary = query(snapshot.getNamespaceId(), snapshot.getSummaryDataId(),
            Constants.A2A.AGENT_GROUP);
        if (summary == null) {
            return false;
        }
        A2aHistoricalDefinitionSnapshot refreshed = load(snapshot.getNamespaceId(),
            toConfigInfo(snapshot.getSummaryDataId(), summary));
        return snapshot.getSourceFingerprint().equals(refreshed.getSourceFingerprint());
    }
    
    private A2aHistoricalDefinitionSnapshot load(String namespaceId, ConfigInfo summaryConfig) {
        requireSummaryConfig(namespaceId, summaryConfig);
        AgentCardVersionInfo summary = parse(summaryConfig.getContent(),
            AgentCardVersionInfo.class, "historical A2A summary");
        validateSummary(summary);
        Map<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot> versions =
            new LinkedHashMap<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot>();
        for (AgentVersionDetail version : summary.getVersionDetails()) {
            String dataId = summaryConfig.getDataId() + '-' + version.getVersion();
            SourceValue value = query(namespaceId, dataId, Constants.A2A.AGENT_VERSION_GROUP);
            if (value == null) {
                throw new IllegalStateException("Historical A2A Version is missing: "
                    + summary.getName() + '@' + version.getVersion());
            }
            AgentCardDetailInfo card = parse(value.content, AgentCardDetailInfo.class,
                "historical A2A Version");
            validateVersion(namespaceId, summary, version.getVersion(), card);
            versions.put(version.getVersion(),
                new A2aHistoricalDefinitionSnapshot.VersionSnapshot(dataId, value.content,
                    value.md5, card));
        }
        String fingerprint = fingerprint(summaryConfig.getContent(), summaryConfig.getMd5(),
            summary, versions);
        return new A2aHistoricalDefinitionSnapshot(namespaceId, summaryConfig.getDataId(),
            summaryConfig.getContent(), summaryConfig.getMd5(), summary, versions, fingerprint);
    }
    
    private void requireSummaryConfig(String namespaceId, ConfigInfo configInfo) {
        if (configInfo == null || StringUtils.isBlank(configInfo.getDataId())
            || StringUtils.isBlank(configInfo.getContent())
            || StringUtils.isBlank(configInfo.getMd5())) {
            throw new IllegalStateException(
                "Historical A2A summary Config identity is incomplete");
        }
        if (StringUtils.isNotBlank(configInfo.getTenant())
            && !namespaceId.equals(configInfo.getTenant())) {
            throw new IllegalStateException("Historical A2A summary namespace does not match");
        }
    }
    
    private void validateSummary(AgentCardVersionInfo summary) {
        if (summary == null) {
            throw new IllegalStateException("Historical A2A summary is empty");
        }
        AgentValidationUtils.validateAgentName(summary.getName());
        requireRegistrationType(summary.getRegistrationType(), summary.getName());
        List<AgentVersionDetail> details = summary.getVersionDetails();
        if (details == null || details.isEmpty()) {
            throw new IllegalStateException("Historical A2A summary contains no Version");
        }
        Set<String> versions = new HashSet<String>();
        for (AgentVersionDetail detail : details) {
            if (detail == null) {
                throw new IllegalStateException("Historical A2A summary contains a null Version");
            }
            AgentValidationUtils.validateVersion(detail.getVersion());
            if (!versions.add(detail.getVersion())) {
                throw new IllegalStateException("Duplicate historical A2A Version: "
                    + detail.getVersion());
            }
        }
        AgentValidationUtils.validateVersion(summary.getLatestPublishedVersion());
        if (!versions.contains(summary.getLatestPublishedVersion())) {
            throw new IllegalStateException(
                "Historical A2A latest Version does not exist: "
                    + summary.getLatestPublishedVersion());
        }
    }
    
    private void validateVersion(String namespaceId, AgentCardVersionInfo summary, String version,
        AgentCardDetailInfo card) {
        if (card == null || !summary.getName().equals(card.getName())
            || !version.equals(card.getVersion())) {
            throw new IllegalStateException("Historical A2A Version identity does not match: "
                + summary.getName() + '@' + version);
        }
        try {
            definitionConverter.normalizeRegistrationType(card.getRegistrationType(), null);
            definitionConverter.convert(namespaceId, card, card.getRegistrationType(), false);
        } catch (Exception e) {
            throw new IllegalStateException("Historical A2A Version cannot be normalized: "
                + summary.getName() + '@' + version, e);
        }
    }
    
    private void requireRegistrationType(String registrationType, String identity) {
        try {
            definitionConverter.normalizeRegistrationType(registrationType, null);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Historical A2A registration type is invalid: " + identity, e);
        }
    }
    
    private SourceValue query(String namespaceId, String dataId, String group) {
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
            dataId, group, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response == null || response
            .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return null;
        }
        if (response.getStatus() != ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
            || StringUtils.isBlank(response.getContent())
            || StringUtils.isBlank(response.getMd5())) {
            throw new IllegalStateException("Historical A2A Config is unavailable: " + dataId);
        }
        return new SourceValue(response.getContent(), response.getMd5());
    }
    
    private String fingerprint(String summaryContent, String summaryMd5,
        AgentCardVersionInfo summary,
        Map<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot> versions) {
        MessageDigest digest = DigestUtils.getSha256Digest();
        update(digest, summaryContent);
        update(digest, summaryMd5);
        update(digest, summary.getLatestPublishedVersion());
        update(digest, "enable");
        List<String> sortedVersions = new ArrayList<String>(versions.keySet());
        Collections.sort(sortedVersions);
        for (String version : sortedVersions) {
            update(digest, version);
            A2aHistoricalDefinitionSnapshot.VersionSnapshot value = versions.get(version);
            update(digest, value.getMd5());
        }
        return Hex.encodeHexString(digest.digest());
    }
    
    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
    
    private int resolvePages(Page<ConfigInfo> page, int pageSize) {
        if (page.getPagesAvailable() > 0) {
            return page.getPagesAvailable();
        }
        return (int) Math.ceil((double) page.getTotalCount() / pageSize);
    }
    
    private ConfigInfo toConfigInfo(String dataId, SourceValue value) {
        ConfigInfo result = new ConfigInfo(dataId, Constants.A2A.AGENT_GROUP, value.content);
        result.setMd5(value.md5);
        return result;
    }
    
    private <T> T parse(String content, Class<T> type, String description) {
        try {
            return JacksonUtils.toObj(content, type);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid " + description + " JSON", e);
        }
    }
    
    private static final class SourceValue {
        
        private final String content;
        
        private final String md5;
        
        private SourceValue(String content, String md5) {
            this.content = content;
            this.md5 = md5;
        }
    }
}
