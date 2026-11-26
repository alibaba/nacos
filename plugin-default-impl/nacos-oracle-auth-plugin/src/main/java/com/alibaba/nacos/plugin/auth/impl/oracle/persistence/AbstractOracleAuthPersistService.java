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

package com.alibaba.nacos.plugin.auth.impl.oracle.persistence;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.auth.impl.oracle.OracleAuthPluginConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.AuthPaginationHelper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class of the Oracle adapted auth persist services. Wires the external storage
 * {@link JdbcTemplate}, guards that the datasource dialect really is Oracle, and provides the
 * Oracle adapted pagination helper and the shared fuzzy-search argument translation to the
 * concrete persist services.
 *
 * <p>The default auth plugin is intentionally untouched by this plugin: the Oracle adaptions
 * (reserved-word column, {@code OFFSET/FETCH} pagination) live entirely in this module, and the
 * concrete services take over the default ones via primary bean definitions.</p>
 *
 * @author liam
 */
public abstract class AbstractOracleAuthPersistService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.persistence");
    
    private static final String PATTERN_STR = "*";
    
    /**
     * Oracle has no default LIKE escape character, so the {@code \_} produced by
     * {@link #generateLikeArgument(String)} is only honored with an explicit escape clause; this
     * mirrors the escape clause the default plugin appends for embedded Derby storage.
     */
    protected static final String SQL_ORACLE_ESCAPE_BACK_SLASH_FOR_LIKE = " ESCAPE '\\' ";
    
    protected JdbcTemplate jt;
    
    private String dataSourceType = "";
    
    @PostConstruct
    protected void init() {
        DataSourceService dataSource = DynamicDataSource.getInstance().getDataSource();
        jt = dataSource.getJdbcTemplate();
        dataSourceType = dataSource.getDataSourceType();
        if (!OracleAuthPluginConstants.ORACLE_DATA_SOURCE_TYPE.equalsIgnoreCase(dataSourceType)) {
            throw new IllegalStateException(
                "nacos-oracle-auth-plugin is activated, but the current datasource dialect is '"
                    + dataSourceType + "'. Please align "
                    + PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY
                    + " with the actual datasource, or remove this plugin if Oracle storage is not used.");
        }
        LOGGER.info(
            "nacos-oracle-auth-plugin is serving auth persistence for datasource dialect '{}'.",
            dataSourceType);
    }
    
    /**
     * Create pagination utils with Oracle {@code OFFSET/FETCH} syntax.
     *
     * @param <E> Generic object
     * @return {@link AuthPaginationHelper}
     */
    public <E> AuthPaginationHelper<E> createPaginationHelper() {
        return new OracleAuthPaginationHelper<>(jt);
    }
    
    /**
     * Generate fuzzy search Sql, translated from the console wildcard {@code *} to SQL {@code %}.
     *
     * @param s origin string
     * @return fuzzy search Sql
     */
    public String generateLikeArgument(String s) {
        String underscore = "_";
        if (s.contains(underscore)) {
            s = s.replaceAll(underscore, "\\\\_");
        }
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
}
