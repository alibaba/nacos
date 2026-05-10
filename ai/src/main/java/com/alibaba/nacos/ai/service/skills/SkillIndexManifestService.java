/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

import static com.alibaba.nacos.ai.model.skills.SkillIndexManifest.LABEL_LATEST;

/**
 * Service for reading, writing, and deleting skill index manifest in Nacos Config.
 *
 * <p>The manifest is stored at group={@code skill_{name}}, dataId={@code skill_index.json},
 * and serves as the single source of truth for client-side skill discovery.</p>
 *
 * @author nacos
 */
@Service
public class SkillIndexManifestService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillIndexManifestService.class);
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final SyncEffectService syncEffectService;
    
    public SkillIndexManifestService(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService, SyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.syncEffectService = syncEffectService;
    }
    
    /**
     * Query manifest from server-side config cache.
     *
     * @return manifest object, or null if config not found
     */
    public SkillIndexManifest query(String namespaceId, String skillName) {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                SkillUtils.SKILL_INDEX_DATA_ID, SkillUtils.buildSkillGroup(skillName), namespaceId);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || response.getContent() == null) {
                return null;
            }
            return JacksonUtils.toObj(response.getContent(), SkillIndexManifest.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to query skill index manifest for {}: {}", skillName,
                e.getMessage());
            return null;
        }
    }
    
    /**
     * Get existing manifest or create a new empty one with initialized maps.
     */
    public SkillIndexManifest loadForUpdate(String namespaceId, String skillName) {
        SkillIndexManifest manifest = query(namespaceId, skillName);
        if (manifest == null) {
            manifest = new SkillIndexManifest();
        }
        if (manifest.getLabels() == null) {
            manifest.setLabels(new HashMap<>(4));
        }
        if (manifest.getVersions() == null) {
            manifest.setVersions(new HashMap<>(4));
        }
        return manifest;
    }
    
    /**
     * Write manifest to Nacos Config. Creates if not exists, updates if already exists.
     */
    public void write(String namespaceId, String skillName, SkillIndexManifest manifest)
        throws NacosException {
        long startTimeStamp = System.currentTimeMillis();
        ConfigForm form = new ConfigForm();
        form.setDataId(SkillUtils.SKILL_INDEX_DATA_ID);
        form.setGroup(SkillUtils.buildSkillGroup(skillName));
        form.setNamespaceId(namespaceId);
        form.setContent(JacksonUtils.toJson(manifest));
        form.setSrcUser("nacos");
        form.setType(ConfigType.JSON.getType());
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        try {
            configOperationService.publishConfig(form, requestInfo, null);
        } catch (ConfigAlreadyExistsException alreadyExists) {
            requestInfo.setUpdateForExist(Boolean.TRUE);
            configOperationService.publishConfig(form, requestInfo, null);
        }
        if (syncEffectService != null) {
            syncEffectService.toSync(form, startTimeStamp);
        }
    }
    
    /**
     * Delete manifest from Nacos Config.
     */
    public void delete(String namespaceId, String skillName) throws NacosException {
        configOperationService.deleteConfig(SkillUtils.SKILL_INDEX_DATA_ID,
            SkillUtils.buildSkillGroup(skillName), namespaceId, null, null, "nacos", null);
    }
    
    /**
     * Resolve version from manifest: explicit version > label lookup > latest label.
     *
     * @param manifest the manifest to resolve from
     * @param version  explicit version (may be blank)
     * @param label    label to look up (defaults to "latest" if blank)
     * @return resolved version string, or null if not found
     */
    public static String resolveVersion(SkillIndexManifest manifest, String version, String label) {
        if (manifest == null || manifest.getVersions() == null
            || manifest.getVersions().isEmpty()) {
            return null;
        }
        if (StringUtils.isNotBlank(version)) {
            return manifest.getVersions().containsKey(version) ? version : null;
        }
        String labelKey = StringUtils.isNotBlank(label) ? label : LABEL_LATEST;
        if (manifest.getLabels() != null) {
            String v = manifest.getLabels().get(labelKey);
            if (StringUtils.isNotBlank(v) && manifest.getVersions().containsKey(v)) {
                return v;
            }
        }
        return null;
    }
}
