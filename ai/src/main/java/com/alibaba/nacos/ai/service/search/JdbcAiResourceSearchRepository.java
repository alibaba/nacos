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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.model.search.AiResourceSearchChunk;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.model.search.AiResourceSearchHit;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * JDBC implementation for search document and chunk index tables.
 *
 * @author nacos
 */
@Repository
@ConditionalOnAiResourceSearchEnabled
public class JdbcAiResourceSearchRepository implements AiResourceSearchRepository {
    
    private static final String SQL_INSERT_ENTRY = "INSERT INTO ai_resource_search_document "
        + "(namespace_id, resource_type, resource_name, resource_version, display_name, c_desc, "
        + "tags, capabilities, representative_queries, metadata, source_digest, status, "
        + "generate_mode, gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    
    private static final String SQL_INSERT_CHUNK = "INSERT INTO ai_resource_search_chunk "
        + "(document_id, namespace_id, resource_type, resource_name, resource_version, "
        + "chunk_type, chunk_text, canonical_text, language, chunk_hash, metadata, status, gmt_create, gmt_modified) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    
    private static final RowMapper<AiResourceSearchDocument> ENTRY_ROW_MAPPER =
        new AiResourceSearchDocumentRowMapper();
    
    private static final RowMapper<AiResourceSearchHit> HIT_ROW_MAPPER =
        new AiResourceSearchHitRowMapper();
    
    private static final RowMapper<AiResourceSearchChunk> CHUNK_ROW_MAPPER =
        new AiResourceSearchChunkRowMapper();
    
    private final JdbcTemplate injectedJdbcTemplate;
    
    private final TransactionTemplate injectedTransactionTemplate;
    
    public JdbcAiResourceSearchRepository() {
        this.injectedJdbcTemplate = null;
        this.injectedTransactionTemplate = null;
    }
    
    public JdbcAiResourceSearchRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, transactionTemplate(jdbcTemplate));
    }
    
    JdbcAiResourceSearchRepository(JdbcTemplate jdbcTemplate,
        TransactionTemplate transactionTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
        this.injectedTransactionTemplate = transactionTemplate;
    }
    
    @Override
    public List<AiResourceSearchChunk> replaceEntry(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> chunks) {
        return getTransactionTemplate().execute(status -> {
            deleteByResourceVersionWithoutTransaction(entry.getNamespaceId(),
                entry.getResourceType(), entry.getResourceName(), entry.getResourceVersion());
            long documentId = insertEntry(entry);
            entry.setId(documentId);
            return appendChunks(entry, chunks);
        });
    }
    
    @Override
    public List<AiResourceSearchChunk> appendChunks(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> chunks) {
        List<AiResourceSearchChunk> result = new ArrayList<>();
        if (entry == null || entry.getId() == null || chunks == null || chunks.isEmpty()
            || !entryExists(entry)) {
            return result;
        }
        for (AiResourceSearchChunk chunk : chunks) {
            chunk.setDocumentId(entry.getId());
            chunk.setNamespaceId(entry.getNamespaceId());
            chunk.setResourceType(entry.getResourceType());
            chunk.setResourceName(entry.getResourceName());
            chunk.setResourceVersion(entry.getResourceVersion());
            result.add(insertChunk(chunk));
        }
        return result;
    }
    
    @Override
    public List<AiResourceSearchChunk> replaceEnhancementChunks(AiResourceSearchDocument entry,
        List<AiResourceSearchChunk> chunks) {
        return getTransactionTemplate().execute(status -> {
            if (entry == null || entry.getId() == null || !entryExists(entry)) {
                throw new IllegalStateException("AI resource search document changed");
            }
            getJdbcTemplate().update("DELETE FROM ai_resource_search_chunk WHERE document_id=? "
                + "AND chunk_type IN (?, ?, ?)", entry.getId(),
                AiResourceSearchConstants.CHUNK_TYPE_AI_SUMMARY,
                AiResourceSearchConstants.CHUNK_TYPE_SEARCH_INTENT,
                AiResourceSearchConstants.CHUNK_TYPE_SEARCH_TERM);
            appendChunks(entry, chunks);
            return listChunks(entry.getId());
        });
    }
    
    @Override
    public List<AiResourceSearchChunk> listChunks(long documentId) {
        return getJdbcTemplate().query(
            "SELECT * FROM ai_resource_search_chunk WHERE document_id=? ORDER BY id",
            CHUNK_ROW_MAPPER, documentId);
    }
    
    @Override
    public void updateEntryStatus(long documentId, String status) {
        getJdbcTemplate().update("UPDATE ai_resource_search_document SET status=?, "
            + "gmt_modified=CURRENT_TIMESTAMP WHERE id=?", status, documentId);
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
    public AiResourceSearchDocument findEntry(String namespaceId, String resourceType,
        String resourceName) {
        List<AiResourceSearchDocument> entries = queryWithMaxRows(
            "SELECT * FROM ai_resource_search_document WHERE namespace_id=? AND resource_type=? "
                + "AND resource_name=? ORDER BY id DESC",
            List.of(namespaceId, resourceType, resourceName), ENTRY_ROW_MAPPER, 1);
        return entries.isEmpty() ? null : entries.get(0);
    }
    
    @Override
    public List<AiResourceSearchDocument> findEntriesByIds(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>();
        String placeholders = placeholders(documentIds.size());
        args.addAll(documentIds);
        return getJdbcTemplate().query(
            "SELECT * FROM ai_resource_search_document WHERE id IN (" + placeholders + ")",
            ENTRY_ROW_MAPPER, args.toArray());
    }
    
    @Override
    public List<AiResourceSearchHit> searchChunks(String namespaceId, String text,
        List<String> resourceTypes, int limit) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT document_id, id AS chunk_id, resource_type, resource_name, "
                + "resource_version, chunk_type, CASE WHEN LOWER(canonical_text) LIKE ? THEN 1.0 "
                + "WHEN LOWER(chunk_text) LIKE ? THEN 0.8 ELSE 0.4 END AS score "
                + "FROM ai_resource_search_chunk WHERE namespace_id=? AND status=? "
                + "AND (LOWER(canonical_text) LIKE ? OR LOWER(chunk_text) LIKE ?)");
        String like = "%" + text.toLowerCase(Locale.ROOT) + "%";
        args.add(like);
        args.add(like);
        args.add(namespaceId);
        args.add(AiResourceSearchConstants.STATUS_ENABLED);
        args.add(like);
        args.add(like);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY score DESC");
        return queryWithMaxRows(sql.toString(), args, HIT_ROW_MAPPER, limit);
    }
    
    @Override
    public List<AiResourceSearchDocument> listEnabledEntries(String namespaceId,
        List<String> resourceTypes,
        int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
            new StringBuilder("SELECT * FROM ai_resource_search_document WHERE namespace_id=? "
                + "AND status=?");
        args.add(namespaceId);
        args.add(AiResourceSearchConstants.STATUS_ENABLED);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY gmt_modified DESC");
        return queryWithMaxRows(sql.toString(), args, ENTRY_ROW_MAPPER, limit);
    }
    
    @Override
    public List<AiResourceSearchDocument> listEntries(String namespaceId,
        List<String> resourceTypes, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
            new StringBuilder("SELECT * FROM ai_resource_search_document WHERE namespace_id=?");
        args.add(namespaceId);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY gmt_modified DESC");
        return queryWithMaxRows(sql.toString(), args, ENTRY_ROW_MAPPER, limit);
    }
    
    @Override
    public List<AiResourceSearchDocument> scanEnabledEntries(String namespaceId,
        List<String> resourceTypes, long afterId, int limit) {
        return scanEntriesBatch(namespaceId, resourceTypes, afterId, limit, true);
    }
    
    @Override
    public List<AiResourceSearchDocument> scanEnabledEntriesByResourceKey(String namespaceId,
        List<String> resourceTypes, String afterResourceType, String afterResourceName,
        long afterId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM ai_resource_search_document WHERE namespace_id=? AND status=?");
        args.add(namespaceId);
        args.add(AiResourceSearchConstants.STATUS_ENABLED);
        appendResourceTypeFilter(sql, args, resourceTypes);
        if (afterResourceType != null) {
            sql.append(" AND (resource_type>? OR (resource_type=? AND resource_name>?) "
                + "OR (resource_type=? AND resource_name=? AND id>?))");
            args.add(afterResourceType);
            args.add(afterResourceType);
            args.add(afterResourceName);
            args.add(afterResourceType);
            args.add(afterResourceName);
            args.add(afterId);
        }
        sql.append(" ORDER BY resource_type, resource_name, id");
        return queryWithMaxRows(sql.toString(), args, ENTRY_ROW_MAPPER, limit);
    }
    
    @Override
    public List<AiResourceSearchDocument> scanEntries(String namespaceId,
        List<String> resourceTypes, long afterId, int limit) {
        return scanEntriesBatch(namespaceId, resourceTypes, afterId, limit, false);
    }
    
    @Override
    public int countChunks(long documentId) {
        Integer count = getJdbcTemplate().queryForObject(
            "SELECT COUNT(1) FROM ai_resource_search_chunk WHERE document_id=?", Integer.class,
            documentId);
        return count == null ? 0 : count;
    }
    
    private long insertEntry(AiResourceSearchDocument entry) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        getJdbcTemplate().update(connection -> {
            PreparedStatement ps = prepareStatementWithGeneratedKey(connection,
                SQL_INSERT_ENTRY);
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
                "insert ai_resource_search_document failed, no generated key");
        }
        return key.longValue();
    }
    
    private AiResourceSearchChunk insertChunk(AiResourceSearchChunk chunk) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        getJdbcTemplate().update(connection -> {
            PreparedStatement ps = prepareStatementWithGeneratedKey(connection,
                SQL_INSERT_CHUNK);
            ps.setLong(1, chunk.getDocumentId());
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
                "insert ai_resource_search_chunk failed, no generated key");
        }
        chunk.setId(key.longValue());
        return chunk;
    }
    
    private PreparedStatement prepareStatementWithGeneratedKey(Connection connection, String sql)
        throws SQLException {
        String primaryKey = connection.getMetaData().storesUpperCaseIdentifiers() ? "ID" : "id";
        return connection.prepareStatement(sql, new String[] {primaryKey});
    }
    
    private boolean entryExists(AiResourceSearchDocument entry) {
        Integer count = getJdbcTemplate().queryForObject(
            "SELECT COUNT(1) FROM ai_resource_search_document WHERE id=? AND namespace_id=? "
                + "AND resource_type=? AND resource_name=? AND resource_version=?",
            Integer.class, entry.getId(), entry.getNamespaceId(), entry.getResourceType(),
            entry.getResourceName(), entry.getResourceVersion());
        return count != null && count > 0;
    }
    
    private void deleteByWhere(String where, List<Object> args) {
        getJdbcTemplate().update("DELETE FROM ai_resource_search_chunk WHERE document_id IN "
            + "(SELECT id FROM ai_resource_search_document WHERE " + where + ")", args.toArray());
        getJdbcTemplate().update("DELETE FROM ai_resource_search_document WHERE " + where,
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
    
    private List<AiResourceSearchDocument> scanEntriesBatch(String namespaceId,
        List<String> resourceTypes, long afterId, int limit, boolean enabledOnly) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
            new StringBuilder("SELECT * FROM ai_resource_search_document WHERE namespace_id=? "
                + "AND id>?");
        args.add(namespaceId);
        args.add(afterId);
        if (enabledOnly) {
            sql.append(" AND status=?");
            args.add(AiResourceSearchConstants.STATUS_ENABLED);
        }
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY id");
        return queryWithMaxRows(sql.toString(), args, ENTRY_ROW_MAPPER, limit);
    }
    
    private <T> List<T> queryWithMaxRows(String sql, List<Object> args, RowMapper<T> rowMapper,
        int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return getJdbcTemplate().query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < args.size(); i++) {
                statement.setObject(i + 1, args.get(i));
            }
            statement.setMaxRows(limit);
            return statement;
        }, rowMapper);
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
    
    private static class AiResourceSearchDocumentRowMapper
        implements RowMapper<AiResourceSearchDocument> {
        
        @Override
        public AiResourceSearchDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiResourceSearchDocument entry = new AiResourceSearchDocument();
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
    
    private static class AiResourceSearchHitRowMapper implements RowMapper<AiResourceSearchHit> {
        
        @Override
        public AiResourceSearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiResourceSearchHit hit = new AiResourceSearchHit();
            hit.setDocumentId(rs.getLong("document_id"));
            hit.setChunkId(rs.getLong("chunk_id"));
            hit.setResourceType(rs.getString("resource_type"));
            hit.setResourceName(rs.getString("resource_name"));
            hit.setResourceVersion(rs.getString("resource_version"));
            hit.setChunkType(rs.getString("chunk_type"));
            hit.setScore(rs.getDouble("score"));
            return hit;
        }
    }
    
    private static class AiResourceSearchChunkRowMapper
        implements RowMapper<AiResourceSearchChunk> {
        
        @Override
        public AiResourceSearchChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiResourceSearchChunk chunk = new AiResourceSearchChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setGmtCreate(rs.getTimestamp("gmt_create"));
            chunk.setGmtModified(rs.getTimestamp("gmt_modified"));
            chunk.setDocumentId(rs.getLong("document_id"));
            chunk.setNamespaceId(rs.getString("namespace_id"));
            chunk.setResourceType(rs.getString("resource_type"));
            chunk.setResourceName(rs.getString("resource_name"));
            chunk.setResourceVersion(rs.getString("resource_version"));
            chunk.setChunkType(rs.getString("chunk_type"));
            chunk.setChunkText(rs.getString("chunk_text"));
            chunk.setCanonicalText(rs.getString("canonical_text"));
            chunk.setLanguage(rs.getString("language"));
            chunk.setChunkHash(rs.getString("chunk_hash"));
            chunk.setMetadata(rs.getString("metadata"));
            chunk.setStatus(rs.getString("status"));
            return chunk;
        }
    }
}
