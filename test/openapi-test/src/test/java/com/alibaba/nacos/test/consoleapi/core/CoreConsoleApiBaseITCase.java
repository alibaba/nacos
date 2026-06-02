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

package com.alibaba.nacos.test.consoleapi.core;

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.consoleapi.ConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Shared helpers for core console OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class CoreConsoleApiBaseITCase extends ConsoleApiBaseITCase {

    protected static final String CONSOLE_NAMESPACE_PATH = CONSOLE_BASE_PATH + "/core/namespace";

    protected static final String CONSOLE_NAMESPACE_LIST_PATH = CONSOLE_NAMESPACE_PATH + "/list";

    protected static final String CONSOLE_NAMESPACE_EXIST_PATH = CONSOLE_NAMESPACE_PATH + "/exist";

    protected static final String CONSOLE_CLUSTER_PATH = CONSOLE_BASE_PATH + "/core/cluster";

    protected static final String CONSOLE_CLUSTER_NODES_PATH = CONSOLE_CLUSTER_PATH + "/nodes";

    protected static final String CONSOLE_PLUGIN_PATH = CONSOLE_BASE_PATH + "/plugin";

    protected static final String CONSOLE_PLUGIN_LIST_PATH = CONSOLE_PLUGIN_PATH + "/list";

    protected static final String CONSOLE_PLUGIN_AVAILABILITY_PATH = CONSOLE_PLUGIN_PATH + "/availability";

    protected static final String CONSOLE_SERVER_PATH = CONSOLE_BASE_PATH + "/server";

    protected static final String CONSOLE_HEALTH_PATH = CONSOLE_BASE_PATH + "/health";

    protected String randomNamespaceId(String scenario) {
        return "oit_ns_" + scenario + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    protected Query namespaceCreateQuery(String namespaceId, String namespaceName, String namespaceDesc) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "customNamespaceId", namespaceId);
        addIfNotBlank(query, "namespaceName", namespaceName);
        addIfNotBlank(query, "namespaceDesc", namespaceDesc);
        return query;
    }

    protected Query namespaceUpdateQuery(String namespaceId, String namespaceName, String namespaceDesc) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "namespaceId", namespaceId);
        addIfNotBlank(query, "namespaceName", namespaceName);
        addIfNotBlank(query, "namespaceDesc", namespaceDesc);
        return query;
    }

    protected void deleteNamespaceQuietly(String namespaceId) throws Exception {
        deleteQuietly(CONSOLE_NAMESPACE_PATH, Query.newInstance().addParam("namespaceId", namespaceId));
    }

    protected JsonNode findNamespace(JsonNode namespaces, String namespaceId) {
        JsonNode namespace = findByTextField(namespaces, "namespace", namespaceId);
        if (!namespace.isMissingNode()) {
            return namespace;
        }
        return MissingNode.getInstance();
    }

    protected void assertNamespaceListed(JsonNode namespaces, String namespaceId) {
        assertFalse(findNamespace(namespaces, namespaceId).isMissingNode(), namespaces.toString());
    }
}
