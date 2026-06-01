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

package com.alibaba.nacos.ai.constant;

/**
 * Nacos AI Server Constants.
 *
 * @author xiweng.yy
 */
public class Constants {
    
    public static final String MCP_PATH = "/ai/mcp";
    
    public static final String MCP_ADMIN_PATH = "/v3/admin" + MCP_PATH;
    
    public static final String MCP_CONSOLE_PATH = "/v3/console" + MCP_PATH;
    
    public static final String AI_RESOURCE_IMPORT_ADMIN_PATH = "/v3/admin/ai/import";
    
    public static final String AI_RESOURCE_IMPORT_CONSOLE_PATH = "/v3/console/ai/import";
    
    public static final String MCP_LIST_SEARCH_ACCURATE = "accurate";
    
    public static final String MCP_LIST_SEARCH_BLUR = "blur";
    
    public static final String ALL_PATTERN = com.alibaba.nacos.api.common.Constants.ALL_PATTERN;
    
    public static final String MCP_SERVER_VERSIONS_GROUP = "mcp-server-versions";
    
    public static final String MCP_SERVER_GROUP = "mcp-server";
    
    public static final String MCP_SERVER_TOOL_GROUP = "mcp-tools";
    
    public static final String MCP_SERVER_RESOURCE_GROUP = "mcp-resources";
    
    public static final String MCP_SERVER_SPEC_DATA_ID_SUFFIX = "-mcp-server.json";
    
    public static final String MCP_SERVER_VERSION_DATA_ID_SUFFIX = "-mcp-versions.json";
    
    public static final String MCP_SERVER_TOOL_DATA_ID_SUFFIX = "-mcp-tools.json";
    
    public static final String MCP_SERVER_RESOURCE_DATA_ID_SUFFIX = "-mcp-resources.json";
    
    public static final String MCP_SERVER_ENDPOINT_GROUP = "mcp-endpoints";
    
    public static final String MCP_SERVER_ENDPOINT_CLUSTER =
        com.alibaba.nacos.api.common.Constants.DEFAULT_CLUSTER_NAME;
    
    public static final String MCP_BACKEND_INSTANCE_PROTOCOL_KEY = "transportProtocol";
    
    public static final String MCP_SERVER_ENDPOINT_ADDRESS = "address";
    
    public static final String MCP_SERVER_ENDPOINT_PORT = "port";
    
    public static final String MCP_SERVER_ENDPOINT_METADATA_MARK = "__nacos.ai.mcp.service__";
    
    public static final String MCP_SERVER_CONFIG_MARK = "nacos.internal.config=mcp";
    
    public static final String PROTOCOL_TYPE_HTTP = "http";
    
    public static final String PROTOCOL_TYPE_HTTPS = "https";
    
    public static final String SERVER_EXPORT_PATH_KEY = "exportPath";
    
    public static final String MCP_SERVER_NAME_TAG_KEY_PREFIX = "mcpServerName=";
    
    public static final int MAX_LIST_SIZE = 100;
    
    public static final String RELEASE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    
    public static final String CONFIG_TAGS_NAME = "config_tags";
    
    public static final String META_PATH = "path";
    
    public static final String SERVER_VERSION_CONFIG_DATA_ID_TEMPLATE =
        "%s" + MCP_SERVER_VERSION_DATA_ID_SUFFIX;
    
    public static final String SERVER_SPECIFICATION_CONFIG_DATA_ID_TEMPLATE =
        "%s-%s" + MCP_SERVER_SPEC_DATA_ID_SUFFIX;
    
    public static final String SERVER_TOOLS_SPEC_CONFIG_DATA_ID_TEMPLATE =
        "%s-%s" + MCP_SERVER_TOOL_DATA_ID_SUFFIX;
    
    public static final String SERVER_RESOURCE_SPEC_CONFIG_DATA_ID_TEMPLATE =
        "%s-%s" + MCP_SERVER_RESOURCE_DATA_ID_SUFFIX;
    
    public static class A2A {
        
        public static final String CONSOLE_PATH = "/v3/console/ai/a2a";
        
        public static final String ADMIN_PATH = "/v3/admin/ai/a2a";
        
        public static final String AGENT_GROUP = "agent";
        
        public static final String AGENT_VERSION_GROUP = "agent-version";
        
        public static final String SEARCH_BLUR = "blur";
        
        public static final String SEARCH_ACCURATE = "accurate";
        
        public static final String AGENT_ENDPOINT_GROUP = "agent-endpoints";
        
        public static final String AGENT_ENDPOINT_PATH_KEY = "__nacos.agent.endpoint.path__";
        
        public static final String AGENT_ENDPOINT_TRANSPORT_KEY =
            "__nacos.agent.endpoint.transport__";
        
        public static final String NACOS_AGENT_ENDPOINT_SUPPORT_TLS =
            "__nacos.agent.endpoint.supportTls__";
        
        public static final String NACOS_AGENT_ENDPOINT_PROTOCOL_KEY =
            "__nacos.agent.endpoint.protocol__";
        
        public static final String NACOS_AGENT_ENDPOINT_QUERY_KEY =
            "__nacos.agent.endpoint.query__";
        
        public static final String NACOS_AGENT_ENDPOINT_PROTOCOL_VERSION_KEY =
            "__nacos.agent.endpoint.protocolVersion__";
        
        public static final String NACOS_AGENT_ENDPOINT_TENANT_KEY =
            "__nacos.agent.endpoint.tenant__";
    }
    
    public static class Skills {
        
        public static final String CONSOLE_PATH = "/v3/console/ai/skills";
        
        public static final String ADMIN_PATH = "/v3/admin/ai/skills";
        
        public static final String CLIENT_PATH = "/v3/client/ai/skills";
        
        public static final String SKILL_GROUP = "skill";
        
        public static final String SKILL_VERSION_GROUP = "skill-version";
        
        public static final String SEARCH_BLUR = "blur";
        
        public static final String SEARCH_ACCURATE = "accurate";
        
        public static final String SKILL_DEFAULT_NAMESPACE = "public";
        
        /**
         * Resource type constant used in {@code ai_resource_version.type} for skill rows.
         */
        public static final String RESOURCE_TYPE_SKILL = "skill";
        
        /**
         * Key inside {@code ai_resource_version.storage} JSON for the published content MD5.
         */
        public static final String STORAGE_KEY_CONTENT_MD5 = "contentMd5";
        
        /**
         * Response header carrying the published skill content MD5 for client listener cache.
         */
        public static final String HEADER_SKILL_MD5 = "X-Nacos-Skill-Md5";
        
        /**
         * Response header carrying the resolved version when the client queries by label.
         */
        public static final String HEADER_SKILL_RESOLVED_VERSION = "X-Nacos-Skill-Resolved-Version";
        
        /**
         * Default max allowed size for skill zip upload (10MB).
         *
         * <p>Runtime callers should use
         * {@code com.alibaba.nacos.ai.utils.SkillZipParser#resolveMaxUploadBytes()} instead, which
         * honors the {@code nacos.ai.skill.zip.max-upload-size-mb} property when an operator
         * needs to raise this cap. This constant is preserved as the historical default and for
         * backward compatibility with callers outside the skill upload path.
         */
        public static final long MAX_UPLOAD_ZIP_BYTES = 10L * 1024 * 1024;
    }
    
    public static class AgentSpecs {
        
        public static final String ADMIN_PATH = "/v3/admin/ai/agentspecs";
        
        public static final String CLIENT_PATH = "/v3/client/ai/agentspecs";
        
        public static final String CONSOLE_PATH = "/v3/console/ai/agentspecs";
        
        public static final String AGENTSPEC_GROUP_PREFIX = "agentspec_";
        
        public static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
        
        public static final String AGENTSPEC_MAIN_DATA_ID = "manifest.json";
        
        /**
         * Default max allowed size for agentspec zip upload (50MB).
         *
         * <p>Runtime callers should use
         * {@code com.alibaba.nacos.ai.utils.AgentSpecZipParser#resolveMaxUploadBytes()} instead,
         * which honors the {@code nacos.ai.agentspec.zip.max-upload-size-mb} property when an
         * operator needs to raise this cap. This constant is preserved as the historical default
         * and for backward compatibility with callers outside the AgentSpec upload path.
         */
        public static final long MAX_UPLOAD_ZIP_BYTES = 50L * 1024 * 1024;
        
        public static final String AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY =
            "nacos.ai.agentspec.storage.provider";
        
        public static final String SEARCH_BLUR = "blur";
        
        public static final String SEARCH_ACCURATE = "accurate";
        
        public static final String AGENTSPEC_DEFAULT_NAMESPACE = "public";
        
        public static final String HEADER_AGENTSPEC_MD5 = "X-Nacos-AgentSpec-Md5";
        
        public static final String HEADER_AGENTSPEC_RESOLVED_VERSION =
            "X-Nacos-AgentSpec-Resolved-Version";
    }
    
    public static class Pipeline {
        
        public static final String ADMIN_PATH = "/v3/admin/ai/pipelines";
        
        public static final String CONSOLE_PATH = "/v3/console/ai/pipelines";
        
        /**
         * List pipeline executions (aligned with Skill/Prompt {@code /list} style).
         */
        public static final String LIST_SUBPATH = "/list";
        
        /**
         * Get single pipeline execution by id (query parameter {@code pipelineId}).
         */
        public static final String DETAIL_SUBPATH = "/detail";
    }
    
    public static class Prompt {
        
        public static final String CONSOLE_PATH = "/v3/console/ai/prompt";
        
        public static final String ADMIN_PATH = "/v3/admin/ai/prompt";
        
        public static final String CLIENT_PATH = "/v3/client/ai/prompt";
        
        /**
         * Fixed group for all prompt configurations.
         */
        public static final String PROMPT_GROUP = "nacos-ai-prompt";
        
        /**
         * DataId suffix for prompt configurations.
         */
        public static final String PROMPT_DATA_ID_SUFFIX = ".json";
        
        /**
         * DataId suffix for descriptor side prompt metadata.
         */
        public static final String DESCRIPTOR_DATA_ID_SUFFIX =
            ".descriptor" + PROMPT_DATA_ID_SUFFIX;
        
        /**
         * DataId suffix for runtime label/version mapping.
         */
        public static final String LABEL_VERSION_MAPPING_DATA_ID_SUFFIX =
            ".label-version-mapping" + PROMPT_DATA_ID_SUFFIX;
        
        /**
         * Key for prompt version in extInfo.
         */
        public static final String EXT_PROMPT_VERSION = "prompt_version";
        
        /**
         * Key for prompt commit message in extInfo.
         */
        public static final String EXT_PROMPT_COMMIT_MSG = "prompt_commit_msg";
        
        /**
         * Search mode: blur search.
         */
        public static final String SEARCH_BLUR = "blur";
        
        /**
         * Search mode: accurate search.
         */
        public static final String SEARCH_ACCURATE = "accurate";
        
        /**
         * Default namespace for prompt.
         */
        public static final String PROMPT_DEFAULT_NAMESPACE = "public";
        
        /**
         * Config type for prompt.
         */
        public static final String PROMPT_CONFIG_TYPE = "json";
        
        /**
         * JSON field: promptKey.
         */
        public static final String FIELD_PROMPT_KEY = "promptKey";
        
        /**
         * JSON field: version.
         */
        public static final String FIELD_VERSION = "version";
        
        /**
         * JSON field: template.
         */
        public static final String FIELD_TEMPLATE = "template";
        
        /**
         * JSON field: commitMsg.
         */
        public static final String FIELD_COMMIT_MSG = "commitMsg";
    }
}
