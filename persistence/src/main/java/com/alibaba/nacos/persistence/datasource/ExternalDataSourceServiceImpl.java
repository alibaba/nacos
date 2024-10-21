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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.monitor.DatasourceMetrics;
import com.alibaba.nacos.persistence.utils.ConnectionCheckUtil;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.persistence.utils.PersistenceExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base data source.
 *
 * @author Nacos
 */
public class ExternalDataSourceServiceImpl implements DataSourceService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalDataSourceServiceImpl.class);
    
    /**
     * JDBC execute timeout value, unit:second.
     */
    private int queryTimeout = 3;
    
    private static final int TRANSACTION_QUERY_TIMEOUT = 5;
    
    private static final int DB_MASTER_SELECT_THRESHOLD = 1;
    
    private static final String DB_LOAD_ERROR_MSG = "[db-load-error]load jdbc.properties error";
    
    private List<HikariDataSource> dataSourceList = new ArrayList<>();
    
    private JdbcTemplate jt;
    
    private DataSourceTransactionManager tm;
    
    private TransactionTemplate tjt;
    
    private JdbcTemplate testMasterJT;
    
    private JdbcTemplate testMasterWritableJT;
    
    private volatile List<JdbcTemplate> testJtList;
    
    private volatile List<Boolean> isHealthList;
    
    private volatile int masterIndex;
    
    private String dataSourceType = "";
    
    private final String defaultDataSourceType = "";
    
    @Override
    public void init() {
        queryTimeout = ConvertUtils.toInt(System.getProperty("QUERYTIMEOUT"), 3);
        jt = new JdbcTemplate();
        // Set the maximum number of records to prevent memory expansion
        jt.setMaxRows(50000);
        jt.setQueryTimeout(queryTimeout);
        
        testMasterJT = new JdbcTemplate();
        testMasterJT.setQueryTimeout(queryTimeout);
        
        testMasterWritableJT = new JdbcTemplate();
        // Prevent the login interface from being too long because the main library is not available
        testMasterWritableJT.setQueryTimeout(1);
        
        //  Database health check
        
        testJtList = new ArrayList<>();
        isHealthList = new ArrayList<>();
        
        tm = new DataSourceTransactionManager();
        tjt = new TransactionTemplate(tm);
        
        // Transaction timeout needs to be distinguished from ordinary operations.
        tjt.setTimeout(TRANSACTION_QUERY_TIMEOUT);
        
        dataSourceType = DatasourcePlatformUtil.getDatasourcePlatform(defaultDataSourceType);
        
        if (DatasourceConfiguration.isUseExternalDB()) {
            try {
                reload();
            } catch (IOException e) {
                LOGGER.error("[ExternalDataSourceService] datasource reload error", e);
                throw new RuntimeException(DB_LOAD_ERROR_MSG, e);
            }
            
            if (this.dataSourceList.size() > DB_MASTER_SELECT_THRESHOLD) {
                PersistenceExecutor.scheduleTask(new SelectMasterTask(), 10, 10, TimeUnit.SECONDS);
            }
            PersistenceExecutor.scheduleTask(new CheckDbHealthTask(), 10, 10, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public synchronized void reload() throws IOException {
        try {
            final List<JdbcTemplate> testJtListNew = new ArrayList<JdbcTemplate>();
            final List<Boolean> isHealthListNew = new ArrayList<Boolean>();
            
            List<HikariDataSource> dataSourceListNew = new ExternalDataSourceProperties()
                    .build(EnvUtil.getEnvironment(), (dataSource) -> {
                        //check datasource connection
                        ConnectionCheckUtil.checkDataSourceConnection(dataSource);
                        
                        JdbcTemplate jdbcTemplate = new JdbcTemplate();
                        jdbcTemplate.setQueryTimeout(queryTimeout);
                        jdbcTemplate.setDataSource(dataSource);
                        testJtListNew.add(jdbcTemplate);
                        isHealthListNew.add(Boolean.TRUE);
                    });
            
            final List<HikariDataSource> dataSourceListOld = dataSourceList;
            final List<JdbcTemplate> testJtListOld = testJtList;
            dataSourceList = dataSourceListNew;
            testJtList = testJtListNew;
            isHealthList = isHealthListNew;
            new SelectMasterTask().run();
            new CheckDbHealthTask().run();
            
            //close old datasource.
            if (dataSourceListOld != null && !dataSourceListOld.isEmpty()) {
                for (HikariDataSource dataSource : dataSourceListOld) {
                    dataSource.close();
                }
            }
            if (testJtListOld != null && !testJtListOld.isEmpty()) {
                for (JdbcTemplate oldJdbc : testJtListOld) {
                    oldJdbc.setDataSource(null);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error(DB_LOAD_ERROR_MSG, e);
            throw new IOException(e);
        }
    }
    
    @Override
    public boolean checkMasterWritable() {
        
        testMasterWritableJT.setDataSource(jt.getDataSource());
        // Prevent the login interface from being too long because the main library is not available
        testMasterWritableJT.setQueryTimeout(1);
        String sql = " SELECT @@read_only ";
        
        try {
            Integer result = testMasterWritableJT.queryForObject(sql, Integer.class);
            if (result == null) {
                return false;
            } else {
                return result == 0;
            }
        } catch (CannotGetJdbcConnectionException e) {
            LOGGER.error("[db-error] " + e, e);
            return false;
        }
        
    }
    
    @Override
    public JdbcTemplate getJdbcTemplate() {
        return this.jt;
    }
    
    @Override
    public TransactionTemplate getTransactionTemplate() {
        return this.tjt;
    }
    
    @Override
    public String getCurrentDbUrl() {
        DataSource ds = this.jt.getDataSource();
        if (ds == null) {
            return StringUtils.EMPTY;
        }
        HikariDataSource bds = (HikariDataSource) ds;
        return bds.getJdbcUrl();
    }
    
    @Override
    public String getHealth() {
        for (int i = 0; i < isHealthList.size(); i++) {
            if (!isHealthList.get(i)) {
                if (i == masterIndex) {
                    // The master is unhealthy.
                    return "DOWN:" + InternetAddressUtil.getIPFromString(dataSourceList.get(i).getJdbcUrl());
                } else {
                    // The slave  is unhealthy.
                    return "WARN:" + InternetAddressUtil.getIPFromString(dataSourceList.get(i).getJdbcUrl());
                }
            }
        }
        
        return "UP";
    }
    
    @Override
    public String getDataSourceType() {
        return dataSourceType;
    }
    
    class SelectMasterTask implements Runnable {
        
        @Override
        public void run() {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("check master db.");
            }
            boolean isFound = false;
            
            int index = -1;
            for (HikariDataSource ds : dataSourceList) {
                index++;
                testMasterJT.setDataSource(ds);
                testMasterJT.setQueryTimeout(queryTimeout);
                try {
                    testMasterJT.update("DELETE FROM config_info WHERE data_id='com.alibaba.nacos.testMasterDB'");
                    if (jt.getDataSource() != ds) {
                        LOGGER.warn("[master-db] {}", ds.getJdbcUrl());
                    }
                    jt.setDataSource(ds);
                    tm.setDataSource(ds);
                    isFound = true;
                    masterIndex = index;
                    break;
                } catch (DataAccessException e) { // read only
                    LOGGER.warn("[master-db] master db access error", e);
                }
            }
            
            if (!isFound) {
                LOGGER.error("[master-db] master db not found.");
                DatasourceMetrics.getDbException().increment();
            }
        }
    }
    
    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    class CheckDbHealthTask implements Runnable {
        
        @Override
        public void run() {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("check db health.");
            }
            String sql = "SELECT * FROM config_info_beta WHERE id = 1";
            
            for (int i = 0; i < testJtList.size(); i++) {
                JdbcTemplate jdbcTemplate = testJtList.get(i);
                try {
                    try {
                        jdbcTemplate.queryForMap(sql);
                    } catch (EmptyResultDataAccessException e) {
                        // do nothing.
                    }
                    isHealthList.set(i, Boolean.TRUE);
                } catch (DataAccessException e) {
                    if (i == masterIndex) {
                        LOGGER.error("[db-error] master db {} down.",
                                InternetAddressUtil.getIPFromString(dataSourceList.get(i).getJdbcUrl()));
                    } else {
                        LOGGER.error("[db-error] slave db {} down.",
                                InternetAddressUtil.getIPFromString(dataSourceList.get(i).getJdbcUrl()));
                    }
                    isHealthList.set(i, Boolean.FALSE);
                    
                    DatasourceMetrics.getDbException().increment();
                }
            }
        }
    }
}
