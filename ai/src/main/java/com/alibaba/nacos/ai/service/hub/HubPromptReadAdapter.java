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
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
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
import java.util.Objects;
import java.util.regex.Pattern;

/** Fixed-version Prompt projections retaining the existing template, variable and query contracts. */
@Service
public class HubPromptReadAdapter {
    
    private static final HubResourceType TYPE = HubResourceType.PROMPT;
    
    private final HubResourcePublicationService publications;
    private final HubResourceDirectoryService directory;
    private final HubResourceDiscoveryProjector projector;
    private final HubResourceContentService contents;
    private final AiResourceManager manager;
    private final HubResourceDownloadRecorder downloads;
    
    public HubPromptReadAdapter(HubResourcePublicationService publications,
        HubResourceDirectoryService directory, HubResourceDiscoveryProjector projector,
        HubResourceContentService contents, AiResourceManager manager,
        HubResourceDownloadRecorder downloads) {
        this.publications = publications;
        this.directory = directory;
        this.projector = projector;
        this.contents = contents;
        this.manager = manager;
        this.downloads = downloads;
    }
    
    /** Existing text search uses metadata and tags, never template execution or source READ. */
    public Page<PromptMetaSummary> search(String query, List<String> tagsAll, int pageNo,
        int pageSize)
        throws NacosException {
        return summaries(
            directory.list(TYPE, query, tagsAll, null, "download_count", pageNo, pageSize), pageNo);
    }
    
    /** Preserve management exact/blur name and raw business-tag substring filters before paging. */
    public Page<PromptMetaSummary> list(String name, String search, String bizTags, int pageNo,
        int pageSize)
        throws NacosException {
        Pattern pattern = StringUtils.isBlank(name) ? null
            : Pattern.compile(".*" + Pattern.quote(name).replace("*", "\\E.*\\Q")
                .replace("%", "\\E.*\\Q") + ".*", Pattern.DOTALL);
        return summaries(directory.list(TYPE, null, Collections.emptyList(), null, "gmt_modified",
            pageNo, pageSize,
            entry -> (pattern == null || ("blur".equalsIgnoreCase(search)
                ? pattern.matcher(entry.reference().getSourceName()).matches()
                : name.equals(entry.reference().getSourceName())))
                && (StringUtils.isBlank(bizTags) || (entry.source().getBizTags() != null
                    && entry.source().getBizTags().contains(bizTags)))),
            pageNo);
    }
    
    /** Governance view contains only the current shared version and sanitized shared metadata. */
    public PromptMetaInfo getMeta(String id) throws NacosException {
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> {
                HubResourceDirectoryService.Entry entry =
                    new HubResourceDirectoryService.Entry(hub, source, reference);
                PromptMetaInfo result = new PromptMetaInfo();
                fillSummary(result, entry);
                result.setVersions(Collections.singletonList(reference.getSourceVersion()));
                PromptVersionSummary version = new PromptVersionSummary();
                fillVersion(version, entry);
                result.setVersionDetails(Collections.singletonList(version));
                return result;
            });
    }
    
    /** Preserve explicit version over label, with unknown labels falling back to the only shared latest. */
    public PromptVersionInfo query(String id, String version, String label) throws NacosException {
        return getVersion(id, StringUtils.isNotBlank(version) ? version : null);
    }
    
    /** Read the exact saved provider and expose no source storage, ID or other source version. */
    public PromptVersionInfo getVersion(String id, String version) throws NacosException {
        long entryId = HubResourceNameUtils.parseEntryId(id);
        return publications.inspectPinned(TYPE, entryId, version, (hub, source, reference) -> {
            PromptVersionInfo body = contents.readPrompt(entryId, reference.getSourceVersion());
            PromptVersionInfo result = new PromptVersionInfo();
            result.setTemplate(body.getTemplate());
            result.setMd5(body.getMd5());
            result.setVariables(body.getVariables());
            fillVersion(result, new HubResourceDirectoryService.Entry(hub, source, reference));
            return result;
        });
    }
    
    /** Count successful explicit downloads only, preserving ordinary Prompt query metric semantics. */
    public PromptVersionInfo download(String id, String version) throws NacosException {
        PromptVersionInfo result = getVersion(id, version);
        downloads.record(TYPE, HubResourceNameUtils.parseEntryId(id), result.getVersion());
        return result;
    }
    
    /** A shared entry has one effective version, independently paginated from source history. */
    public Page<PromptVersionSummary> versions(String id, int pageNo, int pageSize)
        throws NacosException {
        if (pageNo < 1 || pageSize < 1 || pageSize > 500) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid Prompt version pagination");
        }
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> {
                PromptVersionSummary summary = new PromptVersionSummary();
                fillVersion(summary, new HubResourceDirectoryService.Entry(hub, source, reference));
                Page<PromptVersionSummary> page = new Page<>();
                page.setPageNumber(pageNo);
                page.setPagesAvailable(1);
                page.setTotalCount(1);
                page.setPageItems(
                    pageNo == 1 ? Collections.singletonList(summary) : Collections.emptyList());
                return page;
            });
    }
    
    private Page<PromptMetaSummary> summaries(Page<HubResourceDirectoryService.Entry> page,
        int pageNo)
        throws NacosException {
        List<PromptMetaSummary> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : page.getPageItems()) {
            PromptMetaSummary item = new PromptMetaSummary();
            fillSummary(item, entry);
            items.add(item);
        }
        return AiResourceManager.buildPageResult(items, page, pageNo);
    }
    
    private void fillSummary(PromptMetaSummary result, HubResourceDirectoryService.Entry entry)
        throws NacosException {
        result.setPromptKey(entry.hub().getId().toString());
        result.setDescription(entry.source().getDesc());
        result.setBizTagsStr(entry.source().getBizTags());
        result.setBizTags(tags(entry.source().getBizTags()));
        result.setLatestVersion(entry.reference().getSourceVersion());
        result.setLabels(
            HubResourceReadMetadata.labels(entry.source(), entry.reference().getSourceVersion()));
        result.setOnlineCnt(1);
        result.setDownloadCount(entry.hub().getDownloadCount());
        result.setGmtModified(
            entry.source().getGmtModified() == null ? null
                : entry.source().getGmtModified().getTime());
        result.setHub(projector.project(TYPE, entry));
    }
    
    private void fillVersion(PromptVersionSummary result, HubResourceDirectoryService.Entry entry)
        throws NacosException {
        AiResourceVersion row = manager.findVersion(entry.reference().getSourceNamespaceId(),
            entry.reference().getSourceName(), TYPE.resourceType(),
            entry.reference().getSourceVersion());
        if (row == null || !Objects.equals(row.getId(), entry.reference().getSourceVersionId())
            || !"online".equals(row.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Shared Prompt version is unavailable");
        }
        result.setPromptKey(entry.hub().getId().toString());
        result.setVersion(entry.reference().getSourceVersion());
        result.setStatus("online");
        result.setCommitMsg(row.getDesc());
        result.setPublishPipelineInfo(row.getPublishPipelineInfo());
        result.setSrcUser(row.getAuthor());
        result.setGmtModified(
            row.getGmtModified() == null ? null : row.getGmtModified().getTime());
        result.setHub(projector.project(TYPE, entry));
        AiResourceVersion hubVersion =
            manager.findVersion("__nacos_ai_hub__", entry.hub().getName(),
                TYPE.resourceType(), entry.reference().getSourceVersion());
        result.setDownloadCount(hubVersion == null ? 0L : hubVersion.getDownloadCount());
    }
    
    private List<String> tags(String json) {
        try {
            return json == null ? Collections.emptyList() : JacksonUtils.toObj(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        } catch (RuntimeException invalid) {
            return Collections.emptyList();
        }
    }
}
