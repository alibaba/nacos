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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.utils.SkillContentDigestUtils;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Skill client operation service implementation.
 *
 * <p>Implements the listener-style query path with MD5-based not-modified semantics. The
 * authoritative content MD5 is persisted alongside the version row in the
 * {@code ai_resource_version.storage} JSON column at publish time. For historical versions that
 * were published before the listener feature shipped, the MD5 is back-filled synchronously on the
 * first listener-style query (Path A).
 *
 * <p>Defensive null-fallback: if the MD5 is missing or the back-fill fails for any reason, this
 * service responds with HTTP 200 and the freshly computed MD5 instead of HTTP 304, so the client
 * is guaranteed to refresh its local cache at least once after the feature ships.
 *
 * @author nacos
 * @since 3.2.0
 */
@Service
public class SkillClientOperationServiceImpl implements SkillClientOperationService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SkillClientOperationServiceImpl.class);
    
    private final SkillOperationService skillOperationService;
    
    private final SkillIndexManifestService manifestService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    public SkillClientOperationServiceImpl(@Lazy SkillOperationService skillOperationService,
        SkillIndexManifestService manifestService,
        AiResourceVersionPersistService aiResourceVersionPersistService) {
        this.skillOperationService = skillOperationService;
        this.manifestService = manifestService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
    }
    
    @Override
    public SkillQueryResult querySkill(String namespaceId, String name, String version,
        String label, String clientMd5) throws NacosException {
        if (StringUtils.isBlank(name)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `name` not present");
        }
        
        // Step 1: Resolve target version up-front so we can short-circuit on MD5 match without
        // loading the full skill bytes. Manifest absence here is treated as not-found and falls
        // through to skillOperationService.querySkill which raises a consistent error message.
        String resolvedVersion = resolveVersionFromManifest(namespaceId, name, version, label);
        
        // Step 2: Read the published content MD5 directly from the version row's storage JSON.
        String storedMd5 = readStoredContentMd5(namespaceId, name, resolvedVersion);
        
        // Step 3: Fast path — client cache is fresh, no need to load skill bytes.
        if (StringUtils.isNotBlank(storedMd5) && StringUtils.isNotBlank(clientMd5)
            && storedMd5.equals(clientMd5)) {
            return SkillQueryResult.notModified(storedMd5, resolvedVersion);
        }
        
        // Step 4: Load the skill via the regular query path. This also validates meta visibility
        // and produces the canonical NOT_FOUND error when the resource has been deleted in flight.
        Skill skill = skillOperationService.querySkill(namespaceId, name, version, label);
        
        // Step 5: Decide effective MD5. When the version row is missing the field (legacy publish
        // before the listener feature shipped), compute it from the loaded skill and back-fill the
        // storage column synchronously (Path A). The defensive null-fallback ensures we always
        // return a fresh payload in this branch — never not-modified — even if the freshly
        // computed MD5 happens to coincide with the client-supplied one.
        String effectiveMd5 = storedMd5;
        if (StringUtils.isBlank(effectiveMd5)) {
            effectiveMd5 = backfillContentMd5(namespaceId, name, resolvedVersion, skill);
        }
        return new SkillQueryResult(skill, effectiveMd5, resolvedVersion);
    }
    
    /**
     * Resolve the target version string from the skill index manifest. Returns {@code null} when
     * the manifest is missing or the {@code version}/{@code label} cannot be resolved; callers
     * should treat a {@code null} return as "let the regular query path produce NOT_FOUND".
     */
    private String resolveVersionFromManifest(String namespaceId, String name, String version,
        String label) {
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest == null) {
            return null;
        }
        return SkillIndexManifestService.resolveVersion(manifest, version, label);
    }
    
    /**
     * Read the persisted {@code contentMd5} from the version row's {@code storage} JSON column.
     * Returns {@code null} when the row is missing, the storage payload is blank, the JSON is
     * unparseable, or the {@code contentMd5} key is absent.
     */
    private String readStoredContentMd5(String namespaceId, String name, String resolvedVersion) {
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        AiResourceVersion versionRow =
            aiResourceVersionPersistService.find(namespaceId, name,
                Constants.Skills.RESOURCE_TYPE_SKILL, resolvedVersion);
        if (versionRow == null || StringUtils.isBlank(versionRow.getStorage())) {
            return null;
        }
        try {
            Map<String, Object> map = JacksonUtils.toObj(versionRow.getStorage(),
                new TypeReference<Map<String, Object>>() {
                });
            Object md5 = map == null ? null
                : map.get(Constants.Skills.STORAGE_KEY_CONTENT_MD5);
            return md5 instanceof String ? (String) md5 : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse storage JSON for skill {}@{}, ignored",
                name, resolvedVersion, e);
            return null;
        }
    }
    
    /**
     * Path-A back-fill: compute the content MD5 from the loaded skill and persist it into the
     * version row's storage column. Persistence failure is swallowed and logged; the freshly
     * computed MD5 is still returned so the response carries an authoritative fingerprint.
     */
    private String backfillContentMd5(String namespaceId, String name, String resolvedVersion,
        Skill skill) {
        String computed;
        try {
            computed = SkillContentDigestUtils.computeContentMd5(skill);
        } catch (Exception e) {
            LOGGER.warn("Failed to compute content MD5 for skill {}@{}, fall back to null",
                name, resolvedVersion, e);
            return null;
        }
        if (StringUtils.isBlank(resolvedVersion)) {
            return computed;
        }
        try {
            aiResourceVersionPersistService.updateStorageMd5(namespaceId, name,
                Constants.Skills.RESOURCE_TYPE_SKILL, resolvedVersion, computed);
        } catch (Exception e) {
            LOGGER.warn("Failed to back-fill content MD5 for skill {}@{}, response is still 200",
                name, resolvedVersion, e);
        }
        return computed;
    }
}
