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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.ResourceContentEncoder;
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Projects guarded shared reads onto the existing Skill management contracts. */
@Service
public class HubSkillManagementAdapter {
    
    private final HubSkillReadService reads;
    
    private final HubSkillDirectoryService directory;
    
    public HubSkillManagementAdapter(HubSkillReadService reads,
        HubSkillDirectoryService directory) {
        this.reads = reads;
        this.directory = directory;
    }
    
    /** Apply existing management filters before the directory sorts and paginates. */
    public Page<SkillSummary> list(String name, String search, String owner, String scope,
        String bizTag, int pageNo, int pageSize) throws NacosException {
        return list(name, search, null, owner, scope, bizTag, pageNo, pageSize);
    }
    
    /** Reuse the ordinary Skill list sort option for shared management reads. */
    public Page<SkillSummary> list(String name, String search, String orderBy, String owner,
        String scope, String bizTag, int pageNo, int pageSize) throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        request.setOrderBy(orderBy);
        Page<HubSkillSummary> shared = directory.list(request,
            (publication,
                source) -> (StringUtils.isBlank(name)
                    || (Constants.Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)
                        ? publication.getSourceName().equals(name)
                        : contains(publication.getSourceName(), name)))
                    && (StringUtils.isBlank(owner) || owner.equals(source.getOwner()))
                    && (StringUtils.isBlank(scope)
                        || VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(scope))
                    && (StringUtils.isBlank(bizTag) || contains(source.getBizTags(), bizTag)));
        Page<SkillSummary> result = new Page<>();
        result.setPageNumber(shared.getPageNumber());
        result.setTotalCount(shared.getTotalCount());
        result.setPagesAvailable(shared.getPagesAvailable());
        ArrayList<SkillSummary> items = new ArrayList<>();
        for (HubSkillSummary entry : shared.getPageItems()) {
            SkillSummary item = new SkillSummary();
            fillSummary(item, entry);
            items.add(item);
        }
        result.setPageItems(items);
        return result;
    }
    
    public SkillMeta getDetail(String name) throws NacosException {
        HubSkillDetail shared = reads.getDetail(parseEntryId(name));
        SkillMeta result = new SkillMeta();
        fillSummary(result, shared);
        Map<String, String> labels = new LinkedHashMap<>();
        if (shared.getVersionLabels() != null) {
            shared.getVersionLabels()
                .forEach(label -> labels.put(label, shared.getSharedVersion()));
        }
        result.setLabels(labels);
        SkillMeta.SkillVersionSummary version = new SkillMeta.SkillVersionSummary();
        version.setVersion(shared.getSharedVersion());
        version.setStatus(shared.getVersionStatus());
        version.setAuthor(shared.getAuthor());
        version.setCommitMsg(shared.getCommitMsg());
        version.setCreateTime(shared.getSharedAt());
        version.setUpdateTime(shared.getSharedAt());
        version.setDownloadCount(shared.getVersionDownloadCount());
        result.setVersions(Collections.singletonList(version));
        result.setSourceReadable(shared.isSourceReadable());
        if (shared.isSourceReadable()) {
            result.setSourceNamespaceId(shared.getSourceNamespaceId());
            result.setSourceName(shared.getSourceName());
        }
        return result;
    }
    
    public Skill getVersion(String name, String version) throws NacosException {
        HubSkillContent content = reads.getContent(parseEntryId(name), version);
        return toSkill(name, content);
    }
    
    private Skill toSkill(String name, HubSkillContent content) {
        Skill result = new Skill();
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setName(name);
        result.setSkillMd(content.getSkillMd());
        result.setDescription(SkillZipParser.parseYamlFrontMatterFromMarkdown(content.getSkillMd())
            .get("description"));
        Map<String, SkillResource> resources = new LinkedHashMap<>();
        content.getResource().forEach((path, original) -> {
            SkillResource resource = new SkillResource();
            resource.setName(original.getName());
            resource.setType(original.getType());
            resource.setContent(original.getContent());
            if (ResourceContentEncoder.isBinary(path)) {
                resource
                    .setMetadata(Collections.singletonMap(ResourceContentEncoder.METADATA_ENCODING,
                        ResourceContentEncoder.METADATA_ENCODING_BASE64));
            }
            resources.put(SkillUtils.generateResourceId(resource.getType(), resource.getName()),
                resource);
        });
        result.setResource(resources);
        return result;
    }
    
    /** Preserve the source package bytes and name; the read service counts exactly one transfer. */
    public SkillDownload download(String name, String version) throws NacosException {
        HubSkillDownload shared = reads.download(parseEntryId(name), version);
        SkillDownload result = new SkillDownload();
        result.setHubEntryId(shared.getHubEntryId());
        result.setDisplayName(shared.getDisplayName());
        result.setResolvedVersion(shared.getSharedVersion());
        result.setZipBytes(shared.getZipBytes());
        return result;
    }
    
    /** Compatibility for internal callers of the existing object-based download operation. */
    public Skill downloadAsSkill(String name, String version) throws NacosException {
        long id = parseEntryId(name);
        HubSkillContent content = reads.getContent(id, version);
        Skill result = toSkill(name, content);
        SkillDownload downloaded = download(name, content.getSharedVersion());
        result.setName(downloaded.getDisplayName());
        return result;
    }
    
    private void fillSummary(SkillSummary result, HubSkillSummary entry) {
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setName(Long.toString(entry.getHubEntryId()));
        result.setHubEntryId(entry.getHubEntryId());
        result.setDiscoverySource("HUB");
        result.setDisplayName(entry.getDisplayName());
        result.setSourceDisplayName(entry.getSourceDisplayName());
        result.setDescription(entry.getDescription());
        result.setVersion(entry.getSharedVersion());
        result.setUpdateTime(entry.getSharedAt());
        result.setDownloadCount(entry.getDownloadCount());
        result.setOwner(entry.getAuthor());
        result.setBizTags(JacksonUtils.toJson(entry.getTags()));
        result.setEnable(true);
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        result.setWritable(false);
        result.setOnlineCnt(1);
    }
    
    private static boolean contains(String value, String query) {
        return value != null
            && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    
    private long parseEntryId(String name) throws NacosApiException {
        try {
            long id = Long.parseLong(name);
            if (id > 0 && Long.toString(id).equals(name)) {
                return id;
            }
        } catch (NumberFormatException invalid) {
            // Display names and source IDs cannot identify a shared entry.
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Shared Skill name must be the opaque positive hubEntryId returned by list");
    }
}
