/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.persistence.extrnal;

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.plugin.auth.impl.model.OffsetFetchResult;
import com.alibaba.nacos.plugin.auth.impl.persistence.AuthPaginationHelper;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.PageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.PageHandlerAdapterFactory;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DefaultPageHandlerAdapter;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

/**
 * Auth plugin Pagination Utils For Apache External.
 *
 * @param <E> Generic class
 * @author huangKeMing
 */
public class AuthExternalPaginationHelperImpl<E> implements AuthPaginationHelper<E> {
    
    private final JdbcTemplate jdbcTemplate;
    
    private volatile String dataSourceType;
    
    public AuthExternalPaginationHelperImpl(JdbcTemplate jdbcTemplate, String dataSourceType) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSourceType = dataSourceType;
    }
    
    /**
     * Take paging.
     *
     * @param sqlCountRows query total SQL
     * @param sqlFetchRows query data sql
     * @param args         query parameters
     * @param pageNo       page number
     * @param pageSize     page size
     * @param rowMapper    {@link RowMapper}
     * @return Paginated data {@code <E>}
     */
    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, final Object[] args,
            final int pageNo, final int pageSize, final RowMapper rowMapper) {
        return fetchPage(sqlCountRows, sqlFetchRows, args, pageNo, pageSize, null, rowMapper);
    }
    
    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, Object[] args, final int pageNo,
            final int pageSize, final Long lastMaxId, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        
        // Query the total number of current records.
        Integer rowCountInt = jdbcTemplate.queryForObject(sqlCountRows, args, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
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
        
        // fill the sql Page args
        String fetchSql = sqlFetchRows;
        OffsetFetchResult offsetFetchResult = addOffsetAndFetchNext(fetchSql, args, pageNo, pageSize);
        fetchSql = offsetFetchResult.getFetchSql();
        args = offsetFetchResult.getNewArgs();
        
        List<E> result = jdbcTemplate.query(fetchSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final String sqlFetchRows, Object[] args, final int pageNo,
            final int pageSize, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Query the total number of current records
        Integer rowCountInt = jdbcTemplate.queryForObject(sqlCountRows, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
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
        
        // fill the sql Page args
        String fetchSql = sqlFetchRows;
        OffsetFetchResult offsetFetchResult = addOffsetAndFetchNext(fetchSql, args, pageNo, pageSize);
        fetchSql = offsetFetchResult.getFetchSql();
        args = offsetFetchResult.getNewArgs();
        
        List<E> result = jdbcTemplate.query(fetchSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public Page fetchPageLimit(MapperResult countMapperResult, MapperResult mapperResult, int pageNo, int pageSize,
            RowMapper rowMapper) {
        return fetchPageLimit(countMapperResult.getSql(), countMapperResult.getParamList().toArray(),
                mapperResult.getSql(), mapperResult.getParamList().toArray(), pageNo, pageSize, rowMapper);
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final Object[] args1, final String sqlFetchRows,
            Object[] args2, final int pageNo, final int pageSize, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Query the total number of current records
        Integer rowCountInt = jdbcTemplate.queryForObject(sqlCountRows, args1, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
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
        // fill the sql Page args
        String fetchSql = sqlFetchRows;
        OffsetFetchResult offsetFetchResult = addOffsetAndFetchNext(fetchSql, args2, pageNo, pageSize);
        fetchSql = offsetFetchResult.getFetchSql();
        args2 = offsetFetchResult.getNewArgs();
        
        List<E> result = jdbcTemplate.query(fetchSql, args2, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public Page<E> fetchPageLimit(final String sqlFetchRows, Object[] args, final int pageNo, final int pageSize,
            final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Create Page object
        final Page<E> page = new Page<>();
        
        // fill the sql Page args
        String fetchSql = sqlFetchRows;
        OffsetFetchResult offsetFetchResult = addOffsetAndFetchNext(fetchSql, args, pageNo, pageSize);
        fetchSql = offsetFetchResult.getFetchSql();
        args = offsetFetchResult.getNewArgs();
        
        List<E> result = jdbcTemplate.query(fetchSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }
    
    @Override
    public void updateLimit(final String sql, final Object[] args) {
        try {
            jdbcTemplate.update(sql, args);
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    /**
     * Update limit with response.
     *
     * @param sql  sql
     * @param args args
     * @return update row count
     */
    public int updateLimitWithResponse(final String sql, final Object[] args) {
        String sqlUpdate = sql;
        
        try {
            return jdbcTemplate.update(sqlUpdate, args);
        } finally {
            EmbeddedStorageContextHolder.cleanAllContext();
        }
    }
    
    private OffsetFetchResult addOffsetAndFetchNext(String fetchSql, Object[] arg, int pageNo, int pageSize) {
        return getHandlerAdapter(dataSourceType).addOffsetAndFetchNext(fetchSql, arg, pageNo, pageSize);
    }
    
    /**
     * Get handler adapter.
     *
     * @param dataSourceType data source type.
     * @return
     */
    protected PageHandlerAdapter getHandlerAdapter(String dataSourceType) {
        List<PageHandlerAdapter> handlerAdapters = PageHandlerAdapterFactory.getInstance().getHandlerAdapters();
        for (PageHandlerAdapter adapter : handlerAdapters) {
            if (adapter.supports(dataSourceType)) {
                return adapter;
            }
            
        }
        
        return PageHandlerAdapterFactory.getInstance().getHandlerAdapterMap()
                .get(DefaultPageHandlerAdapter.class.getName());
    }
    
}
