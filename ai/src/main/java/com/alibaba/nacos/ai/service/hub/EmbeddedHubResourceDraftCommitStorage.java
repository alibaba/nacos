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

import com.alibaba.nacos.ai.model.hub.HubResourceType;

import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/** Embedded SQL mutations execute through Nacos's consensus-aware database operate API. */
@Service
@Conditional(value = ConditionOnEmbeddedStorage.class)
public class EmbeddedHubResourceDraftCommitStorage implements HubResourceDraftCommitStorage {
    
    private final DatabaseOperate database;
    
    public EmbeddedHubResourceDraftCommitStorage(DatabaseOperate database) {
        this.database = database;
    }
    
    @Override
    public boolean publish(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion) {
        return change(type, id, namespaceId, name, expectedMetaVersion, "disable", "enable");
    }
    
    @Override
    public boolean disable(HubResourceType type, long id, String namespaceId, String name,
        long expectedMetaVersion) {
        return change(type, id, namespaceId, name, expectedMetaVersion, "enable", "disable");
    }
    
    private boolean change(HubResourceType type, long id, String ns, String name, long version,
        String status, String next) {
        String sql = "UPDATE ai_resource SET status=?, meta_version=meta_version+1 "
            + "WHERE id=? AND namespace_id=? AND name=? AND type=? AND c_from='local' "
            + "AND status=? AND meta_version=?";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {next, id, ns, name, type.resourceType(), status, version});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
    
    @Override
    public boolean deleteDisabled(HubResourceType type, long id, String ns, String name,
        long version) {
        String sql = "DELETE FROM ai_resource WHERE id=? AND namespace_id=? AND name=? "
            + "AND type=? AND c_from='local' AND status='disable' AND meta_version=?";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {id, ns, name, type.resourceType(), version});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
    
    @Override
    public boolean deleteDraftVersion(HubResourceType type, long id, String namespaceId,
        String name, long metaVersion,
        long versionId, String version) {
        String sql = "DELETE FROM ai_resource_version WHERE id=? AND namespace_id=? AND name=? "
            + "AND type=? AND version=? AND status='draft' AND EXISTS "
            + "(SELECT 1 FROM ai_resource WHERE id=? AND namespace_id=? AND name=? "
            + "AND type=? AND c_from='local' AND status='disable' AND meta_version=?)";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {versionId, namespaceId, name, type.resourceType(), version, id,
                namespaceId, name, type.resourceType(),
                metaVersion});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
}
