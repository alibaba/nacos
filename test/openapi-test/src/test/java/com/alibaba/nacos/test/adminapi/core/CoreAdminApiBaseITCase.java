/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.adminapi.core;

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;

import java.util.UUID;

/**
 * Shared helpers for core admin OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class CoreAdminApiBaseITCase extends OpenApiBaseITCase {

    protected static final String ADMIN_CORE_NAMESPACE_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/namespace");

    protected static final String ADMIN_CORE_CLUSTER_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/cluster");

    protected static final String ADMIN_CORE_PLUGIN_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/plugin");

    protected static final String ADMIN_CORE_LOADER_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/loader");

    protected static final String ADMIN_CORE_OPS_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/ops");

    protected static final String ADMIN_CORE_STATE_PATH =
            nacosPath(Commons.NACOS_ADMIN_CORE_CONTEXT_V3 + "/state");

    protected String randomNamespaceId(String scenario) {
        return "openapi_it_admin_" + scenario + "_" + UUID.randomUUID();
    }

    protected Query namespaceQuery(String namespaceId, String namespaceName, String namespaceDesc) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "namespaceId", namespaceId);
        addIfNotBlank(query, "namespaceName", namespaceName);
        addIfNotBlank(query, "namespaceDesc", namespaceDesc);
        return query;
    }

    protected void deleteNamespaceQuietly(String namespaceId) throws Exception {
        deleteQuietly(ADMIN_CORE_NAMESPACE_PATH, Query.newInstance().addParam("namespaceId", namespaceId));
    }
}
