/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;

/**
 * Pagination Utils For Apache Derby.
 *
 * @param <E> Generic class
 * @author boyan
 * @date 2010-5-6
 */
class EmbeddedPaginationHelperImpl<E> implements PaginationHelper {
    
    private final DatabaseOperate databaseOperate;
    
    public EmbeddedPaginationHelperImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    /**
     * Take paging.
     *
     * @param sqlCountRows Query total SQL
     * @param sqlFetchRows Query data sql
     * @param args         query args
     * @param pageNo       page number
     * @param pageSize     page size
     * @param rowMapper    Entity mapping
     * @return Paging data
     */
    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, final Object[] args,
            final int pageNo, final int pageSize, final RowMapper rowMapper) {
        return fetchPage(sqlCountRows, sqlFetchRows, args, pageNo, pageSize, null, rowMapper);
    }

    @Override
    public Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, final Object[] args,
            final int pageNo, final int pageSize, final Long lastMaxId, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        
        // Query the total number of current records
        Integer rowCountInt = databaseOperate.queryOne(sqlCountRows, args, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
        }
        
        // Count pages
        int pageCount = rowCountInt / pageSize;
        if (rowCountInt > pageSize * pageCount) {
            pageCount++;
        }
        
        // Create Page object
        final Page<E> page = new Page<E>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(rowCountInt);
        
        if (pageNo > pageCount) {
            return page;
        }
        
        final int startRow = (pageNo - 1) * pageSize;
        String selectSql = sqlFetchRows + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        
        List<E> result = databaseOperate.queryMany(selectSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }

    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final String sqlFetchRows, final Object[] args,
            final int pageNo, final int pageSize, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Query the total number of current records
        Integer rowCountInt = databaseOperate.queryOne(sqlCountRows, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
        }
        
        // Count pages
        int pageCount = rowCountInt / pageSize;
        if (rowCountInt > pageSize * pageCount) {
            pageCount++;
        }
        
        // Create Page object
        final Page<E> page = new Page<E>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(rowCountInt);
        
        if (pageNo > pageCount) {
            return page;
        }
        
        String selectSql = sqlFetchRows.replaceAll("(?i)LIMIT \\?,\\?", "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        List<E> result = databaseOperate.queryMany(selectSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }

    @Override
    public Page<E> fetchPageLimit(final String sqlCountRows, final Object[] args1, final String sqlFetchRows,
            final Object[] args2, final int pageNo, final int pageSize, final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Query the total number of current records
        Integer rowCountInt = databaseOperate.queryOne(sqlCountRows, args1, Integer.class);
        if (rowCountInt == null) {
            throw new IllegalArgumentException("fetchPageLimit error");
        }
        
        // Count pages
        int pageCount = rowCountInt / pageSize;
        if (rowCountInt > pageSize * pageCount) {
            pageCount++;
        }
        
        // Create Page object
        final Page<E> page = new Page<E>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(rowCountInt);
        
        if (pageNo > pageCount) {
            return page;
        }
        
        String selectSql = sqlFetchRows.replaceAll("(?i)LIMIT \\?,\\?", "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        
        List<E> result = databaseOperate.queryMany(selectSql, args2, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }

    @Override
    public Page<E> fetchPageLimit(final String sqlFetchRows, final Object[] args, final int pageNo, final int pageSize,
            final RowMapper rowMapper) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than zero");
        }
        // Create Page object
        final Page<E> page = new Page<E>();
        
        String selectSql = sqlFetchRows.replaceAll("(?i)LIMIT \\?,\\?", "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        
        List<E> result = databaseOperate.queryMany(selectSql, args, rowMapper);
        for (E item : result) {
            page.getPageItems().add(item);
        }
        return page;
    }

    @Override
    public void updateLimit(final String sql, final Object[] args) {
        String sqlUpdate = sql.replaceAll("limit \\?", "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
        
        EmbeddedStorageContextUtils.addSqlContext(sqlUpdate, args);
        try {
            databaseOperate.update(EmbeddedStorageContextUtils.getCurrentSqlContext());
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }
    
}
