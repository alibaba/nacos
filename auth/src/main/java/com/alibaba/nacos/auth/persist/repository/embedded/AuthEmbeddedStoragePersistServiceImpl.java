/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.auth.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.auth.persist.datasource.DataSourceService;
import com.alibaba.nacos.auth.persist.datasource.AuthDynamicDataSource;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Auth persist service for embedded storage.
 *
 * @author Nacos
 */
@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class AuthEmbeddedStoragePersistServiceImpl {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    public AuthEmbeddedStoragePersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    /**
     * init datasource.
     */
    @PostConstruct
    public void init() {
        dataSourceService = AuthDynamicDataSource.getInstance().getDataSource();
    }
    
    public <E> PaginationHelper<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<E>(databaseOperate);
    }
    
    /**
     * For unit testing.
     */
    public JdbcTemplate getJdbcTemplate() {
        return this.dataSourceService.getJdbcTemplate();
    }
    
    public DatabaseOperate getDatabaseOperate() {
        return databaseOperate;
    }
    
}
