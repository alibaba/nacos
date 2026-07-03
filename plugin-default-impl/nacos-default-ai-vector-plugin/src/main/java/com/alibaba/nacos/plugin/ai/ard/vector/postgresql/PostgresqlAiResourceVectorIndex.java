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

package com.alibaba.nacos.plugin.ai.ard.vector.postgresql;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.datasource.DataSourcePoolProperties;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorChunk;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL pgvector implementation for ARD embeddings.
 *
 * @author nacos
 */
public class PostgresqlAiResourceVectorIndex implements AiResourceVectorIndex {

    public static final String TYPE = "postgresql";

    private static final String POSTGRES = "postgres";

    static final String KEY_POSTGRESQL_URL = "nacos.ai.ard.vector.postgresql.url";

    static final String KEY_POSTGRESQL_USER = "nacos.ai.ard.vector.postgresql.user";

    static final String KEY_POSTGRESQL_PASSWORD = "nacos.ai.ard.vector.postgresql.password";

    static final String KEY_POSTGRESQL_DRIVER_CLASS_NAME =
        "nacos.ai.ard.vector.postgresql.driver-class-name";

    private static final String DEFAULT_POSTGRESQL_DRIVER_CLASS_NAME = "org.postgresql.Driver";

    private static final String SQL_INSERT = "INSERT INTO ai_resource_ard_embedding_pg "
        + "(namespace_id, entry_id, chunk_id, identifier, resource_type, resource_name, "
        + "resource_version, embedding_model, embedding_dimension, embedding, gmt_create, gmt_modified) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private final JdbcTemplate injectedJdbcTemplate;

    private volatile JdbcTemplate dedicatedJdbcTemplate;

    private volatile AutoCloseable dedicatedDataSource;

    public PostgresqlAiResourceVectorIndex() {
        this.injectedJdbcTemplate = null;
    }

    public PostgresqlAiResourceVectorIndex(JdbcTemplate jdbcTemplate) {
        this.injectedJdbcTemplate = jdbcTemplate;
    }

    @Override
    public void close() throws Exception {
        AutoCloseable dataSource = dedicatedDataSource;
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public boolean available() {
        if (!isPostgresql()) {
            return false;
        }
        try {
            getJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM ai_resource_ard_embedding_pg WHERE 1=0",
                Integer.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void replaceResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion,
        Collection<AiResourceVectorDocument> documents) {
        deleteByResourceVersion(namespaceId, resourceType, resourceName, resourceVersion);
        addDocuments(documents);
    }

    @Override
    public void addDocuments(Collection<AiResourceVectorDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (AiResourceVectorDocument document : documents) {
            insert(document);
        }
    }

    @Override
    public void deleteByResource(String namespaceId, String resourceType, String resourceName) {
        getJdbcTemplate().update("DELETE FROM ai_resource_ard_embedding_pg WHERE namespace_id=? "
            + "AND resource_type=? AND resource_name=?",
            namespaceId, resourceType, resourceName);
    }

    @Override
    public void deleteByResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion) {
        getJdbcTemplate().update("DELETE FROM ai_resource_ard_embedding_pg WHERE namespace_id=? "
            + "AND resource_type=? AND resource_name=? AND resource_version=?",
            namespaceId, resourceType, resourceName, resourceVersion);
    }

    @Override
    public List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
        double[] queryVector, List<String> resourceTypes, int limit) {
        if (StringUtils.isBlank(embeddingModel) || queryVector == null || queryVector.length == 0) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT entry_id, chunk_id, identifier, resource_type, resource_name, "
                + "resource_version, (1 - (embedding <=> ?::vector)) AS score "
                + "FROM ai_resource_ard_embedding_pg WHERE namespace_id=? "
                + "AND embedding_model=? AND embedding_dimension=?");
        args.add(toVectorLiteral(queryVector));
        args.add(namespaceId);
        args.add(embeddingModel);
        args.add(queryVector.length);
        appendResourceTypeFilter(sql, args, resourceTypes);
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(toVectorLiteral(queryVector));
        args.add(limit);
        return getJdbcTemplate().query(sql.toString(), (rs, rowNum) -> {
            AiResourceVectorHit hit = new AiResourceVectorHit();
            hit.setEntryId(rs.getLong("entry_id"));
            hit.setChunkId(rs.getLong("chunk_id"));
            hit.setIdentifier(rs.getString("identifier"));
            hit.setResourceType(rs.getString("resource_type"));
            hit.setResourceName(rs.getString("resource_name"));
            hit.setResourceVersion(rs.getString("resource_version"));
            hit.setScore(rs.getDouble("score"));
            return hit;
        }, args.toArray());
    }

    private void insert(AiResourceVectorDocument document) {
        AiResourceVectorChunk chunk = document.getChunk();
        getJdbcTemplate().update(SQL_INSERT, chunk.getNamespaceId(), chunk.getEntryId(),
            chunk.getId(), chunk.getIdentifier(), chunk.getResourceType(), chunk.getResourceName(),
            chunk.getResourceVersion(), document.getEmbeddingModel(),
            document.getEmbedding().length, toVectorLiteral(document.getEmbedding()));
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

    private String toVectorLiteral(double[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        result.append(']');
        return result.toString();
    }

    private JdbcTemplate getJdbcTemplate() {
        if (injectedJdbcTemplate != null) {
            return injectedJdbcTemplate;
        }
        String jdbcUrl = envProperty(KEY_POSTGRESQL_URL);
        if (StringUtils.isNotBlank(jdbcUrl)) {
            return getDedicatedJdbcTemplate(jdbcUrl);
        }
        return DynamicDataSource.getInstance().getDataSource().getJdbcTemplate();
    }

    JdbcTemplate getDedicatedJdbcTemplate(String jdbcUrl) {
        JdbcTemplate result = dedicatedJdbcTemplate;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            if (dedicatedJdbcTemplate == null) {
                dedicatedJdbcTemplate = createDedicatedJdbcTemplate(jdbcUrl);
            }
            return dedicatedJdbcTemplate;
        }
    }

    private JdbcTemplate createDedicatedJdbcTemplate(String jdbcUrl) {
        DataSourcePoolProperties poolProperties =
            DataSourcePoolProperties.build(EnvUtil.getEnvironment());
        poolProperties.setDriverClassName(envProperty(KEY_POSTGRESQL_DRIVER_CLASS_NAME,
            DEFAULT_POSTGRESQL_DRIVER_CLASS_NAME));
        poolProperties.setJdbcUrl(jdbcUrl.trim());
        String username = envProperty(KEY_POSTGRESQL_USER);
        if (StringUtils.isNotBlank(username)) {
            poolProperties.setUsername(username);
        }
        String password = envProperty(KEY_POSTGRESQL_PASSWORD);
        if (StringUtils.isNotBlank(password)) {
            poolProperties.setPassword(password);
        }
        DataSource dataSource = poolProperties.getDataSource();
        dedicatedDataSource = (AutoCloseable) dataSource;
        return new JdbcTemplate(dataSource);
    }

    private boolean isPostgresql() {
        if (injectedJdbcTemplate != null) {
            return true;
        }
        if (StringUtils.isNotBlank(envProperty(KEY_POSTGRESQL_URL))) {
            return true;
        }
        try {
            String dataSourceType =
                DynamicDataSource.getInstance().getDataSource().getDataSourceType();
            return TYPE.equalsIgnoreCase(dataSourceType) || POSTGRES.equalsIgnoreCase(dataSourceType);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String envProperty(String key) {
        return envProperty(key, StringUtils.EMPTY);
    }

    private String envProperty(String key, String defaultValue) {
        try {
            if (EnvUtil.getEnvironment() == null) {
                return defaultValue;
            }
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
