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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Source lifecycle fences for MCP, Agent, Prompt and AgentSpec. Skill keeps its existing fences.
 * Call only after ordinary source authorization and argument/state validation.
 */
@Service
public class HubResourceLifecycleRevoker {
    
    private static final int MAX_ATTEMPTS = 12;
    
    private final HubEntryStorage storage;
    
    public HubResourceLifecycleRevoker(HubEntryStorage storage) {
        this.storage = storage;
    }
    
    /** Execute a source mutation between two identity-pinned publication revocation fences. */
    public <T> T around(AiResource source, String affectedVersion, SourceMutation<T> mutation)
        throws NacosException {
        if (!supported(source)) {
            return mutation.run();
        }
        revoke(source, affectedVersion);
        T result;
        try {
            result = mutation.run();
        } catch (NacosException | RuntimeException | Error failure) {
            try {
                revoke(source, affectedVersion);
            } catch (NacosException | RuntimeException repairFailure) {
                failure.addSuppressed(repairFailure);
            }
            throw failure;
        }
        revoke(source, affectedVersion);
        return result;
    }
    
    /** Re-evaluate the current pinned version on every CAS retry, never disabling another version. */
    public boolean revoke(AiResource source, String affectedVersion) throws NacosException {
        if (!supported(source)) {
            return false;
        }
        if (source.getId() == null || source.getId() <= 0 || source.getName() == null
            || source.getNamespaceId() == null
            || HubNamespaceUtils.isHubNamespace(source.getNamespaceId())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Revocation requires a real source identity");
        }
        HubResourceType type = HubResourceType.fromResourceType(source.getType());
        String internalName =
            HubResourceNameUtils.buildInternalName(source.getName(), source.getId());
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            AiResource current = storage.findByInternalName(type, internalName);
            if (current == null || "disable".equals(current.getStatus())) {
                return false;
            }
            if (current.getId() == null || current.getMetaVersion() == null
                || current.getMetaVersion() <= 0 || !"enable".equals(current.getStatus())) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Hub publication identity or status is inconsistent");
            }
            if (!affected(current, source, affectedVersion)) {
                return false;
            }
            AiResource next = new AiResource();
            next.setStatus("disable");
            next.setDesc(current.getDesc());
            next.setBizTags(current.getBizTags());
            next.setExt(current.getExt());
            next.setVersionInfo(current.getVersionInfo());
            if (storage.updateMetaCas(type, current, current.getMetaVersion(), next)) {
                return true;
            }
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Hub publication changed repeatedly; retry source lifecycle operation");
    }
    
    private boolean affected(AiResource entry, AiResource source, String version) {
        try {
            HubResourceReference reference = HubResourceReferenceJsonUtil.parse(entry.getExt());
            return Objects.equals(reference.getSourceNamespaceId(), source.getNamespaceId())
                && Objects.equals(reference.getSourceResourceId(), source.getId())
                && Objects.equals(reference.getSourceName(), source.getName())
                && (version == null || Objects.equals(reference.getSourceVersion(), version));
        } catch (NacosException | RuntimeException invalidReference) {
            // The canonical name includes the globally unique source row ID; a malformed
            // descriptor cannot keep an enabled orphan alive during a source takedown.
            return true;
        }
    }
    
    private boolean supported(AiResource source) {
        return source != null && ("mcp".equals(source.getType()) || "agent".equals(source.getType())
            || "prompt".equals(source.getType()) || "agentspec".equals(source.getType()));
    }
    
    /** Ordinary source work; this callback neither supplies nor bypasses authorization. */
    @FunctionalInterface
    public interface SourceMutation<T> {
        
        /** Perform the already-authorized source mutation. */
        T run() throws NacosException;
    }
}
