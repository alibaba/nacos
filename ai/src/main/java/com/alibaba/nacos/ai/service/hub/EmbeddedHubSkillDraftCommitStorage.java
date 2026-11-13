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

import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/** Embedded SQL mutations execute through Nacos's consensus-aware database operate API. */
@Service
@Conditional(value = ConditionOnEmbeddedStorage.class)
public class EmbeddedHubSkillDraftCommitStorage implements HubSkillDraftCommitStorage {
    
    private final DatabaseOperate database;
    
    public EmbeddedHubSkillDraftCommitStorage(DatabaseOperate database) {
        this.database = database;
    }
    
    @Override
    public boolean publish(long id, String namespaceId, String name, long expectedMetaVersion) {
        return change(id, namespaceId, name, expectedMetaVersion, "disable", "enable");
    }
    
    @Override
    public boolean disable(long id, String namespaceId, String name, long expectedMetaVersion) {
        return change(id, namespaceId, name, expectedMetaVersion, "enable", "disable");
    }
    
    private boolean change(long id, String ns, String name, long version,
        String status, String next) {
        String sql = "UPDATE ai_resource SET status=?, meta_version=meta_version+1 "
            + "WHERE id=? AND namespace_id=? AND name=? AND type='skill' AND c_from='local' "
            + "AND status=? AND meta_version=?";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {next, id, ns, name, status, version});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
    
    @Override
    public boolean deleteDisabled(long id, String ns, String name, long version) {
        String sql = "DELETE FROM ai_resource WHERE id=? AND namespace_id=? AND name=? "
            + "AND type='skill' AND c_from='local' AND status='disable' AND meta_version=?";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {id, ns, name, version});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
    
    @Override
    public boolean deleteDraftVersion(long id, String namespaceId, String name, long metaVersion,
        long versionId, String version) {
        String sql = "DELETE FROM ai_resource_version WHERE id=? AND namespace_id=? AND name=? "
            + "AND type='skill' AND version=? AND status='draft' AND EXISTS "
            + "(SELECT 1 FROM ai_resource WHERE id=? AND namespace_id=? AND name=? "
            + "AND type='skill' AND c_from='local' AND status='disable' AND meta_version=?)";
        EmbeddedStorageContextHolder.addSqlContext(true, sql,
            new Object[] {versionId, namespaceId, name, version, id, namespaceId, name,
                metaVersion});
        return Boolean.TRUE.equals(database.blockUpdate());
    }
}
