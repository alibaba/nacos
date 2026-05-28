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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.http.DefaultImportHttpClient;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp.McpRegistryImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillsShImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill.SkillWellKnownImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportSourceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Default AI resource import source presets.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class DefaultAiResourceImportSourceProvider implements AiResourceImportSourceProvider {
    
    public static final String PREFIX = "nacos.plugin.ai.importer.";
    
    public static final String MCP_OFFICIAL_PREFIX = PREFIX + "mcp.official.";
    
    public static final String SKILL_WELL_KNOWN_PREFIX = PREFIX + "skills.well-known.";
    
    public static final String SKILLS_SH_PREFIX = PREFIX + "skills.skills-sh.";
    
    public static final String OFFICIAL_MCP_ENDPOINT =
        "https://registry.modelcontextprotocol.io/v0/servers";
    
    public static final String SKILLS_SH_ENDPOINT = "https://skills.sh";
    
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    
    private static final int DEFAULT_MAX_PAGE_COUNT = 20;
    
    private static final int DEFAULT_MAX_ITEM_COUNT = 500;
    
    private static final long DEFAULT_MAX_ARTIFACT_SIZE = 10L * 1024L * 1024L;
    
    @Override
    public Collection<AiResourceImportSource> loadSources(Properties properties)
        throws NacosException {
        List<AiResourceImportSource> result = new ArrayList<>(3);
        AiResourceImportSource officialMcp = loadOfficialMcpSource(properties);
        if (officialMcp != null) {
            result.add(officialMcp);
        }
        AiResourceImportSource wellKnownSkill = loadSkillWellKnownSource(properties);
        if (wellKnownSkill != null) {
            result.add(wellKnownSkill);
        }
        AiResourceImportSource skillsSh = loadSkillsShSource(properties);
        if (skillsSh != null) {
            result.add(skillsSh);
        }
        return result;
    }
    
    private AiResourceImportSource loadOfficialMcpSource(Properties properties) {
        if (!getBoolean(properties, MCP_OFFICIAL_PREFIX + "enabled", true)) {
            return null;
        }
        AiResourceImportSource result = new AiResourceImportSource();
        result.setSourceId(getString(properties, MCP_OFFICIAL_PREFIX, "source-id", "sourceId",
            "mcp-official"));
        result.setDisplayName(getString(properties, MCP_OFFICIAL_PREFIX, "display-name",
            "displayName", "Official MCP Registry"));
        result.setDescription(getString(properties, MCP_OFFICIAL_PREFIX, "description",
            "description", "Import MCP servers from the official MCP registry."));
        result.setPluginName(McpRegistryImportServiceBuilder.IMPORTER_TYPE);
        result.setResourceTypes(
            Collections.singletonList(AiResourceImportConstants.RESOURCE_TYPE_MCP));
        result.setEndpoint(getString(properties, MCP_OFFICIAL_PREFIX, "endpoint", "endpoint",
            OFFICIAL_MCP_ENDPOINT));
        applyCommonSourceOptions(properties, MCP_OFFICIAL_PREFIX, result);
        return result;
    }
    
    private AiResourceImportSource loadSkillWellKnownSource(Properties properties)
        throws NacosException {
        if (!getBoolean(properties, SKILL_WELL_KNOWN_PREFIX + "enabled", false)) {
            return null;
        }
        String endpoint = getString(properties, SKILL_WELL_KNOWN_PREFIX, "url", "endpoint",
            null);
        if (StringUtils.isBlank(endpoint)) {
            throw invalidConfig(
                "Skill well-known import source url must not be empty when enabled.");
        }
        AiResourceImportSource result = new AiResourceImportSource();
        result.setSourceId(getString(properties, SKILL_WELL_KNOWN_PREFIX, "source-id",
            "sourceId", "skills-well-known"));
        result.setDisplayName(getString(properties, SKILL_WELL_KNOWN_PREFIX, "display-name",
            "displayName", "Skill Well-known Registry"));
        result.setDescription(getString(properties, SKILL_WELL_KNOWN_PREFIX, "description",
            "description", "Import Skills from a well-known Skill discovery endpoint."));
        result.setPluginName(SkillWellKnownImportServiceBuilder.IMPORTER_TYPE);
        result.setResourceTypes(
            Collections.singletonList(AiResourceImportConstants.RESOURCE_TYPE_SKILL));
        result.setEndpoint(endpoint);
        applyCommonSourceOptions(properties, SKILL_WELL_KNOWN_PREFIX, result);
        return result;
    }
    
    private AiResourceImportSource loadSkillsShSource(Properties properties) {
        if (!getBoolean(properties, SKILLS_SH_PREFIX + "enabled", true)) {
            return null;
        }
        AiResourceImportSource result = new AiResourceImportSource();
        result.setSourceId(getString(properties, SKILLS_SH_PREFIX, "source-id", "sourceId",
            "skills-sh"));
        result.setDisplayName(getString(properties, SKILLS_SH_PREFIX, "display-name",
            "displayName", "skills.sh"));
        result.setDescription(getString(properties, SKILLS_SH_PREFIX, "description",
            "description", "Import Skills from skills.sh."));
        result.setPluginName(SkillsShImportServiceBuilder.IMPORTER_TYPE);
        result.setResourceTypes(
            Collections.singletonList(AiResourceImportConstants.RESOURCE_TYPE_SKILL));
        result.setEndpoint(getString(properties, SKILLS_SH_PREFIX, "endpoint", "url",
            SKILLS_SH_ENDPOINT));
        applyCommonSourceOptions(properties, SKILLS_SH_PREFIX, result);
        return result;
    }
    
    private void applyCommonSourceOptions(Properties properties, String prefix,
        AiResourceImportSource source) {
        source.setEnabled(true);
        source.setAuthRef(getString(properties, prefix, "auth-ref", "authRef", null));
        source.setConnectTimeoutMillis(getInt(properties, prefix + "connect-timeout-ms",
            DEFAULT_CONNECT_TIMEOUT_MILLIS));
        source.setReadTimeoutMillis(getInt(properties, prefix + "read-timeout-ms",
            DEFAULT_READ_TIMEOUT_MILLIS));
        source.setMaxPageCount(getInt(properties, prefix + "max-page-count",
            DEFAULT_MAX_PAGE_COUNT));
        source.setMaxItemCount(getInt(properties, prefix + "max-item-count",
            DEFAULT_MAX_ITEM_COUNT));
        source.setMaxArtifactSize(getLong(properties, prefix + "max-artifact-size",
            DEFAULT_MAX_ARTIFACT_SIZE));
        applySecurityOptions(properties, prefix, source);
    }
    
    private void applySecurityOptions(Properties properties, String prefix,
        AiResourceImportSource source) {
        Map<String, String> sourceProperties = new LinkedHashMap<>(2);
        putConfiguredProperty(properties, prefix, DefaultImportHttpClient.PROPERTY_ALLOW_HTTP,
            DefaultImportHttpClient.PROPERTY_ALLOW_HTTP_CAMEL, sourceProperties);
        putConfiguredProperty(properties, prefix,
            DefaultImportHttpClient.PROPERTY_ALLOW_PRIVATE_NETWORK,
            DefaultImportHttpClient.PROPERTY_ALLOW_PRIVATE_NETWORK_CAMEL, sourceProperties);
        if (!sourceProperties.isEmpty()) {
            source.setProperties(sourceProperties);
        }
    }
    
    private void putConfiguredProperty(Properties properties, String prefix, String kebabKey,
        String camelKey, Map<String, String> sourceProperties) {
        String value = getString(properties, prefix, kebabKey, camelKey, null);
        if (StringUtils.isNotBlank(value)) {
            sourceProperties.put(kebabKey, value);
        }
    }
    
    private String getString(Properties properties, String prefix, String kebabKey,
        String camelKey, String defaultValue) {
        String value = properties.getProperty(prefix + kebabKey);
        if (StringUtils.isBlank(value)) {
            value = properties.getProperty(prefix + camelKey);
        }
        return StringUtils.isBlank(value) ? defaultValue : StringUtils.trim(value);
    }
    
    private boolean getBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }
    
    private int getInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }
    
    private long getLong(Properties properties, String key, long defaultValue) {
        String value = properties.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Long.parseLong(value);
    }
    
    private NacosException invalidConfig(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
