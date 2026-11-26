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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthPageConstant;
import com.alibaba.nacos.plugin.auth.impl.persistence.AuthPaginationHelper;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Oracle adapted auth pagination helper. Appends the ANSI {@code OFFSET ? ROWS FETCH NEXT ? ROWS
 * ONLY} clause (supported since Oracle 12c) to the fetch SQL instead of the MySQL dialect
 * clauses used by the default auth plugin, so this plugin does not depend on the default
 * {@code PageHandlerAdapterFactory}, which has no Oracle adapter.
 *
 * @param <E> Generic object
 * @author liam
 */
public class OracleAuthPaginationHelper<E> implements AuthPaginationHelper<E> {
    
    private static final String ORACLE_FETCH_SUFFIX =
        " " + AuthPageConstant.OFFSET_ROWS + " " + AuthPageConstant.FETCH_NEXT;
    
    private final JdbcTemplate jdbcTemplate;
    
    public OracleAuthPaginationHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows,
        final Object[] args, final int pageNo, final int pageSize, final RowMapper<E> rowMapper) {
        return fetchPage(sqlCountRows, sqlFetchRows, args, pageNo, pageSize, null, rowMapper);
    }
    
    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows,
        final Object[] args, final int pageNo, final int pageSize, final Long lastMaxId,
        final RowMapper<E> rowMapper) {
        if (lastMaxId != null) {
            throw new UnsupportedOperationException(
                "Oracle auth pagination does not support lastMaxId");
        }
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        
        // Query the total number of current records.
        Integer rowCountInt = jdbcTemplate.queryForObject(sqlCountRows, args, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPage error");
        }
        
        // Compute pages count
        int pageCount = rowCountInt / pageSize;
        if (rowCountInt > pageSize * pageCount) {
            pageCount++;
        }
        
        // Create Page object
        final Page<E> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(rowCountInt);
        
        if (pageNo > pageCount) {
            return page;
        }
        
        // Fill the sql page args for Oracle OFFSET ? ROWS FETCH NEXT ? ROWS ONLY.
        List<Object> newArgsList = new ArrayList<>(Arrays.asList(args));
        newArgsList.add((pageNo - 1) * pageSize);
        newArgsList.add(pageSize);
        
        List<E> result = jdbcTemplate.query(sqlFetchRows + ORACLE_FETCH_SUFFIX,
            newArgsList.toArray(), rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final String sqlFetchRows,
        final Object[] args, final int pageNo, final int pageSize, final RowMapper<E> rowMapper) {
        throw unsupportedFetchPageLimit();
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final Object[] args1,
        final String sqlFetchRows, final Object[] args2, final int pageNo, final int pageSize,
        final RowMapper<E> rowMapper) {
        throw unsupportedFetchPageLimit();
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlFetchRows, final Object[] args, final int pageNo,
        final int pageSize, final RowMapper<E> rowMapper) {
        throw unsupportedFetchPageLimit();
    }
    
    @Override
    public Page<E> fetchPageLimit(final MapperResult countMapperResult,
        final MapperResult mapperResult, final int pageNo, final int pageSize,
        final RowMapper<E> rowMapper) {
        throw unsupportedFetchPageLimit();
    }
    
    @Override
    public void updateLimit(final String sql, final Object[] args) {
        jdbcTemplate.update(sql, args);
    }
    
    private UnsupportedOperationException unsupportedFetchPageLimit() {
        return new UnsupportedOperationException(
            "fetchPageLimit is not used by the oracle auth SQL; use fetchPage instead");
    }
}
