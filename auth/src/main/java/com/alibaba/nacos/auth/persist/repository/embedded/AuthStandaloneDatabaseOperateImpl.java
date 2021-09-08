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

package com.alibaba.nacos.auth.persist.repository.embedded;

import com.alibaba.nacos.auth.configuration.ConditionStandaloneEmbedStorage;
import com.alibaba.nacos.auth.persist.datasource.DataSourceService;
import com.alibaba.nacos.auth.persist.datasource.AuthDynamicDataSource;
import com.alibaba.nacos.auth.util.LogUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Derby operation in stand-alone mode.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionStandaloneEmbedStorage.class)
@Component
public class AuthStandaloneDatabaseOperateImpl implements BaseDatabaseOperate {
    
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate;
    
    @PostConstruct
    protected void init() {
        DataSourceService dataSourceService = AuthDynamicDataSource.getInstance().getDataSource();
        jdbcTemplate = dataSourceService.getJdbcTemplate();
        transactionTemplate = dataSourceService.getTransactionTemplate();
        LogUtil.DEFAULT_LOG.info("use StandaloneDatabaseOperateImpl");
    }
    
    @Override
    public <R> R queryOne(String sql, Class<R> cls) {
        return queryOne(jdbcTemplate, sql, cls);
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, Class<R> cls) {
        return queryOne(jdbcTemplate, sql, args, cls);
    }
    
    @Override
    public <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper) {
        return queryOne(jdbcTemplate, sql, args, mapper);
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper) {
        return queryMany(jdbcTemplate, sql, args, mapper);
    }
    
    @Override
    public <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass) {
        return queryMany(jdbcTemplate, sql, args, rClass);
    }
    
    @Override
    public List<Map<String, Object>> queryMany(String sql, Object[] args) {
        return queryMany(jdbcTemplate, sql, args);
    }
}
