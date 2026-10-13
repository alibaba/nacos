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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** Exact source-version validation, including PRIVATE-but-readable direct copies. */
@Service
public class HubSkillTransferSourceValidator {
    
    private final AiResourceManager manager;
    
    public HubSkillTransferSourceValidator(AiResourceManager manager) {
        this.manager = manager;
    }
    
    /** The caller must check independent READ before using the returned version for copy. */
    public AiResource lookupDirectSource(String namespaceId, String name) throws NacosException {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(name)) {
            throw unavailable();
        }
        AiResource meta;
        try {
            meta = manager.findMeta(namespaceId, name, AiResourceConstants.RESOURCE_TYPE_SKILL);
        } catch (RuntimeException storageFailure) {
            throw storageUnavailable();
        }
        if (meta == null || meta.getId() == null || meta.getId() <= 0
            || !Objects.equals(meta.getNamespaceId(), namespaceId)
            || !Objects.equals(meta.getName(), name)
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(meta.getType())) {
            throw unavailable();
        }
        return meta;
    }
    
    /** Require online version but not PUBLIC; DIRECT copy permission is separate. */
    public HubSkillTransferSourceSnapshot qualify(AiResource original, String exactVersion,
        Long hubEntryId) throws NacosException {
        if (original == null || original.getId() == null || original.getId() <= 0
            || StringUtils.isBlank(exactVersion) || "latest".equalsIgnoreCase(exactVersion)) {
            throw unavailable();
        }
        AiResource meta = lookupDirectSource(original.getNamespaceId(), original.getName());
        if (!Objects.equals(original.getId(), meta.getId())
            || !AiResourceConstants.META_STATUS_ENABLE.equals(meta.getStatus())) {
            throw unavailable();
        }
        AiResourceVersion version =
            findVersion(meta.getNamespaceId(), meta.getName(), exactVersion);
        if (version == null || version.getId() == null || version.getId() <= 0
            || !Objects.equals(version.getNamespaceId(), meta.getNamespaceId())
            || !Objects.equals(version.getName(), meta.getName())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(version.getType())
            || !Objects.equals(version.getVersion(), exactVersion)
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())) {
            throw unavailable();
        }
        return new HubSkillTransferSourceSnapshot(meta, version, hubEntryId);
    }
    
    /** Recheck the exact source primary keys; never follow a reused name/version string. */
    public AiResourceVersion requireStillLive(HubSkillTransferSourceSnapshot expected)
        throws NacosException {
        if (expected == null) {
            throw unavailable();
        }
        AiResource meta = lookupDirectSource(expected.getNamespaceId(), expected.getName());
        AiResourceVersion version =
            findVersion(expected.getNamespaceId(), expected.getName(), expected.getVersion());
        if (!expected.matches(meta, version)
            || !AiResourceConstants.META_STATUS_ENABLE.equals(meta.getStatus())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(version.getStatus())) {
            throw unavailable();
        }
        return version;
    }
    
    private AiResourceVersion findVersion(String namespaceId, String name, String version)
        throws NacosException {
        try {
            return manager.findVersion(namespaceId, name, AiResourceConstants.RESOURCE_TYPE_SKILL,
                version);
        } catch (RuntimeException storageFailure) {
            throw storageUnavailable();
        }
    }
    
    private NacosApiException storageUnavailable() {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
            "Skill transfer source unavailable");
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Source Skill or selected version is unavailable");
    }
}
