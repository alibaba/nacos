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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** S3 orchestrator. Import and direct copy share exactly one target draft writer. */
@Service
public class HubSkillTransferService {
    
    private final NamespaceOperationService namespaces;
    private final HubSkillTransferPermissionGuard permissions;
    private final HubSkillReadAccessService readAccess;
    private final HubSkillTransferSourceValidator sources;
    private final HubSkillTransferFileService files;
    private final HubSkillDraftTransferWriter writer;
    
    public HubSkillTransferService(NamespaceOperationService namespaces,
        HubSkillTransferPermissionGuard permissions,
        HubSkillReadAccessService readAccess,
        HubSkillTransferSourceValidator sources,
        HubSkillTransferFileService files,
        HubSkillDraftTransferWriter writer) {
        this.namespaces = namespaces;
        this.permissions = permissions;
        this.readAccess = readAccess;
        this.sources = sources;
        this.files = files;
        this.writer = writer;
    }
    
    /** Import just the currently shared, immutable Hub version; source READ not required. */
    public HubSkillTransferResult importFromHub(HubSkillTransferRequest request)
        throws NacosException {
        HubConfig.requireEnabled();
        request = snapshot(request);
        String targetVersion = HubSkillTransferRequestValidator.validate(request, true);
        final String targetNamespaceId = request.getTargetNamespaceId();
        final String targetName = request.getTargetName();
        requireTarget(request);
        writer.requireAvailable(targetNamespaceId, targetName, targetVersion);
        ResolvedHubSkillReference publication = readAccess.resolveReadable(request.getHubEntryId());
        AiResource meta = sources.lookupDirectSource(publication.getSourceNamespaceId(),
            publication.getSourceName());
        HubSkillTransferSourceSnapshot source = sources.qualify(meta,
            publication.getSourceVersion(), publication.getHubEntryId());
        if (source.getResourceId() != publication.getSourceResourceId()
            || source.getVersionId() != publication.getSourceVersionId()) {
            readAccess.requireStillReadable(publication);
            throw unavailable();
        }
        AiResourceVersion sourceRow = sources.requireStillLive(source);
        HubSkillTransferFileService.Files contents = files.load(source, sourceRow,
            request.getTargetName(), targetVersion);
        readAccess.requireStillReadable(publication);
        return writer.create(source, request.getTargetNamespaceId(), request.getTargetName(),
            targetVersion, contents, origin(source), () -> {
                permissions.requireTargetWrite(targetNamespaceId, targetName);
                requireTargetNamespace(targetNamespaceId);
                readAccess.requireStillReadable(publication);
                sources.requireStillLive(source);
            });
    }
    
    /** Copy an explicit online version without any Hub publication. Source READ is mandatory. */
    public HubSkillTransferResult copyDirect(HubSkillTransferRequest request)
        throws NacosException {
        request = snapshot(request);
        String targetVersion = HubSkillTransferRequestValidator.validate(request, false);
        final String targetNamespaceId = request.getTargetNamespaceId();
        final String targetName = request.getTargetName();
        requireTarget(request);
        writer.requireAvailable(targetNamespaceId, targetName, targetVersion);
        AiResource sourceMeta = sources.lookupDirectSource(request.getSourceNamespaceId(),
            request.getSourceName());
        permissions.requireDirectSourceRead(sourceMeta);
        HubSkillTransferSourceSnapshot source = sources.qualify(sourceMeta,
            request.getSourceVersion(), null);
        AiResourceVersion sourceRow = sources.requireStillLive(source);
        HubSkillTransferFileService.Files contents = files.load(source, sourceRow,
            request.getTargetName(), targetVersion);
        // A direct copy does not acquire a Hub grant: always recheck source READ.
        permissions.requireDirectSourceRead(sources.lookupDirectSource(
            source.getNamespaceId(), source.getName()));
        return writer.create(source, request.getTargetNamespaceId(), request.getTargetName(),
            targetVersion, contents, origin(source), () -> {
                permissions.requireTargetWrite(targetNamespaceId, targetName);
                requireTargetNamespace(targetNamespaceId);
                AiResource current = sources.lookupDirectSource(source.getNamespaceId(),
                    source.getName());
                if (current == null || !source.getNamespaceId().equals(current.getNamespaceId())
                    || source.getResourceId() != current.getId()) {
                    throw unavailable();
                }
                permissions.requireDirectSourceRead(current);
                sources.requireStillLive(source);
            });
    }
    
    private void requireTarget(HubSkillTransferRequest request) throws NacosException {
        // Target authorization precedes any source lookup or file IO.
        permissions.requireTargetWrite(request.getTargetNamespaceId(), request.getTargetName());
        requireTargetNamespace(request.getTargetNamespaceId());
    }
    
    private void requireTargetNamespace(String namespaceId) throws NacosException {
        namespaces.getNamespace(namespaceId); // checks CUSTOM/global; Hub is rejected by validator
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Skill transfer source unavailable");
    }
    
    private HubSkillTransferRequest snapshot(HubSkillTransferRequest original) {
        if (original == null) {
            return null;
        }
        HubSkillTransferRequest result = new HubSkillTransferRequest();
        result.setHubEntryId(original.getHubEntryId());
        result.setSourceNamespaceId(original.getSourceNamespaceId());
        result.setSourceName(original.getSourceName());
        result.setSourceVersion(original.getSourceVersion());
        result.setTargetNamespaceId(original.getTargetNamespaceId());
        result.setTargetName(original.getTargetName());
        result.setTargetVersion(original.getTargetVersion());
        return result;
    }
    
    private Map<String, Object> origin(HubSkillTransferSourceSnapshot source) {
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("schemaVersion", 1);
        origin.put("sourceNamespaceId", source.getNamespaceId());
        origin.put("sourceResourceId", source.getResourceId());
        origin.put("sourceName", source.getName());
        origin.put("sourceVersionId", source.getVersionId());
        origin.put("sourceVersion", source.getVersion());
        origin.put("hubEntryId", source.getHubEntryId());
        origin.put("importedAt", Instant.now().toString());
        return origin;
    }
}
