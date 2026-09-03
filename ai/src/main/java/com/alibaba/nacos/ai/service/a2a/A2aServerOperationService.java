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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentOperationService;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeEndpointMapper;
import com.alibaba.nacos.ai.utils.AgentCardUtil;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.utils.EndpointNaturalKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Compatibility adapter between legacy A2A surfaces and the canonical Agent model.
 *
 * <p>AgentCard definitions are written only through {@link AgentOperationService}. Canonical
 * SERVICE queries project exact-Version-compatible endpoints from the shared RAD Runtime Naming
 * service.</p>
 *
 * @author Nacos
 */
@Component
public class A2aServerOperationService implements A2aOperationService {
    
    private static final String A2A_PROTOCOL = A2aCanonicalDefinitionConverter.A2A_PROTOCOL;
    
    private static final int SCAN_PAGE_SIZE = 100;
    
    private static final DateTimeFormatter LEGACY_TIME_FORMATTER =
        DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT).withZone(ZoneOffset.UTC);
    
    private static final ExecutorService PROJECTION_EXECUTOR =
        ExecutorFactory.Managed.newFixedExecutorService(
            A2aServerOperationService.class.getCanonicalName(), 4,
            new NameThreadFactory("com.alibaba.nacos.ai.a2a-projection"));
    
    private final AgentOperationService agentOperationService;
    
    private final ServiceStorage serviceStorage;
    
    private final A2aCanonicalDefinitionConverter definitionConverter;
    
    private final Executor projectionExecutor;
    
    @Autowired
    public A2aServerOperationService(AgentOperationService agentOperationService,
        ServiceStorage serviceStorage, A2aCanonicalDefinitionConverter definitionConverter) {
        this(agentOperationService, serviceStorage, definitionConverter, PROJECTION_EXECUTOR);
    }
    
    A2aServerOperationService(AgentOperationService agentOperationService,
        ServiceStorage serviceStorage, A2aCanonicalDefinitionConverter definitionConverter,
        Executor projectionExecutor) {
        this.agentOperationService = agentOperationService;
        this.serviceStorage = serviceStorage;
        this.definitionConverter = definitionConverter;
        this.projectionExecutor = projectionExecutor;
    }
    
    /**
     * Register the first legacy AgentCard definition.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType legacy URL or SERVICE type
     * @throws NacosException when the Agent already exists or validation fails
     */
    public void registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
        throws NacosException {
        String normalizedType = definitionConverter.normalizeRegistrationType(registrationType,
            AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        agentOperationService.registerLegacyOnlineVersion(namespaceId,
            definitionConverter.convert(namespaceId, agentCard, normalizedType, true));
    }
    
    /**
     * Release a legacy AgentCard from the Client SDK.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType legacy URL or SERVICE type
     * @param setAsLatest whether a new or restored Version should become latest
     * @throws NacosException when validation, authorization, or persistence fails
     */
    public void releaseAgent(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException {
        String normalizedType = definitionConverter.normalizeRegistrationType(registrationType,
            AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE);
        agentOperationService.releaseLegacyOnlineVersion(namespaceId,
            definitionConverter.convert(namespaceId, agentCard, normalizedType, true),
            setAsLatest);
    }
    
    /**
     * Update or add one legacy AgentCard Version.
     *
     * @param agentCard AgentCard definition
     * @param namespaceId namespace identifier
     * @param registrationType legacy URL or SERVICE type; blank inherits existing A2A type
     * @param setAsLatest whether the target Version should become latest
     * @throws NacosException when the Agent is absent or canonical content conflicts
     */
    public void updateAgentCard(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException {
        String normalizedType = StringUtils.isBlank(registrationType)
            ? resolveInheritedRegistrationType(namespaceId, agentCard.getName(),
                agentCard.getVersion())
            : definitionConverter.normalizeRegistrationType(registrationType, null);
        agentOperationService.updateLegacyOnlineVersion(namespaceId,
            definitionConverter.convert(namespaceId, agentCard, normalizedType, false),
            setAsLatest);
    }
    
    /**
     * Delete one exact Version or the complete Agent when Version is blank.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Version, or blank for the complete Agent
     * @throws NacosException when authorization or persistence fails
     */
    public void deleteAgent(String namespaceId, String agentName, String version)
        throws NacosException {
        if (StringUtils.isBlank(version)) {
            agentOperationService.deleteLegacyAgentIfPresent(namespaceId, agentName);
        } else {
            agentOperationService.deleteLegacyVersionIfPresent(namespaceId, agentName, version);
        }
    }
    
    /**
     * Query one legacy AgentCard for a management surface.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Version, or blank for common latest
     * @param registrationType optional query projection type
     * @return projected legacy AgentCard
     * @throws NacosException when the Agent or Version cannot be projected
     */
    public AgentCardDetailInfo getAgentCard(String namespaceId, String agentName, String version,
        String registrationType) throws NacosException {
        return projectAgentCard(namespaceId, agentName, version, registrationType, false);
    }
    
    /**
     * Query one legacy AgentCard for the Client data plane.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @param version exact Version, or blank for common latest
     * @param registrationType optional query projection type
     * @return projected legacy AgentCard
     * @throws NacosException when the Agent is disabled or the Version cannot be projected
     */
    public AgentCardDetailInfo getAgentCardForClient(String namespaceId, String agentName,
        String version, String registrationType) throws NacosException {
        return projectAgentCard(namespaceId, agentName, version, registrationType, true);
    }
    
    /**
     * List legacy AgentCard summaries after A2A filtering.
     *
     * @param namespaceId namespace identifier
     * @param agentName optional Agent name filter
     * @param search legacy accurate or blur mode
     * @param pageNo page number
     * @param pageSize page size
     * @return legacy summary page
     * @throws NacosException when Agent content cannot be projected
     */
    public Page<AgentCardVersionInfo> listAgents(String namespaceId, String agentName,
        String search, int pageNo, int pageSize) throws NacosException {
        String normalizedName = agentName == null ? StringUtils.EMPTY : agentName;
        boolean accurate = Constants.A2A.SEARCH_ACCURATE.equalsIgnoreCase(search)
            && StringUtils.isNotEmpty(normalizedName);
        List<AgentSummary> eligible = loadEligibleSummaries(namespaceId, normalizedName, accurate);
        int from = Math.min(eligible.size(), (pageNo - 1) * pageSize);
        int to = Math.min(eligible.size(), from + pageSize);
        List<CompletableFuture<AgentCardVersionInfo>> futures =
            new ArrayList<CompletableFuture<AgentCardVersionInfo>>(to - from);
        for (AgentSummary summary : eligible.subList(from, to)) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return projectVersionInfo(summary);
                } catch (NacosException e) {
                    throw new CompletionException(e);
                }
            }, projectionExecutor));
        }
        List<AgentCardVersionInfo> items =
            new ArrayList<AgentCardVersionInfo>(futures.size());
        for (CompletableFuture<AgentCardVersionInfo> future : futures) {
            try {
                items.add(future.join());
            } catch (CompletionException e) {
                if (e.getCause() instanceof NacosException) {
                    throw (NacosException) e.getCause();
                }
                throw e;
            }
        }
        Page<AgentCardVersionInfo> result = new Page<AgentCardVersionInfo>();
        result.setPageNumber(pageNo);
        result.setTotalCount(eligible.size());
        result.setPagesAvailable((int) Math.ceil((double) eligible.size() / pageSize));
        result.setPageItems(items);
        return result;
    }
    
    /**
     * List all online A2A Versions for one Agent.
     *
     * @param namespaceId namespace identifier
     * @param name exact Agent name
     * @return legacy Version summaries
     * @throws NacosException when the Agent is absent or contains no A2A Version
     */
    public List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> listAgentVersions(
        String namespaceId, String name) throws NacosException {
        Agent agent = getLegacyAgent(namespaceId, name);
        Set<String> a2aVersions = a2aVersions(agent.getVersionCatalog().getOnlineVersions());
        if (a2aVersions.isEmpty()) {
            throw agentNotFound(name);
        }
        return toLegacyVersionDetails(loadAllOnlineVersionSummaries(namespaceId, name),
            a2aVersions, latestVersion(agent));
    }
    
    private AgentCardDetailInfo projectAgentCard(String namespaceId, String agentName,
        String version, String registrationType, boolean clientRead) throws NacosException {
        Agent agent = getLegacyAgent(namespaceId, agentName);
        if (clientRead && !AiConstants.Agent.RESOURCE_STATUS_ENABLE.equals(agent.getStatus())) {
            throw agentNotFound(agentName);
        }
        Set<String> a2aVersions = a2aVersions(agent.getVersionCatalog().getOnlineVersions());
        if (a2aVersions.isEmpty()) {
            throw agentNotFound(agentName);
        }
        String targetVersion = StringUtils.isBlank(version) ? latestVersion(agent) : version;
        if (StringUtils.isBlank(targetVersion) || !a2aVersions.contains(targetVersion)) {
            throw versionNotFound(agentName, targetVersion);
        }
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail detail;
        try {
            detail = agentOperationService.getVersion(namespaceId, agentName, targetVersion);
        } catch (NacosApiException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                throw versionNotFound(agentName, targetVersion);
            }
            throw e;
        }
        if (!AiConstants.Agent.VERSION_STATUS_ONLINE.equals(detail.getStatus())) {
            throw versionNotFound(agentName, targetVersion);
        }
        AgentCallInterface callInterface = requireA2aCallInterface(detail, agentName,
            targetVersion);
        String storedType = registrationType(callInterface);
        String queryType = StringUtils.isBlank(registrationType) ? storedType
            : definitionConverter.normalizeRegistrationType(registrationType, null);
        AgentCardDetailInfo result = toLegacyCard(callInterface, agentName, targetVersion,
            storedType);
        if (AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE.equals(queryType)) {
            injectRuntimeEndpoints(result, callInterface, namespaceId);
        }
        if (targetVersion.equals(latestVersion(agent))) {
            result.setLatestVersion(Boolean.TRUE);
        }
        return result;
    }
    
    private String resolveInheritedRegistrationType(String namespaceId, String agentName,
        String version) throws NacosException {
        Agent agent = getLegacyAgent(namespaceId, agentName);
        AgentCallInterface target = findA2aCallInterface(
            getVersionIfA2a(namespaceId, agentName, version));
        if (target != null) {
            return registrationType(target);
        }
        String latest = latestVersion(agent);
        target = findA2aCallInterface(getVersionIfA2a(namespaceId, agentName, latest));
        return target == null ? AiConstants.A2a.A2A_ENDPOINT_TYPE_URL : registrationType(target);
    }
    
    private com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail getVersionIfA2a(
        String namespaceId, String agentName, String version) throws NacosException {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        try {
            return agentOperationService.getVersion(namespaceId, agentName, version);
        } catch (NacosApiException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
    
    private AgentCallInterface requireA2aCallInterface(
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail detail, String agentName,
        String version) throws NacosApiException {
        AgentCallInterface result = findA2aCallInterface(detail);
        if (result == null) {
            throw versionNotFound(agentName, version);
        }
        return result;
    }
    
    private AgentCallInterface findA2aCallInterface(
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail detail) {
        if (detail == null || detail.getCallInterfaces() == null) {
            return null;
        }
        for (AgentCallInterface callInterface : detail.getCallInterfaces()) {
            if (callInterface != null && A2A_PROTOCOL.equals(callInterface.getProtocol())) {
                return callInterface;
            }
        }
        return null;
    }
    
    private String registrationType(AgentCallInterface callInterface) {
        return callInterface.getEndpointSourceOrder() != null
            && !callInterface.getEndpointSourceOrder().isEmpty()
            && EndpointSource.RUNTIME == callInterface.getEndpointSourceOrder().get(0)
                ? AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE
                : AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;
    }
    
    private AgentCardDetailInfo toLegacyCard(AgentCallInterface callInterface, String agentName,
        String version, String storedType) throws NacosApiException {
        try {
            AgentCard card = JacksonUtils.toObj(JacksonUtils.toJson(
                callInterface.getNativeDescriptor()), AgentCard.class);
            AgentRequestUtil.validateAgentCard(card);
            if (!agentName.equals(card.getName()) || !version.equals(card.getVersion())) {
                throw versionNotFound(agentName, version);
            }
            return AgentCardUtil.buildAgentCardDetailInfo(card, storedType);
        } catch (NacosApiException e) {
            throw versionNotFound(agentName, version);
        } catch (RuntimeException e) {
            throw versionNotFound(agentName, version);
        }
    }
    
    private void injectRuntimeEndpoints(AgentCardDetailInfo card,
        AgentCallInterface callInterface, String namespaceId) {
        String serviceName = RadServiceNameComposer.compose(card.getName(), A2A_PROTOCOL);
        Service service =
            Service.newService(namespaceId, Constants.Agent.AGENT_ENDPOINT_GROUP, serviceName);
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        if (serviceInfo == null || serviceInfo.getHosts() == null) {
            return;
        }
        List<Instance> hosts = new ArrayList<Instance>();
        for (Instance instance : serviceInfo.getHosts()) {
            if (instance != null && instance.isEnabled()
                && AgentRuntimeEndpointMapper.supportsVersion(instance, card.getVersion())) {
                hosts.add(instance);
            }
        }
        hosts.sort(Comparator.comparingInt(this::endpointPriority)
            .thenComparing(instance -> endpointNaturalKey(namespaceId, card.getName(), instance)));
        if (hosts.isEmpty()) {
            return;
        }
        List<AgentInterface> interfaces = new ArrayList<AgentInterface>(hosts.size());
        for (Instance instance : hosts) {
            AgentInterface agentInterface = AgentCardUtil.buildAgentInterface(instance);
            if (StringUtils.isBlank(agentInterface.getProtocolVersion())) {
                agentInterface.setProtocolVersion(callInterface.getProtocolVersion());
            }
            interfaces.add(agentInterface);
        }
        AgentInterface preferred = selectPreferred(interfaces, card.getPreferredTransport());
        card.setSupportedInterfaces(interfaces);
        card.setAdditionalInterfaces(new ArrayList<AgentInterface>(interfaces));
        card.setUrl(preferred.getUrl());
        card.setPreferredTransport(preferred.getProtocolBinding());
        card.setProtocolVersion(preferred.getProtocolVersion());
    }
    
    private int endpointPriority(Instance instance) {
        String value = metadata(instance).get(Constants.Agent.AGENT_ENDPOINT_PRIORITY_KEY);
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    
    private String endpointNaturalKey(String namespaceId, String agentName, Instance instance) {
        AgentInterface agentInterface = AgentCardUtil.buildAgentInterface(instance);
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(agentInterface.getUrl());
        endpoint.setTransport(agentInterface.getProtocolBinding());
        return EndpointNaturalKey.of(namespaceId, agentName, A2A_PROTOCOL, endpoint).toString();
    }
    
    private Map<String, String> metadata(Instance instance) {
        return instance.getMetadata() == null ? Collections.<String, String>emptyMap()
            : instance.getMetadata();
    }
    
    private AgentInterface selectPreferred(List<AgentInterface> interfaces,
        String preferredTransport) {
        for (AgentInterface agentInterface : interfaces) {
            if (StringUtils.equalsIgnoreCase(agentInterface.getProtocolBinding(),
                preferredTransport)) {
                return agentInterface;
            }
        }
        return interfaces.get(0);
    }
    
    private List<AgentSummary> loadEligibleSummaries(String namespaceId, String agentName,
        boolean accurate) throws NacosException {
        List<AgentSummary> result = new ArrayList<AgentSummary>();
        int pageNo = 1;
        while (true) {
            Page<AgentSummary> page = agentOperationService.listAgents(namespaceId,
                StringUtils.isBlank(agentName) ? null : agentName, null, null, null, null, pageNo,
                SCAN_PAGE_SIZE);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                return result;
            }
            for (AgentSummary summary : page.getPageItems()) {
                if ((!accurate || agentName.equals(summary.getAgentName()))
                    && hasA2aLatest(summary)) {
                    result.add(summary);
                }
            }
            if (page.getPageItems().size() < SCAN_PAGE_SIZE) {
                return result;
            }
            pageNo++;
        }
    }
    
    private boolean hasA2aLatest(AgentSummary summary) {
        if (summary.getVersionCatalog() == null
            || StringUtils.isBlank(summary.getVersionCatalog().getLatestVersion())
            || summary.getVersionCatalog().getOnlineVersions() == null) {
            return false;
        }
        String latest = summary.getVersionCatalog().getLatestVersion();
        for (AgentVersionCatalogEntry entry : summary.getVersionCatalog().getOnlineVersions()) {
            if (latest.equals(entry.getVersion()) && entry.getProtocols() != null
                && entry.getProtocols().contains(A2A_PROTOCOL)) {
                return true;
            }
        }
        return false;
    }
    
    private AgentCardVersionInfo projectVersionInfo(AgentSummary summary) throws NacosException {
        String latest = summary.getVersionCatalog().getLatestVersion();
        com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail latestDetail =
            agentOperationService.getVersion(summary.getNamespaceId(), summary.getAgentName(),
                latest);
        AgentCallInterface latestA2a =
            requireA2aCallInterface(latestDetail, summary.getAgentName(), latest);
        AgentCardDetailInfo latestCard = toLegacyCard(latestA2a, summary.getAgentName(), latest,
            registrationType(latestA2a));
        AgentCardVersionInfo result = AgentCardUtil.buildAgentCardVersionInfo(latestCard,
            registrationType(latestA2a), true);
        Set<String> versions = a2aVersions(summary.getVersionCatalog().getOnlineVersions());
        result.setVersionDetails(toLegacyVersionDetails(loadAllOnlineVersionSummaries(
            summary.getNamespaceId(), summary.getAgentName()), versions, latest));
        return result;
    }
    
    private List<AgentVersionSummary> loadAllOnlineVersionSummaries(String namespaceId,
        String agentName) throws NacosException {
        List<AgentVersionSummary> result = new ArrayList<AgentVersionSummary>();
        int pageNo = 1;
        while (true) {
            Page<AgentVersionSummary> page = agentOperationService.listVersions(namespaceId,
                agentName, AiConstants.Agent.VERSION_STATUS_ONLINE, pageNo, SCAN_PAGE_SIZE);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                return result;
            }
            result.addAll(page.getPageItems());
            if (page.getPageItems().size() < SCAN_PAGE_SIZE) {
                return result;
            }
            pageNo++;
        }
    }
    
    private Set<String> a2aVersions(List<AgentVersionCatalogEntry> entries) {
        Set<String> result = new LinkedHashSet<String>();
        if (entries != null) {
            for (AgentVersionCatalogEntry entry : entries) {
                if (entry.getProtocols() != null && entry.getProtocols().contains(A2A_PROTOCOL)) {
                    result.add(entry.getVersion());
                }
            }
        }
        return result;
    }
    
    private List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> toLegacyVersionDetails(
        List<AgentVersionSummary> summaries, Set<String> a2aVersions, String latest) {
        Map<String, AgentVersionSummary> byVersion = new HashMap<String, AgentVersionSummary>();
        for (AgentVersionSummary summary : summaries) {
            byVersion.put(summary.getVersion(), summary);
        }
        List<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail> result =
            new ArrayList<com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail>();
        for (String version : a2aVersions) {
            AgentVersionSummary summary = byVersion.get(version);
            if (summary == null) {
                continue;
            }
            com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail detail =
                new com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail();
            detail.setVersion(version);
            detail.setCreatedAt(formatTime(summary.getCreateTime()));
            detail.setUpdatedAt(formatTime(summary.getUpdateTime()));
            detail.setLatest(version.equals(latest));
            result.add(detail);
        }
        return result;
    }
    
    private String latestVersion(Agent agent) {
        return agent.getVersionCatalog() == null ? null
            : agent.getVersionCatalog().getLatestVersion();
    }
    
    private Agent getLegacyAgent(String namespaceId, String agentName) throws NacosException {
        try {
            return agentOperationService.getAgent(namespaceId, agentName);
        } catch (NacosApiException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                throw agentNotFound(agentName);
            }
            throw e;
        }
    }
    
    private String formatTime(Long epochMillis) {
        return LEGACY_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
    
    private NacosApiException agentNotFound(String agentName) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_NOT_FOUND,
            "Agent not found: " + agentName);
    }
    
    private NacosApiException versionNotFound(String agentName, String version) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.AGENT_VERSION_NOT_FOUND,
            "Agent " + agentName + " version " + version + " not found.");
    }
}
