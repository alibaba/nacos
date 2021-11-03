/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.persist.datasource;

import com.alibaba.nacos.auth.constant.Constants;
import com.alibaba.nacos.auth.util.AuthPropertyUtil;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.alibaba.nacos.auth.util.LogUtil.DEFAULT_LOG;
import static com.alibaba.nacos.auth.util.LogUtil.FATAL_LOG;

/**
 * Base data source.
 *
 * @author Nacos
 */
public class ExternalDataSourceServiceImpl implements DataSourceService {
    
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
    
    @Override
    public void init() {
        queryTimeout = ConvertUtils.toInt(System.getProperty(Constants.ExternalDataSource.QUERYTIMEOUT), 3);
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
        
        testJtList = new ArrayList<JdbcTemplate>();
        isHealthList = new ArrayList<Boolean>();
        
        tm = new DataSourceTransactionManager();
        tjt = new TransactionTemplate(tm);
        
        // Transaction timeout needs to be distinguished from ordinary operations.
        tjt.setTimeout(TRANSACTION_QUERY_TIMEOUT);
        if (AuthPropertyUtil.isUseExternalDB()) {
            try {
                reload();
            } catch (IOException e) {
                FATAL_LOG.error("[ExternalDataSourceService] dats source reload error", e);
                throw new RuntimeException(DB_LOAD_ERROR_MSG);
            }
        }
    }
    
    @Override
    public synchronized void reload() throws IOException {
        try {
            dataSourceList = new ExternalDataSourceProperties()
                    .build(EnvUtil.getEnvironment(), (dataSource) -> {
                        JdbcTemplate jdbcTemplate = new JdbcTemplate();
                        jdbcTemplate.setQueryTimeout(queryTimeout);
                        jdbcTemplate.setDataSource(dataSource);
                        testJtList.add(jdbcTemplate);
                        isHealthList.add(Boolean.TRUE);
                    });
            new SelectMasterTask().run();
        } catch (RuntimeException e) {
            FATAL_LOG.error(DB_LOAD_ERROR_MSG, e);
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
            FATAL_LOG.error("[db-error] " + e.toString(), e);
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
    
    class SelectMasterTask implements Runnable {
        
        @Override
        public void run() {
            if (DEFAULT_LOG.isDebugEnabled()) {
                DEFAULT_LOG.debug("check master db.");
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
                        FATAL_LOG.warn("[master-db] {}", ds.getJdbcUrl());
                    }
                    jt.setDataSource(ds);
                    tm.setDataSource(ds);
                    isFound = true;
                    masterIndex = index;
                    break;
                } catch (DataAccessException e) { // read only
                    FATAL_LOG.warn("[master-db] master db access error", e);
                }
            }
            
            if (!isFound) {
                FATAL_LOG.error("[master-db] master db not found.");
            }
        }
    }
}
