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

import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecQueryResult;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.AgentSpecContentDigestUtils;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared AgentSpec projections on the existing numbered search, JSON and conditional-get contracts. */
@Service
public class HubAgentSpecReadAdapter {
    
    private static final HubResourceType TYPE = HubResourceType.AGENTSPEC;
    
    private final HubResourcePublicationService publications;
    
    private final HubResourceDirectoryService directory;
    
    private final HubResourceDiscoveryProjector projector;
    
    private final HubResourceContentService contents;
    
    private final AiResourceManager manager;
    
    private final AgentSpecStorageReader storage;
    
    private final HubResourceDownloadRecorder downloads;
    
    public HubAgentSpecReadAdapter(HubResourcePublicationService publications,
        HubResourceDirectoryService directory, HubResourceDiscoveryProjector projector,
        HubResourceContentService contents, AiResourceManager manager,
        AgentSpecStorageReader storage,
        HubResourceDownloadRecorder downloads) {
        this.publications = publications;
        this.directory = directory;
        this.projector = projector;
        this.contents = contents;
        this.manager = manager;
        this.storage = storage;
        this.downloads = downloads;
    }
    
    /** Existing client search keeps keyword and tagsAll, with Hub-only pagination. */
    public Page<AgentSpecBasicInfo> search(String keyword, List<String> tagsAll, int pageNo,
        int pageSize) throws NacosException {
        Page<HubResourceDirectoryService.Entry> entries = directory.list(TYPE, null, tagsAll,
            null, "download_count", pageNo, pageSize,
            entry -> keyword == null || entry.reference().getSourceName().contains(keyword));
        List<AgentSpecBasicInfo> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : entries.getPageItems()) {
            AgentSpecBasicInfo item = new AgentSpecBasicInfo();
            fillBasic(item, entry);
            items.add(item);
        }
        return AiResourceManager.buildPageResult(items, entries, pageNo);
    }
    
    /** Existing management list uses the selected Hub sort, never source popularity or dates. */
    public Page<AgentSpecSummary> list(String name, String search, String owner, String scope,
        String orderBy,
        int pageNo, int pageSize) throws NacosException {
        Pattern namePattern = StringUtils.isBlank(name) ? null
            : Pattern.compile(".*" + Pattern.quote(name).replace("*", "\\E.*\\Q")
                .replace("%", "\\E.*\\Q") + ".*", Pattern.DOTALL);
        Page<HubResourceDirectoryService.Entry> entries = directory.list(TYPE, null,
            Collections.emptyList(), owner, orderBy, pageNo, pageSize,
            entry -> (StringUtils.isBlank(scope) || "PUBLIC".equalsIgnoreCase(scope))
                && (namePattern == null || ("accurate".equalsIgnoreCase(search)
                    ? entry.reference().getSourceName().equals(name)
                    : namePattern.matcher(entry.reference().getSourceName()).matches())));
        List<AgentSpecSummary> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : entries.getPageItems()) {
            AgentSpecSummary item = new AgentSpecSummary();
            fillSummary(item, entry);
            items.add(item);
        }
        return AiResourceManager.buildPageResult(items, entries, pageNo);
    }
    
    /** Management metadata exposes only the one currently shared version. */
    public AgentSpecMeta getMeta(String id) throws NacosException {
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> {
                HubResourceDirectoryService.Entry entry =
                    new HubResourceDirectoryService.Entry(hub, source, reference);
                AgentSpecMeta result = new AgentSpecMeta();
                fillSummary(result, entry);
                AiResourceVersion row = manager.findVersion(AiConstants.Hub.NAMESPACE_ID,
                    hub.getName(), TYPE.resourceType(), reference.getSourceVersion());
                AgentSpecMeta.AgentSpecVersionSummary version =
                    new AgentSpecMeta.AgentSpecVersionSummary();
                version.setVersion(reference.getSourceVersion());
                version.setStatus("online");
                AiResourceVersion sourceVersion = requireVersion(reference);
                version.setAuthor(sourceVersion.getAuthor());
                version.setPublishPipelineInfo(sourceVersion.getPublishPipelineInfo());
                version.setDescription(sourceVersion.getDesc());
                version.setDownloadCount(row == null ? 0L : row.getDownloadCount());
                version.setCreateTime(
                    sourceVersion.getGmtCreate() == null ? null
                        : sourceVersion.getGmtCreate().getTime());
                version.setUpdateTime(
                    sourceVersion.getGmtModified() == null ? null
                        : sourceVersion.getGmtModified().getTime());
                result.setVersions(Collections.singletonList(version));
                return result;
            });
    }
    
    /** Read a full package for management viewing while keeping the opaque management identity. */
    public AgentSpec getVersion(String id, String version) throws NacosException {
        AgentSpec result = contents.readAgentSpec(HubResourceNameUtils.parseEntryId(id), version);
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setName(id);
        return result;
    }
    
    /** Preserve the existing metadata-only route without loading referenced resource bodies. */
    public AgentSpec getVersionMeta(String id, String version) throws NacosException {
        return publications.readPinned(TYPE, HubResourceNameUtils.parseEntryId(id), version,
            (source, reference) -> {
                AiResourceVersion row = requireVersion(reference);
                AgentSpec result = storage.readMeta(reference.getSourceNamespaceId(),
                    reference.getSourceName(), reference.getSourceVersion(), row.getStorage());
                result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                result.setName(id);
                return result;
            });
    }
    
    /**
     * Existing AgentSpec label precedence is retained. The Hub entry has only its current latest
     * label; unknown labels fall back to that latest just as ordinary AgentSpec queries do.
     */
    public AgentSpecQueryResult query(String id, String version, String label, String clientMd5)
        throws NacosException {
        long entryId = HubResourceNameUtils.parseEntryId(id);
        String selectedVersion = StringUtils.isBlank(label) ? version : null;
        AgentSpecQueryResult result = publications.inspectPinned(TYPE, entryId, selectedVersion,
            (hub, source, reference) -> {
                AiResourceVersion row = requireVersion(reference);
                String storedMd5 = readMd5(row.getStorage());
                if (StringUtils.isNotBlank(storedMd5) && storedMd5.equals(clientMd5)) {
                    return AgentSpecQueryResult.notModified(storedMd5,
                        reference.getSourceVersion());
                }
                AgentSpec content = contents.readAgentSpec(entryId, reference.getSourceVersion());
                String digest = StringUtils.isNotBlank(storedMd5) ? storedMd5
                    : AgentSpecContentDigestUtils.computeContentMd5(content);
                content.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                return new AgentSpecQueryResult(content, digest, reference.getSourceVersion());
            });
        if (!result.isNotModified()) {
            downloads.record(TYPE, entryId, result.getResolvedVersion());
        }
        return result;
    }
    
    private AiResourceVersion requireVersion(HubResourceReference reference) throws NacosException {
        AiResourceVersion row = manager.findVersion(reference.getSourceNamespaceId(),
            reference.getSourceName(), TYPE.resourceType(), reference.getSourceVersion());
        if (row == null || !Objects.equals(row.getId(), reference.getSourceVersionId())
            || !"online".equals(row.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Shared AgentSpec content is unavailable");
        }
        return row;
    }
    
    private String readMd5(String descriptor) {
        try {
            Map<?, ?> storage = JacksonUtils.toObj(descriptor, Map.class);
            Object md5 = storage == null ? null : storage.get("contentMd5");
            return md5 instanceof String ? (String) md5 : null;
        } catch (RuntimeException missingDigest) {
            return null;
        }
    }
    
    private void fillBasic(AgentSpecBasicInfo result, HubResourceDirectoryService.Entry entry)
        throws NacosException {
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setName(Long.toString(entry.hub().getId()));
        result.setDescription(entry.source().getDesc());
        result.setUpdateTime(entry.source().getGmtModified() == null ? null
            : entry.source().getGmtModified().getTime());
        result.setHub(projector.project(TYPE, entry));
    }
    
    private void fillSummary(AgentSpecSummary result, HubResourceDirectoryService.Entry entry)
        throws NacosException {
        fillBasic(result, entry);
        result.setEnable(true);
        result.setOwner(entry.source().getOwner());
        result.setScope("PUBLIC");
        result.setBizTags(entry.source().getBizTags());
        result.setFrom("hub");
        result.setLabels(
            HubResourceReadMetadata.labels(entry.source(), entry.reference().getSourceVersion()));
        result.setOnlineCnt(1);
        result.setDownloadCount(entry.hub().getDownloadCount());
        result.setWritable(false);
    }
}
