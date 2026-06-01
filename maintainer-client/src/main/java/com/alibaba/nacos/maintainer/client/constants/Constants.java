/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.constants;

/**
 * All the constants.
 *
 * @author Nacos
 */
public class Constants {
    
    public static class AdminApiPath {
        
        public static final String CONFIG_ADMIN_PATH = "/v3/admin/cs/config";
        
        public static final String CONFIG_HISTORY_ADMIN_PATH = "/v3/admin/cs/history";
        
        public static final String CONFIG_OPS_ADMIN_PATH = "/v3/admin/cs/ops";
        
        public static final String CONFIG_LISTENER_ADMIN_PATH = "/v3/admin/cs/listener";
        
        public static final String NAMING_SERVICE_ADMIN_PATH = "/v3/admin/ns/service";
        
        public static final String NAMING_INSTANCE_ADMIN_PATH = "/v3/admin/ns/instance";
        
        public static final String NAMING_CLUSTER_ADMIN_PATH = "/v3/admin/ns/cluster";
        
        public static final String NAMING_HEALTH_ADMIN_PATH = "/v3/admin/ns/health";
        
        public static final String NAMING_CLIENT_ADMIN_PATH = "/v3/admin/ns/client";
        
        public static final String NAMING_OPS_ADMIN_PATH = "/v3/admin/ns/ops";
        
        public static final String CORE_LOADER_ADMIN_PATH = "/v3/admin/core/loader";
        
        public static final String CORE_CLUSTER_ADMIN_PATH = "/v3/admin/core/cluster";
        
        public static final String CORE_OPS_ADMIN_PATH = "/v3/admin/core/ops";
        
        public static final String CORE_NAMESPACE_ADMIN_PATH = "/v3/admin/core/namespace";
        
        public static final String CORE_STATE_ADMIN_PATH = "/v3/admin/core/state";
        
        public static final String AI_MCP_ADMIN_PATH = "/v3/admin/ai/mcp";
        
        public static final String AI_AGENT_ADMIN_PATH = "/v3/admin/ai/a2a";
        
        public static final String AI_AGENT_LIST_VERSION_ADMIN_PATH =
            AI_AGENT_ADMIN_PATH + "/version/list";
        
        public static final String AI_AGENT_LIST_ADMIN_PATH = AI_AGENT_ADMIN_PATH + "/list";
        
        public static final String AI_PROMPT_ADMIN_PATH = "/v3/admin/ai/prompt";
        
        public static final String AI_PROMPT_LIST_ADMIN_PATH = AI_PROMPT_ADMIN_PATH + "/list";
        
        public static final String AI_PROMPT_VERSIONS_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/versions";
        
        public static final String AI_PROMPT_GOVERNANCE_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/governance";
        
        public static final String AI_PROMPT_VERSION_DETAIL_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/version";
        
        public static final String AI_PROMPT_BIZ_TAGS_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/biz-tags";
        
        public static final String AI_PROMPT_DESCRIPTION_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/description";
        
        /**
         * Deprecated admin path for prompt metadata.
         *
         * @deprecated Use {@link #AI_PROMPT_GOVERNANCE_ADMIN_PATH} instead.
         */
        @Deprecated
        public static final String AI_PROMPT_METADATA_ADMIN_PATH =
            AI_PROMPT_ADMIN_PATH + "/metadata";
        
        /**
         * Deprecated admin path for prompt detail.
         *
         * @deprecated Use {@link #AI_PROMPT_VERSION_DETAIL_ADMIN_PATH} instead.
         */
        @Deprecated
        public static final String AI_PROMPT_DETAIL_ADMIN_PATH = AI_PROMPT_ADMIN_PATH + "/detail";
        
        /**
         * Deprecated admin path for prompt label operations.
         *
         * @deprecated Use updateLabels API instead.
         */
        @Deprecated
        public static final String AI_PROMPT_LABEL_ADMIN_PATH = AI_PROMPT_ADMIN_PATH + "/label";
        
        public static final String CORE_PLUGIN_ADMIN_PATH = "/v3/admin/core/plugin";
        
        public static final String AI_SKILL_ADMIN_PATH = "/v3/admin/ai/skills";
        
        public static final String AI_SKILL_LIST_ADMIN_PATH = AI_SKILL_ADMIN_PATH + "/list";
        
        public static final String AI_SKILL_VERSION_ADMIN_PATH = AI_SKILL_ADMIN_PATH + "/version";
        
        public static final String AI_SKILL_SCOPE_ADMIN_PATH = AI_SKILL_ADMIN_PATH + "/scope";
        
        public static final String AI_SKILL_BIZ_TAGS_ADMIN_PATH = AI_SKILL_ADMIN_PATH + "/biz-tags";
        
        public static final String AI_SKILL_UPLOAD_ADMIN_PATH = AI_SKILL_ADMIN_PATH + "/upload";
        
        public static final String AI_SKILL_BATCH_UPLOAD_ADMIN_PATH =
            AI_SKILL_UPLOAD_ADMIN_PATH + "/batch";
        
        public static final String AI_AGENTSPEC_ADMIN_PATH = "/v3/admin/ai/agentspecs";
        
        public static final String AI_AGENTSPEC_LIST_ADMIN_PATH = AI_AGENTSPEC_ADMIN_PATH + "/list";
        
        public static final String AI_AGENTSPEC_VERSION_ADMIN_PATH =
            AI_AGENTSPEC_ADMIN_PATH + "/version";
        
        public static final String AI_AGENTSPEC_VERSION_META_ADMIN_PATH =
            AI_AGENTSPEC_ADMIN_PATH + "/version/meta";
        
        public static final String AI_AGENTSPEC_SCOPE_ADMIN_PATH =
            AI_AGENTSPEC_ADMIN_PATH + "/scope";
        
        public static final String AI_AGENTSPEC_BIZ_TAGS_ADMIN_PATH =
            AI_AGENTSPEC_ADMIN_PATH + "/biz-tags";
        
        public static final String AI_AGENTSPEC_UPLOAD_ADMIN_PATH =
            AI_AGENTSPEC_ADMIN_PATH + "/upload";
        
        public static final String AI_PIPELINE_ADMIN_PATH = "/v3/admin/ai/pipelines";
        
        public static final String AI_PIPELINE_LIST_ADMIN_PATH = AI_PIPELINE_ADMIN_PATH + "/list";
        
        public static final String AI_PIPELINE_DETAIL_ADMIN_PATH =
            AI_PIPELINE_ADMIN_PATH + "/detail";
    }
    
}
