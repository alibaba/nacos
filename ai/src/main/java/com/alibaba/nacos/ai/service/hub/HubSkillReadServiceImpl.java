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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.notify.NotifyCenter;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only Hub service. The last recheck is deliberately performed after content access. */
@Service
public class HubSkillReadServiceImpl implements HubSkillReadService {
    
    private final HubSkillDirectoryService directory;
    private final HubSkillReadAccessService access;
    private final HubSkillSourceContentReader contentReader;
    
    public HubSkillReadServiceImpl(HubSkillDirectoryService directory,
        HubSkillReadAccessService access, HubSkillSourceContentReader contentReader) {
        this.directory = directory;
        this.access = access;
        this.contentReader = contentReader;
    }
    
    @Override
    public Page<HubSkillSummary> list(HubSkillListRequest request) throws NacosException {
        return directory.list(request);
    }
    
    @Override
    public HubSkillDetail getDetail(Long hubEntryId) throws NacosException {
        HubConfig.requireEnabled();
        ResolvedHubSkillReference publication = access.resolveReadable(hubEntryId);
        HubEligibleSkillVersion eligible = access.requireReadableVersion(publication);
        AiResource source = eligible.getSourceMeta();
        AiResourceVersion version = eligible.getSourceVersion();
        HubSkillSummary summary = directory.toSummary(publication, source);
        HubSkillDetail detail = new HubSkillDetail();
        detail.setHubEntryId(summary.getHubEntryId());
        detail.setDisplayName(summary.getDisplayName());
        detail.setSourceDisplayName(summary.getSourceDisplayName());
        detail.setDescription(summary.getDescription());
        detail.setAuthor(version.getAuthor());
        detail.setVersionStatus(version.getStatus());
        detail.setVersionDownloadCount(version.getDownloadCount() == null ? 0L
            : Math.max(0L, version.getDownloadCount()));
        detail.setCommitMsg(version.getDesc());
        List<String> labels = new ArrayList<>();
        AiResourceManager.requireVersionInfo(source).getLabels().forEach((label, target) -> {
            if (publication.getSourceVersion().equals(target)) {
                labels.add(label);
            }
        });
        Collections.sort(labels);
        detail.setVersionLabels(labels);
        detail.setSharedVersion(summary.getSharedVersion());
        detail.setTags(summary.getTags());
        detail.setSharedAt(summary.getSharedAt());
        detail.setDownloadCount(summary.getDownloadCount());
        boolean sourceReadable = access.canReadSourceDetail(publication);
        detail.setSourceReadable(sourceReadable);
        if (sourceReadable) {
            detail.setSourceNamespaceId(publication.getSourceNamespaceId());
            detail.setSourceName(publication.getSourceName());
        }
        access.requireStillReadable(publication);
        return detail;
    }
    
    @Override
    public HubSkillDownload download(Long hubEntryId) throws NacosException {
        HubConfig.requireEnabled();
        ResolvedHubSkillReference publication = access.resolveReadable(hubEntryId);
        HubSkillDownload result = contentReader.download(publication);
        access.requireStillReadable(publication);
        NotifyCenter.publishEvent(new SkillDownloadEvent(publication.getSourceNamespaceId(),
            publication.getSourceName(), AiResourceConstants.RESOURCE_TYPE_SKILL,
            publication.getSourceVersion()));
        return result;
    }
    
    @Override
    public HubSkillContent getContent(Long hubEntryId) throws NacosException {
        HubConfig.requireEnabled();
        ResolvedHubSkillReference publication = access.resolveReadable(hubEntryId);
        HubSkillContent result = contentReader.load(publication);
        access.requireStillReadable(publication);
        return result;
    }
}
