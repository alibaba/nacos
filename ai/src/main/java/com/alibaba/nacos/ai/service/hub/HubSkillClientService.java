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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.form.search.client.AiResourcePageSearchForm;
import com.alibaba.nacos.ai.form.skills.client.SkillQueryForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Shared namespace adapter for the existing Skill Client search and download routes. */
@Service
public class HubSkillClientService {
    
    private final HubSkillReadService reads;
    
    private final HubSkillReadAccessService access;
    
    private final HubSkillSourceContentReader contents;
    
    public HubSkillClientService(HubSkillReadService reads, HubSkillReadAccessService access,
        HubSkillSourceContentReader contents) {
        this.reads = reads;
        this.access = access;
        this.contents = contents;
    }
    
    /** Search active shares without exposing reference storage or source namespace IDs. */
    public Page<SkillBasicInfo> search(AiResourcePageSearchForm form, int pageNo, int pageSize)
        throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery(form.getQuery());
        request.setTagsAll(form.getTagsAll());
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        // Runtime discovery retains its published popularity ordering.
        request.setOrderBy("download_count");
        Page<HubSkillSummary> shared = reads.list(request);
        List<SkillBasicInfo> items = new ArrayList<>();
        for (HubSkillSummary entry : shared.getPageItems()) {
            SkillBasicInfo item = new SkillBasicInfo();
            item.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
            // Display names are not unique across shares. The opaque handle is downloadable.
            item.setName(Long.toString(entry.getHubEntryId()));
            item.setHubEntryId(entry.getHubEntryId());
            item.setDiscoverySource("HUB");
            item.setDisplayName(entry.getDisplayName());
            item.setSourceDisplayName(entry.getSourceDisplayName());
            item.setDescription(entry.getDescription());
            item.setVersion(entry.getSharedVersion());
            item.setDownloadCount(entry.getDownloadCount());
            item.setUpdateTime(entry.getSharedAt());
            items.add(item);
        }
        Page<SkillBasicInfo> result = new Page<>();
        result.setPageNumber(shared.getPageNumber());
        result.setTotalCount(shared.getTotalCount());
        result.setPagesAvailable(shared.getPagesAvailable());
        result.setPageItems(items);
        return result;
    }
    
    /** Download only the currently shared pinned version, counting actual Hub transfers. */
    public ResponseEntity<byte[]> download(SkillQueryForm form) throws NacosException {
        long entryId = parseEntryId(form.getName());
        ResolvedHubSkillReference publication = access.resolveReadable(entryId);
        if ((StringUtils.isNotBlank(form.getVersion())
            && !publication.getSourceVersion().equals(form.getVersion()))
            || (StringUtils.isBlank(form.getVersion()) && StringUtils.isNotBlank(form.getLabel())
                && !"latest".equals(form.getLabel()))) {
            throw unavailable();
        }
        String md5 = contents.readContentMd5(publication);
        if (StringUtils.isNotBlank(md5) && md5.equals(form.getMd5())) {
            access.requireStillReadable(publication);
            return ResponseEntity.status(304).eTag(md5)
                .header(Constants.Skills.HEADER_SKILL_MD5, md5).build();
        }
        HubSkillDownload download = contents.download(publication);
        access.requireStillReadable(publication);
        if (StringUtils.isBlank(md5)) {
            // Historical versions must transfer once before conditional responses are permitted.
            md5 = contents.backfillContentMd5(publication);
            access.requireStillReadable(publication);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/zip");
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(download.getDisplayName() + ".zip", StandardCharsets.UTF_8).build());
        headers.set(Constants.Skills.HEADER_SKILL_RESOLVED_VERSION, publication.getSourceVersion());
        headers.set("X-Nacos-Hub-Entry-Id", Long.toString(entryId));
        if (StringUtils.isNotBlank(md5)) {
            headers.setETag("\"" + md5 + "\"");
            headers.set(Constants.Skills.HEADER_SKILL_MD5, md5);
        }
        NotifyCenter.publishEvent(new SkillDownloadEvent(AiConstants.Hub.NAMESPACE_ID,
            publication.getInternalName(), AiResourceConstants.RESOURCE_TYPE_SKILL,
            publication.getSourceVersion()));
        return ResponseEntity.ok().headers(headers).body(download.getZipBytes());
    }
    
    private long parseEntryId(String name) throws NacosApiException {
        try {
            long id = Long.parseLong(name);
            if (id > 0 && Long.toString(id).equals(name)) {
                return id;
            }
        } catch (NumberFormatException invalid) {
            // Display names and source IDs cannot identify an unambiguous shared entry.
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Shared Skill name must be the opaque positive hubEntryId returned by search");
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Shared Skill version not found or unavailable");
    }
}
