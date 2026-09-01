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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.metadata.AgentResourceExtSerializer;
import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionCatalogBuilder;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationTargetStore {
    
    public static final String MIGRATION_RESOURCE_SOURCE = "legacy-a2a-migration-v1";
    
    private static final int VERSION_PAGE_SIZE = 100;
    
    private static final int RESOURCE_PAGE_SIZE = 100;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final AiResourceVersionPersistService versionPersistService;
    
    private final AgentVersionStorageService storageService;
    
    private final A2aMigrationStorageVerifier storageVerifier;
    
    public A2aMigrationTargetStore(AiResourcePersistService resourcePersistService,
        AiResourceVersionPersistService versionPersistService,
        AgentVersionStorageService storageService,
        A2aMigrationStorageVerifier storageVerifier) {
        this.resourcePersistService = resourcePersistService;
        this.versionPersistService = versionPersistService;
        this.storageService = storageService;
        this.storageVerifier = storageVerifier;
    }
    
    /**
     * Persist one complete migration definition with Version-first, Resource-last visibility.
     *
     * @param definition normalized migration target
     * @param sourceCurrent operation-scoped historical source fence
     * @return create, repair, or equivalence outcome
     * @throws NacosException when target state conflicts or persistence fails
     */
    public Result reconcile(A2aMigrationDefinition definition, BooleanSupplier sourceCurrent)
        throws NacosException {
        PreparedTarget target = prepare(definition);
        requireCurrentSource(sourceCurrent);
        AiResource existingResource = resourcePersistService.find(target.namespaceId,
            target.agentName, Constants.Agent.RESOURCE_TYPE_AGENT);
        Map<String, AiResourceVersion> existingVersions = listVersionRows(target.namespaceId,
            target.agentName);
        if (existingResource != null
            && !MIGRATION_RESOURCE_SOURCE.equals(existingResource.getFrom())) {
            if (strictlyEquivalent(existingResource, existingVersions, target)) {
                return Result.EXTERNAL_EQUIVALENT;
            }
            throw conflict("Canonical Agent conflicts with historical A2A definition: "
                + target.agentName);
        }
        if (existingResource == null) {
            preflightUnownedVersionRows(existingVersions, target);
        }
        int changes = writeVersions(existingResource, existingVersions, target);
        requireCurrentSource(sourceCurrent);
        changes += publishResource(existingResource, target);
        requireCurrentSource(sourceCurrent);
        changes += deleteExtraOwnedVersions(existingResource, existingVersions, target);
        if (changes == 0) {
            return Result.EQUIVALENT;
        }
        return existingResource == null ? Result.CREATED : Result.REPAIRED;
    }
    
    /**
     * List target Agent names owned by this temporary migration.
     *
     * @param namespaceId namespace identifier
     * @return complete migrated-name set
     */
    public Set<String> listMigratedAgentNames(String namespaceId) {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(namespaceId);
        condition.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        Set<String> result = new LinkedHashSet<String>();
        int pageNo = 1;
        int pages = 1;
        while (pageNo <= pages) {
            Page<AiResource> page = resourcePersistService.list(condition, pageNo,
                RESOURCE_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw new IllegalStateException("Unable to scan migrated Agent Resources");
            }
            pages = resolvePages(page, RESOURCE_PAGE_SIZE);
            for (AiResource resource : page.getPageItems()) {
                if (resource != null && MIGRATION_RESOURCE_SOURCE.equals(resource.getFrom())) {
                    result.add(resource.getName());
                }
            }
            pageNo++;
        }
        return result;
    }
    
    /**
     * Delete one confirmed historical orphan only when its canonical Resource is migration-owned.
     *
     * @param namespaceId namespace identifier
     * @param agentName exact Agent name
     * @return whether a migration-owned Resource was deleted
     * @throws NacosException when cleanup fails
     */
    public boolean deleteConfirmedOrphan(String namespaceId, String agentName)
        throws NacosException {
        AiResource resource = resourcePersistService.find(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        if (resource == null || !MIGRATION_RESOURCE_SOURCE.equals(resource.getFrom())) {
            return false;
        }
        Map<String, AiResourceVersion> versions = listVersionRows(namespaceId, agentName);
        if (resourcePersistService.delete(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT) != 1) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to delete migrated Agent Resource orphan: " + agentName);
        }
        for (AiResourceVersion version : versions.values()) {
            if (versionPersistService.delete(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, version.getVersion()) == 1) {
                deleteStorage(version);
            }
        }
        return true;
    }
    
    private PreparedTarget prepare(A2aMigrationDefinition definition) {
        if (definition == null || definition.getAgent() == null
            || definition.getVersions().isEmpty()) {
            throw new IllegalArgumentException("Complete migrated Agent definition is required");
        }
        Agent sourceAgent = definition.getAgent();
        Map<String, List<String>> protocols = new LinkedHashMap<String, List<String>>();
        Map<String, AgentVersionDetail> versions =
            new LinkedHashMap<String, AgentVersionDetail>();
        Map<String, PreparedAgentVersionWrite> prepared =
            new LinkedHashMap<String, PreparedAgentVersionWrite>();
        for (AgentVersionDetail version : definition.getVersions()) {
            if (versions.put(version.getVersion(), version) != null) {
                throw new IllegalArgumentException(
                    "Duplicate migrated Agent Version: " + version.getVersion());
            }
            protocols.put(version.getVersion(), protocolNames(version.getCallInterfaces()));
            PreparedAgentVersionWrite write = storageService.prepare(sourceAgent.getNamespaceId(),
                sourceAgent.getAgentName(), version.getVersion(),
                new AgentVersionContent(version.getCallInterfaces()));
            prepared.put(version.getVersion(), write);
            version.setContentDigest(write.getDescriptor().getContentDigest());
            version.setCreateTime(0L);
            version.setUpdateTime(0L);
        }
        if (!versions.containsKey(definition.getLatestVersion())) {
            throw new IllegalArgumentException("Migrated latest Version does not exist");
        }
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put(AiResourceConstants.LABEL_LATEST, definition.getLatestVersion());
        AgentVersionCatalogBuilder.Result derived = AgentVersionCatalogBuilder.build(protocols,
            labels);
        Agent agent = copyAgent(sourceAgent);
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        versionInfo.setOnlineCnt(versions.size());
        versionInfo.setLabels(new HashMap<String, String>(derived.getLabels()));
        agent.setVersionInfo(versionInfo);
        agent.setVersionCatalog(derived.getVersionCatalog());
        agent.setMetaVersion(1L);
        agent.setCreateTime(0L);
        agent.setUpdateTime(0L);
        AgentModelValidator.validateAgent(agent);
        for (AgentVersionDetail version : versions.values()) {
            AgentModelValidator.validateVersionDetail(version);
        }
        AiResource resource = toResourceRow(agent);
        Map<String, AiResourceVersion> versionRows =
            new LinkedHashMap<String, AiResourceVersion>();
        for (Map.Entry<String, AgentVersionDetail> entry : versions.entrySet()) {
            versionRows.put(entry.getKey(), toVersionRow(entry.getValue(),
                prepared.get(entry.getKey()).getDescriptor()));
        }
        return new PreparedTarget(agent.getNamespaceId(), agent.getAgentName(), resource,
            versionRows, prepared);
    }
    
    private void preflightUnownedVersionRows(Map<String, AiResourceVersion> existing,
        PreparedTarget target) throws NacosException {
        if (!target.versionRows.keySet().equals(existing.keySet())) {
            if (!existing.isEmpty()) {
                throw conflict("Unowned canonical Agent Version rows conflict with migration: "
                    + target.agentName);
            }
            return;
        }
        for (Map.Entry<String, AiResourceVersion> entry : existing.entrySet()) {
            if (!equivalentVersionContent(entry.getValue(), target.prepared.get(entry.getKey()))
                || !AiConstants.Agent.VERSION_STATUS_ONLINE.equals(entry.getValue().getStatus())) {
                throw conflict("Unowned canonical Agent Version conflicts with migration: "
                    + target.agentName + '@' + entry.getKey());
            }
        }
    }
    
    private int writeVersions(AiResource existingResource,
        Map<String, AiResourceVersion> existingVersions, PreparedTarget target)
        throws NacosException {
        int changes = 0;
        for (Map.Entry<String, AiResourceVersion> entry : target.versionRows.entrySet()) {
            String version = entry.getKey();
            AiResourceVersion expected = entry.getValue();
            AiResourceVersion actual = existingVersions.get(version);
            boolean contentEquivalent = actual != null
                && equivalentVersionContent(actual, target.prepared.get(version));
            boolean descriptorEquivalent = actual != null
                && sameDescriptor(actual, expected);
            if (actual != null && contentEquivalent && descriptorEquivalent
                && sameVersionMetadata(actual, expected)) {
                continue;
            }
            if (actual != null && existingResource == null) {
                throw conflict("Unowned canonical Agent Version cannot be repaired: "
                    + target.agentName + '@' + version);
            }
            if (actual != null && !"nacos".equals(actual.getAuthor())) {
                throw conflict("Migrated Agent Version author conflicts: " + target.agentName
                    + '@' + version);
            }
            storageVerifier.saveAndVerify(target.prepared.get(version));
            if (actual == null) {
                insertVersion(expected);
            } else {
                updateVersion(actual, expected);
            }
            changes++;
        }
        return changes;
    }
    
    private int publishResource(AiResource existing, PreparedTarget target)
        throws NacosException {
        if (existing == null) {
            try {
                long id = resourcePersistService.insert(target.resource);
                if (id <= 0) {
                    throw new IllegalStateException(
                        "Migrated Agent Resource insert returned no id");
                }
                return 1;
            } catch (RuntimeException e) {
                AiResource recovered = resourcePersistService.find(target.namespaceId,
                    target.agentName, Constants.Agent.RESOURCE_TYPE_AGENT);
                if (recovered != null && MIGRATION_RESOURCE_SOURCE.equals(recovered.getFrom())
                    && sameOwnedResource(recovered, target.resource)) {
                    return 0;
                }
                if (recovered != null && strictlyEquivalent(recovered,
                    listVersionRows(target.namespaceId, target.agentName), target)) {
                    return 0;
                }
                if (e instanceof DuplicateKeyException || recovered != null) {
                    throw conflict("Canonical Agent appeared during migration: "
                        + target.agentName);
                }
                throw new NacosException(NacosException.SERVER_ERROR,
                    "Failed to insert migrated Agent Resource: " + target.agentName, e);
            }
        }
        if (sameOwnedResource(existing, target.resource)) {
            return 0;
        }
        if (existing.getMetaVersion() == null || !resourcePersistService.updateMetaCas(
            target.namespaceId, target.agentName, Constants.Agent.RESOURCE_TYPE_AGENT,
            existing.getMetaVersion(), target.resource)) {
            AiResource recovered = resourcePersistService.find(target.namespaceId,
                target.agentName, Constants.Agent.RESOURCE_TYPE_AGENT);
            if (recovered != null && MIGRATION_RESOURCE_SOURCE.equals(recovered.getFrom())
                && sameOwnedResource(recovered, target.resource)) {
                return 0;
            }
            throw conflict("Migrated Agent Resource changed concurrently: " + target.agentName);
        }
        return 1;
    }
    
    private int deleteExtraOwnedVersions(AiResource existingResource,
        Map<String, AiResourceVersion> existingVersions, PreparedTarget target)
        throws NacosException {
        if (existingResource == null
            || !MIGRATION_RESOURCE_SOURCE.equals(existingResource.getFrom())) {
            return 0;
        }
        int changes = 0;
        for (Map.Entry<String, AiResourceVersion> entry : existingVersions.entrySet()) {
            if (target.versionRows.containsKey(entry.getKey())) {
                continue;
            }
            int deleted = versionPersistService.delete(target.namespaceId, target.agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, entry.getKey());
            if (deleted == 1) {
                deleteStorage(entry.getValue());
                changes++;
            }
        }
        return changes;
    }
    
    private boolean strictlyEquivalent(AiResource resource,
        Map<String, AiResourceVersion> versions, PreparedTarget target) {
        if (!sameRepresentation(resource, target.resource)
            || !versions.keySet().equals(target.versionRows.keySet())) {
            return false;
        }
        for (Map.Entry<String, AiResourceVersion> entry : versions.entrySet()) {
            if (!AiConstants.Agent.VERSION_STATUS_ONLINE.equals(entry.getValue().getStatus())
                || !equivalentVersionContent(entry.getValue(),
                    target.prepared.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
    
    private boolean sameOwnedResource(AiResource actual, AiResource expected) {
        return MIGRATION_RESOURCE_SOURCE.equals(actual.getFrom())
            && Objects.equals(actual.getOwner(), expected.getOwner())
            && Objects.equals(actual.getScope(), expected.getScope())
            && sameRepresentation(actual, expected);
    }
    
    private boolean sameRepresentation(AiResource actual, AiResource expected) {
        return actual != null && Objects.equals(actual.getNamespaceId(), expected.getNamespaceId())
            && Objects.equals(actual.getName(), expected.getName())
            && Objects.equals(actual.getType(), expected.getType())
            && Objects.equals(actual.getDesc(), expected.getDesc())
            && Objects.equals(actual.getStatus(), expected.getStatus())
            && equivalentJsonOrEmptyList(actual.getBizTags(), expected.getBizTags())
            && equivalentJson(actual.getExt(), expected.getExt())
            && equivalentJson(actual.getVersionInfo(), expected.getVersionInfo());
    }
    
    private boolean sameVersionMetadata(AiResourceVersion actual, AiResourceVersion expected) {
        return Objects.equals(actual.getStatus(), expected.getStatus())
            && Objects.equals(actual.getAuthor(), expected.getAuthor())
            && Objects.equals(actual.getDesc(), expected.getDesc());
    }
    
    private boolean sameDescriptor(AiResourceVersion actual, AiResourceVersion expected) {
        return equivalentJson(actual.getStorage(), expected.getStorage());
    }
    
    private boolean equivalentVersionContent(AiResourceVersion actual,
        PreparedAgentVersionWrite expected) {
        try {
            AgentVersionStorageDescriptor descriptor =
                AgentVersionStorageDescriptorSerializer.deserialize(actual.getStorage());
            AgentVersionContent content = storageService.load(descriptor);
            AgentVersionContent expectedContent =
                AgentVersionContentSerializer.deserialize(expected.getBytes());
            return semanticValue(content).equals(semanticValue(expectedContent));
        } catch (Exception e) {
            return false;
        }
    }
    
    private void insertVersion(AiResourceVersion expected) throws NacosException {
        try {
            long id = versionPersistService.insert(expected);
            if (id <= 0) {
                throw new IllegalStateException("Migrated Agent Version insert returned no id");
            }
        } catch (RuntimeException e) {
            AiResourceVersion recovered = versionPersistService.find(expected.getNamespaceId(),
                expected.getName(), expected.getType(), expected.getVersion());
            if (recovered != null && sameVersionMetadata(recovered, expected)
                && sameDescriptor(recovered, expected)) {
                return;
            }
            if (e instanceof DuplicateKeyException || recovered != null) {
                throw conflict("Canonical Agent Version appeared during migration: "
                    + expected.getName() + '@' + expected.getVersion());
            }
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to insert migrated Agent Version: " + expected.getName() + '@'
                    + expected.getVersion(),
                e);
        }
    }
    
    private void updateVersion(AiResourceVersion actual, AiResourceVersion expected)
        throws NacosException {
        int storageUpdated = versionPersistService.updateStorageAndDesc(expected.getNamespaceId(),
            expected.getName(), expected.getType(), expected.getVersion(), expected.getStorage(),
            expected.getDesc());
        if (storageUpdated != 1) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to repair migrated Agent Version storage: " + expected.getName() + '@'
                    + expected.getVersion());
        }
        if (!Objects.equals(actual.getStatus(), expected.getStatus())
            && versionPersistService.updateStatus(expected.getNamespaceId(), expected.getName(),
                expected.getType(), expected.getVersion(), expected.getStatus()) != 1) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to repair migrated Agent Version status: " + expected.getName() + '@'
                    + expected.getVersion());
        }
    }
    
    private Map<String, AiResourceVersion> listVersionRows(String namespaceId, String agentName) {
        Map<String, AiResourceVersion> result =
            new LinkedHashMap<String, AiResourceVersion>();
        int pageNo = 1;
        int pages = 1;
        while (pageNo <= pages) {
            Page<AiResourceVersion> page = versionPersistService.list(namespaceId, agentName,
                Constants.Agent.RESOURCE_TYPE_AGENT, null, pageNo, VERSION_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw new IllegalStateException("Unable to scan canonical Agent Version rows");
            }
            pages = resolvePages(page, VERSION_PAGE_SIZE);
            for (AiResourceVersion version : page.getPageItems()) {
                if (version == null || result.put(version.getVersion(), version) != null) {
                    throw new IllegalStateException("Duplicate canonical Agent Version row");
                }
            }
            pageNo++;
        }
        return result;
    }
    
    private int resolvePages(Page<?> page, int pageSize) {
        if (page.getPagesAvailable() > 0) {
            return page.getPagesAvailable();
        }
        return (int) Math.ceil((double) page.getTotalCount() / pageSize);
    }
    
    private AiResource toResourceRow(Agent agent) {
        AgentResourceExt ext = new AgentResourceExt();
        ext.setSchemaVersion(AgentResourceExt.SCHEMA_VERSION);
        ext.setDisplayName(agent.getDisplayName());
        ext.setIconUrl(agent.getIconUrl());
        ext.setProvider(agent.getProvider());
        ext.setExtensions(agent.getExtensions());
        ext.setVersionCatalog(agent.getVersionCatalog());
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setOnlineCnt(agent.getVersionInfo().getOnlineCnt());
        versionInfo.setLabels(new HashMap<String, String>(agent.getVersionInfo().getLabels()));
        AiResource result = new AiResource();
        result.setNamespaceId(agent.getNamespaceId());
        result.setName(agent.getAgentName());
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setDesc(agent.getDescription());
        result.setStatus(agent.getStatus());
        result.setOwner(agent.getOwner());
        result.setScope(agent.getScope());
        result.setBizTags(JacksonUtils.toJson(Collections.emptyList()));
        result.setExt(AgentResourceExtSerializer.serialize(ext));
        result.setFrom(MIGRATION_RESOURCE_SOURCE);
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        result.setMetaVersion(1L);
        return result;
    }
    
    private AiResourceVersion toVersionRow(AgentVersionDetail version,
        AgentVersionStorageDescriptor descriptor) {
        AiResourceVersion result = new AiResourceVersion();
        result.setNamespaceId(version.getNamespaceId());
        result.setName(version.getAgentName());
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setVersion(version.getVersion());
        result.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        result.setAuthor("nacos");
        result.setDesc(version.getChangeDescription());
        result.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        return result;
    }
    
    private Agent copyAgent(Agent source) {
        Agent result = new Agent();
        result.setNamespaceId(source.getNamespaceId());
        result.setAgentName(source.getAgentName());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setIconUrl(source.getIconUrl());
        result.setProvider(source.getProvider());
        result.setTags(source.getTags() == null ? null : new ArrayList<String>(source.getTags()));
        result.setExtensions(source.getExtensions() == null ? null
            : new LinkedHashMap<String, Object>(source.getExtensions()));
        result.setStatus(source.getStatus());
        result.setOwner(source.getOwner());
        result.setScope(source.getScope());
        return result;
    }
    
    private List<String> protocolNames(List<AgentCallInterface> callInterfaces) {
        if (callInterfaces == null) {
            throw new IllegalArgumentException("Migrated Agent Version content is required");
        }
        List<String> result = new ArrayList<String>(callInterfaces.size());
        for (AgentCallInterface callInterface : callInterfaces) {
            result.add(callInterface.getProtocol());
        }
        return result;
    }
    
    private boolean equivalentJson(String left, String right) {
        try {
            return semanticValue(left).equals(semanticValue(right));
        } catch (RuntimeException e) {
            return false;
        }
    }
    
    private boolean equivalentJsonOrEmptyList(String left, String right) {
        String normalizedLeft = left == null ? "[]" : left;
        String normalizedRight = right == null ? "[]" : right;
        return equivalentJson(normalizedLeft, normalizedRight);
    }
    
    private Object semanticValue(String json) {
        return JacksonUtils.toObj(json, Object.class);
    }
    
    private Object semanticValue(Object value) {
        return JacksonUtils.toObj(JacksonUtils.toJson(value), Object.class);
    }
    
    private void deleteStorage(AiResourceVersion version) throws NacosException {
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(version.getStorage());
        storageService.delete(descriptor);
    }
    
    private void requireCurrentSource(BooleanSupplier sourceCurrent) throws NacosException {
        if (!sourceCurrent.getAsBoolean()) {
            throw new NacosException(NacosException.CONFLICT,
                "Historical A2A source changed during migration reconciliation");
        }
    }
    
    private NacosApiException conflict(String message) {
        return new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            message);
    }
    
    /**
     * Result of one complete historical-to-canonical reconciliation.
     */
    public enum Result {
        CREATED,
        REPAIRED,
        EQUIVALENT,
        EXTERNAL_EQUIVALENT
    }
    
    private static final class PreparedTarget {
        
        private final String namespaceId;
        
        private final String agentName;
        
        private final AiResource resource;
        
        private final Map<String, AiResourceVersion> versionRows;
        
        private final Map<String, PreparedAgentVersionWrite> prepared;
        
        private PreparedTarget(String namespaceId, String agentName, AiResource resource,
            Map<String, AiResourceVersion> versionRows,
            Map<String, PreparedAgentVersionWrite> prepared) {
            this.namespaceId = namespaceId;
            this.agentName = agentName;
            this.resource = resource;
            this.versionRows = versionRows;
            this.prepared = prepared;
        }
    }
}
