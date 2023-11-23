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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.springframework.jdbc.core.RowMapper;


/**
 * Auth plugin Pagination Helper.
 *
 * @param <E> Generic class
 * @author huangKeMing
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface AuthPaginationHelper<E> {
    
    Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, final Object[] args, final int pageNo,
            final int pageSize, final RowMapper<E> rowMapper);
    
    Page<E> fetchPage(final String sqlCountRows, final String sqlFetchRows, final Object[] args, final int pageNo,
            final int pageSize, final Long lastMaxId, final RowMapper<E> rowMapper);
    
    Page<E> fetchPageLimit(final String sqlCountRows, final String sqlFetchRows, final Object[] args, final int pageNo,
            final int pageSize, final RowMapper<E> rowMapper);
    
    Page<E> fetchPageLimit(final String sqlCountRows, final Object[] args1, final String sqlFetchRows,
            final Object[] args2, final int pageNo, final int pageSize, final RowMapper<E> rowMapper);
    
    Page<E> fetchPageLimit(final String sqlFetchRows, final Object[] args, final int pageNo, final int pageSize,
            final RowMapper<E> rowMapper);
    
    Page<E> fetchPageLimit(final MapperResult countMapperResult, final MapperResult mapperResult, final int pageNo,
            final int pageSize, final RowMapper<E> rowMapper);
    
    void updateLimit(final String sql, final Object[] args);
    
}
