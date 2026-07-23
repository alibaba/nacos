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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.ai.model.ard.ArdChunk;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.model.ard.ArdSearchHit;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * JDBC implementation for ARD entry and chunk index tables.
 *
 * @author nacos
 */
@Repository
@ConditionalOnArdEnabled
public class JdbcArdIndexRepository implements ArdIndexRepository {
    
    private static final String SQL_INSERT_ENTRY = "INSERT INTO ai_resource_ard_entry "
        + "(namespace_id, resource_type, resource_name, resource_version, display_name, c_desc, "
        + "tags, capabilities, representative_queries, metadata, source_digest, status, "
        + "generate_mode, gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    
    private static final String SQL_INSERT_CHUNK = "INSERT INTO ai_resource_ard_chunk "
        + "(entry_id, namespace_id, resource_type, resource_name, resource_version, "
        + "chunk_type, chunk_text, canonical_text, language, chunk_hash, metadata, status, gmt_create, gmt_modified) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    
    private static final RowMapper<ArdEntry> ENTRY_ROW_MAPPER = new ArdEntryRowMapper();
    
    private static final RowMapper<ArdSearchHit> HIT_ROW_MAPPER = new ArdSearchHitRowMapper();
    
    private final JdbcTemplate injectedJdbcTemplate;
    
    private final TransactionTemplate injectedTransactionTemplate;
    
    public JdbcArdIndexRepository() {
        this.injectedJdbcTemplate = null;
        this.injectedTransactionTemplate = null;
    }
    
    public JdbcArdIndexRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, transactionTemplate(jdbcTemplate));
    }
    
    JdbcArdIndexRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
        this.injectedTransactionTemplate = transactionTemplate;
    }
    
    @Override
    public List<ArdChunk> replaceEntry(ArdEntry entry, List<ArdChunk> chunks) {
        return getTransactionTemplate().execute(status -> {
            deleteByResourceVersionWithoutTransaction(entry.getNamespaceId(),
                entry.getResourceType(), entry.getResourceName(), entry.getResourceVersion());
            long entryId = insertEntry(entry);
            entry.setId(entryId);
            return appendChunks(entry, chunks);
        });
    }
    
    @Override
    public List<ArdChunk> appendChunks(ArdEntry entry, List<ArdChunk> chunks) {
        List<ArdChunk> result = new ArrayList<>();
        if (entry == null || entry.getId() == null || chunks == null || chunks.isEmpty()
            || !entryExists(entry)) {
            return result;
        }
        for (ArdChunk chunk : chunks) {
            chunk.setEntryId(entry.getId());
            chunk.setNamespaceId(entry.getNamespaceId());
            chunk.setResourceType(entry.getResourceType());
            chunk.setResourceName(entry.getResourceName());
            chunk.setResourceVersion(entry.getResourceVersion());
            result.add(insertChunk(chunk));
        }
        return result;
    }
    
    @Override
    public void updateEntryStatus(long entryId, String status) {
        getJdbcTemplate().update("UPDATE ai_resource_ard_entry SET status=?, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE id=?", status, entryId);
    }
    
    @Override
    public void deleteByResource(String namespaceId, String resourceType, String resourceName) {
        List<Object> args = new ArrayList<>();
        String where = "namespace_id=? AND resource_type=? AND resource_name=?";
        args.add(namespaceId);
        args.add(resourceType);
        args.add(resourceName);
        getTransactionTemplate().executeWithoutResult(status -> deleteByWhere(where, args));
    }
    
    @Override
    public void deleteByResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion) {
        getTransactionTemplate()
            .executeWithoutResult(status -> deleteByResourceVersionWithoutTransaction(
                namespaceId, resourceType, resourceName, resourceVersion));
    }
    
    private void deleteByResourceVersionWithoutTransaction(String namespaceId,
        String resourceType, String resourceName, String resourceVersion) {
        List<Object> args = new ArrayList<>();
        String where = "namespace_id=? AND resource_type=? AND resource_name=? "
            + "AND resource_version=?";
        args.add(namespaceId);
        args.add(resourceType);
        args.add(resourceName);
        args.add(resourceVersion);
        deleteByWhere(where, args);
    }
    
    @Override
    public ArdEntry findEntry(String namespaceId, String resourceType, String resourceName) {
        List<ArdEntry> entries = getJdbcTemplate().query(
            "SELECT * FROM ai_resource_ard_entry WHERE namespace_id=? AND resource_type=? "
                + "AND resource_name=? ORDER BY id DESC",
            ENTRY_ROW_MAPPER, namespaceId, resourceType, resourceName);
        return entries.isEmpty() ? null : entries.get(0);
    }
    
    @Override
    public List<ArdEntry> findEntriesByIds(Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>();
        String placeholders = placeholders(entryIds.size());
        args.addAll(entryIds);
        return getJdbcTemplate().query(
            "SELECT * FROM ai_resource_ard_entry WHERE id IN (" + placeholders + ")",
            ENTRY_ROW_MAPPER, args.toArray());
    }
    
    @Override
    public List<ArdSearchHit> searchChunks(String namespaceId, String text,
        List<String> resourceTypes, int limit) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT entry_id, id AS chunk_id, resource_type, resource_name, "
                + "resource_version, chunk_type, CASE WHEN LOWER(canonical_text) LIKE ? THEN 1.0 "
                + "WHEN LOWER(chunk_text) LIKE ? THEN 0.8 ELSE 0.4 END AS score "
                + "FROM ai_resource_ard_chunk WHERE namespace_id=? AND status=? "
                + "AND (LOWER(canonical_text) LIKE ? OR LOWER(chunk_text) LIKE ?)");
        String like = "%" + text.toLowerCase(Locale.ROOT) + "%";
        args.add(like);
        args.add(like);
        args.add(namespaceId);
        args.add(ArdIndexConstants.STATUS_ENABLED);
        args.add(like);
        args.add(like);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY score DESC");
        List<ArdSearchHit> hits =
            getJdbcTemplate().query(sql.toString(), HIT_ROW_MAPPER, args.toArray());
        return limit(hits, limit);
    }
    
    @Override
    public List<ArdEntry> listEnabledEntries(String namespaceId, List<String> resourceTypes,
        int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
            new StringBuilder("SELECT * FROM ai_resource_ard_entry WHERE namespace_id=? "
                + "AND status=?");
        args.add(namespaceId);
        args.add(ArdIndexConstants.STATUS_ENABLED);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY gmt_modified DESC");
        List<ArdEntry> entries =
            getJdbcTemplate().query(sql.toString(), ENTRY_ROW_MAPPER, args.toArray());
        return limit(entries, limit);
    }
    
    @Override
    public List<ArdEntry> listEntries(String namespaceId, List<String> resourceTypes, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
            new StringBuilder("SELECT * FROM ai_resource_ard_entry WHERE namespace_id=?");
        args.add(namespaceId);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY gmt_modified DESC");
        List<ArdEntry> entries =
            getJdbcTemplate().query(sql.toString(), ENTRY_ROW_MAPPER, args.toArray());
        return limit(entries, limit);
    }
    
    @Override
    public int countChunks(long entryId) {
        Integer count = getJdbcTemplate().queryForObject(
            "SELECT COUNT(1) FROM ai_resource_ard_chunk WHERE entry_id=?", Integer.class,
            entryId);
        return count == null ? 0 : count;
    }
    
    private long insertEntry(ArdEntry entry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(SQL_INSERT_ENTRY,
                new String[] {"id"});
            ps.setString(1, entry.getNamespaceId());
            ps.setString(2, entry.getResourceType());
            ps.setString(3, entry.getResourceName());
            ps.setString(4, entry.getResourceVersion());
            ps.setString(5, entry.getDisplayName());
            ps.setString(6, entry.getDescription());
            ps.setString(7, entry.getTags());
            ps.setString(8, entry.getCapabilities());
            ps.setString(9, entry.getRepresentativeQueries());
            ps.setString(10, entry.getMetadata());
            ps.setString(11, entry.getSourceDigest());
            ps.setString(12, entry.getStatus());
            ps.setString(13, entry.getGenerateMode());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                "insert ai_resource_ard_entry failed, no generated key");
        }
        return key.longValue();
    }
    
    private ArdChunk insertChunk(ArdChunk chunk) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        getJdbcTemplate().update(connection -> {
            PreparedStatement ps = connection.prepareStatement(SQL_INSERT_CHUNK,
                new String[] {"id"});
            ps.setLong(1, chunk.getEntryId());
            ps.setString(2, chunk.getNamespaceId());
            ps.setString(3, chunk.getResourceType());
            ps.setString(4, chunk.getResourceName());
            ps.setString(5, chunk.getResourceVersion());
            ps.setString(6, chunk.getChunkType());
            ps.setString(7, chunk.getChunkText());
            ps.setString(8, chunk.getCanonicalText());
            ps.setString(9, chunk.getLanguage());
            ps.setString(10, chunk.getChunkHash());
            ps.setString(11, chunk.getMetadata());
            ps.setString(12, chunk.getStatus());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                "insert ai_resource_ard_chunk failed, no generated key");
        }
        chunk.setId(key.longValue());
        return chunk;
    }
    
    private boolean entryExists(ArdEntry entry) {
        Integer count = getJdbcTemplate().queryForObject(
            "SELECT COUNT(1) FROM ai_resource_ard_entry WHERE id=? AND namespace_id=? "
                + "AND resource_type=? AND resource_name=? AND resource_version=?",
            Integer.class, entry.getId(), entry.getNamespaceId(), entry.getResourceType(),
            entry.getResourceName(), entry.getResourceVersion());
        return count != null && count > 0;
    }
    
    private void deleteByWhere(String where, List<Object> args) {
        getJdbcTemplate().update("DELETE FROM ai_resource_ard_chunk WHERE entry_id IN "
            + "(SELECT id FROM ai_resource_ard_entry WHERE " + where + ")", args.toArray());
        getJdbcTemplate().update("DELETE FROM ai_resource_ard_entry WHERE " + where,
            args.toArray());
    }
    
    private void appendResourceTypeFilter(StringBuilder sql, List<Object> args,
        List<String> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return;
        }
        sql.append(" AND resource_type IN (").append(placeholders(resourceTypes.size()))
            .append(")");
        args.addAll(resourceTypes);
    }
    
    private String placeholders(int size) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append("?");
        }
        return result.toString();
    }
    
    private <T> List<T> limit(List<T> values, int limit) {
        if (values == null || values.size() <= limit) {
            return values == null ? Collections.emptyList() : values;
        }
        return new ArrayList<>(values.subList(0, limit));
    }
    
    private JdbcTemplate getJdbcTemplate() {
        if (injectedJdbcTemplate != null) {
            return injectedJdbcTemplate;
        }
        return DynamicDataSource.getInstance().getDataSource().getJdbcTemplate();
    }
    
    private TransactionTemplate getTransactionTemplate() {
        if (injectedTransactionTemplate != null) {
            return injectedTransactionTemplate;
        }
        return DynamicDataSource.getInstance().getDataSource().getTransactionTemplate();
    }
    
    private static TransactionTemplate transactionTemplate(JdbcTemplate jdbcTemplate) {
        DataSource dataSource = jdbcTemplate == null ? null : jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return null;
        }
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
    
    private static class ArdEntryRowMapper implements RowMapper<ArdEntry> {
        
        @Override
        public ArdEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            ArdEntry entry = new ArdEntry();
            entry.setId(rs.getLong("id"));
            entry.setGmtCreate(rs.getTimestamp("gmt_create"));
            entry.setGmtModified(rs.getTimestamp("gmt_modified"));
            entry.setNamespaceId(rs.getString("namespace_id"));
            entry.setResourceType(rs.getString("resource_type"));
            entry.setResourceName(rs.getString("resource_name"));
            entry.setResourceVersion(rs.getString("resource_version"));
            entry.setDisplayName(rs.getString("display_name"));
            entry.setDescription(rs.getString("c_desc"));
            entry.setTags(rs.getString("tags"));
            entry.setCapabilities(rs.getString("capabilities"));
            entry.setRepresentativeQueries(rs.getString("representative_queries"));
            entry.setMetadata(rs.getString("metadata"));
            entry.setSourceDigest(rs.getString("source_digest"));
            entry.setStatus(rs.getString("status"));
            entry.setGenerateMode(rs.getString("generate_mode"));
            return entry;
        }
    }
    
    private static class ArdSearchHitRowMapper implements RowMapper<ArdSearchHit> {
        
        @Override
        public ArdSearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
            ArdSearchHit hit = new ArdSearchHit();
            hit.setEntryId(rs.getLong("entry_id"));
            hit.setChunkId(rs.getLong("chunk_id"));
            hit.setResourceType(rs.getString("resource_type"));
            hit.setResourceName(rs.getString("resource_name"));
            hit.setResourceVersion(rs.getString("resource_version"));
            hit.setChunkType(rs.getString("chunk_type"));
            hit.setScore(rs.getDouble("score"));
            return hit;
        }
    }
}
