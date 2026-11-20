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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * local data source.
 *
 * <p>Hosts the embedded storage. Derby is the default engine. The H2 engine is selected by
 * {@code nacos.plugin.datasource-dialect.type=h2} and is available in standalone mode only.
 *
 * @author Nacos
 */
public class LocalDataSourceServiceImpl implements DataSourceService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDataSourceServiceImpl.class);
    
    private final String userName = "nacos";
    
    private final String password = "nacos";
    
    private final String derbyShutdownErrMsg = "Derby system shutdown.";
    
    private volatile JdbcTemplate jt;
    
    private volatile TransactionTemplate tjt;
    
    private boolean initialize = false;
    
    private boolean jdbcTemplateInit = false;
    
    private volatile EmbeddedEngine engine = EmbeddedEngine.DERBY;
    
    private String healthStatus = "UP";
    
    @Override
    public synchronized void init() throws Exception {
        if (DatasourceConfiguration.isUseExternalDb()) {
            return;
        }
        if (!initialize) {
            LOGGER.info("use local db service for init");
            engine = EmbeddedEngine.fromPlatform(
                DatasourcePlatformUtil.getDatasourcePlatform(PersistenceConstant.DERBY));
            final String jdbcUrl = engine.jdbcUrl(EnvUtil.getNacosHome());
            initialize(jdbcUrl);
            initialize = true;
        }
    }
    
    @Override
    public synchronized void reload() {
        DataSource ds = jt.getDataSource();
        if (ds == null) {
            throw new RuntimeException("datasource is null");
        }
        try (Connection connection = ds.getConnection()) {
            execute(connection, engine.schemaResource());
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(e.getMessage(), e);
            }
            throw new NacosRuntimeException(NacosException.SERVER_ERROR,
                "load " + engine.schemaFileName() + " error.", e);
        }
    }
    
    public DataSource getDatasource() {
        return jt.getDataSource();
    }
    
    /**
     * Clean and reopen Derby.
     *
     * @throws Exception exception.
     */
    public void cleanAndReopenDerby() throws Exception {
        ensureDerby("Reopening the local database");
        doDerbyClean();
        initialize(engine.jdbcUrl(EnvUtil.getNacosHome()));
    }
    
    /**
     * Restore derby.
     *
     * @param jdbcUrl  jdbcUrl string value.
     * @param callable callable.
     * @throws Exception exception.
     */
    public void restoreDerby(String jdbcUrl, Callable<Void> callable) throws Exception {
        ensureDerby("Restoring the local database");
        doDerbyClean();
        callable.call();
        initialize(jdbcUrl);
    }
    
    private void ensureDerby(String operation) {
        if (EmbeddedEngine.DERBY != engine) {
            throw new IllegalStateException(operation + " is only supported by the embedded "
                + EmbeddedEngine.DERBY.type + " engine, but the selected engine is " + engine.type
                + ".");
        }
    }
    
    private void doDerbyClean() throws Exception {
        LOGGER.warn("use local db service for reopenDerby");
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (Exception e) {
            // An error is thrown when the Derby shutdown is executed, which should be ignored
            if (!StringUtils.containsIgnoreCase(e.getMessage(), derbyShutdownErrMsg)) {
                throw e;
            }
        }
        DiskUtils.deleteDirectory(engine.dataDir(EnvUtil.getNacosHome()).toString());
    }
    
    private synchronized void initialize(String jdbcUrl) {
        DataSourcePoolProperties poolProperties =
            DataSourcePoolProperties.build(EnvUtil.getEnvironment(),
                DataSourcePoolProperties.DEFAULT_EMBEDDED_CONNECTION_TIMEOUT);
        poolProperties.setDriverClassName(engine.driverClassName);
        poolProperties.setJdbcUrl(jdbcUrl);
        poolProperties.setUsername(userName);
        poolProperties.setPassword(password);
        HikariDataSource ds = poolProperties.getDataSource();
        DataSourceTransactionManager tm = new DataSourceTransactionManager();
        tm.setDataSource(ds);
        if (jdbcTemplateInit) {
            jt.setDataSource(ds);
            tjt.setTransactionManager(tm);
        } else {
            jt = new JdbcTemplate();
            jt.setMaxRows(50000);
            jt.setQueryTimeout(5000);
            jt.setDataSource(ds);
            tjt = new TransactionTemplate(tm);
            tjt.setTimeout(5000);
            jdbcTemplateInit = true;
        }
        reload();
    }
    
    @Override
    public boolean checkMasterWritable() {
        return true;
    }
    
    @Override
    public JdbcTemplate getJdbcTemplate() {
        return jt;
    }
    
    @Override
    public TransactionTemplate getTransactionTemplate() {
        return tjt;
    }
    
    @Override
    public String getDataSourceType() {
        return engine.type;
    }
    
    /**
     * Set local data source health status.
     *
     * @param healthStatus health status.
     */
    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }
    
    /**
     * Load sql.
     *
     * @param sqlFile sql.
     * @return sqls.
     * @throws Exception Exception.
     */
    private List<String> loadSql(String sqlFile) throws Exception {
        List<String> sqlList = new ArrayList<>();
        InputStream sqlFileIn = null;
        try {
            File file = new File(
                EnvUtil.getNacosHome() + File.separator + "conf" + File.separator
                    + engine.schemaFileName());
            if (StringUtils.isBlank(EnvUtil.getNacosHome()) || !file.exists()) {
                ClassLoader classLoader = getClass().getClassLoader();
                URL url = classLoader.getResource(sqlFile);
                sqlFileIn = url.openStream();
            } else {
                sqlFileIn = new FileInputStream(file);
            }
            
            StringBuilder sqlSb = new StringBuilder();
            byte[] buff = new byte[1024];
            int byteRead = 0;
            while ((byteRead = sqlFileIn.read(buff)) != -1) {
                sqlSb.append(new String(buff, 0, byteRead, PersistenceConstant.DEFAULT_ENCODE));
            }
            
            String[] sqlArr = sqlSb.toString().split(";");
            for (int i = 0; i < sqlArr.length; i++) {
                String sql = sqlArr[i].replaceAll("--.*", "").trim();
                if (StringUtils.isNotEmpty(sql)) {
                    sqlList.add(sql);
                }
            }
            return sqlList;
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        } finally {
            IoUtils.closeQuietly(sqlFileIn);
        }
    }
    
    /**
     * Execute sql.
     *
     * @param conn    connect.
     * @param sqlFile sql.
     * @throws Exception Exception.
     */
    private void execute(Connection conn, String sqlFile) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            List<String> sqlList = loadSql(sqlFile);
            for (String sql : sqlList) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    LOGGER.warn(e.getMessage());
                }
            }
        }
    }
    
    /**
     * Local embedded database engines. Each engine owns its JDBC driver, its data directory under
     * {@code ${nacos.home}/data} and the schema script that initializes it.
     */
    private enum EmbeddedEngine {
        
        DERBY(PersistenceConstant.DERBY, "org.apache.derby.jdbc.EmbeddedDriver",
            PersistenceConstant.DERBY_BASE_DIR, "derby-schema.sql"),
        
        H2(PersistenceConstant.H2, "org.h2.Driver", PersistenceConstant.H2_BASE_DIR,
            "h2-schema.sql");
        
        /**
         * Name of the H2 database file, stored as {@code nacos.mv.db} in the H2 data directory.
         */
        private static final String H2_DATABASE_NAME = "nacos";
        
        private final String type;
        
        private final String driverClassName;
        
        private final String baseDir;
        
        private final String schemaFileName;
        
        EmbeddedEngine(String type, String driverClassName, String baseDir,
            String schemaFileName) {
            this.type = type;
            this.driverClassName = driverClassName;
            this.baseDir = baseDir;
            this.schemaFileName = schemaFileName;
        }
        
        static EmbeddedEngine fromPlatform(String platform) {
            return PersistenceConstant.H2.equalsIgnoreCase(platform) ? H2 : DERBY;
        }
        
        Path dataDir(String nacosHome) {
            return Paths.get(nacosHome, "data", baseDir);
        }
        
        /**
         * Build the JDBC URL of the local database.
         *
         * <p>A file-backed H2 database defaults to {@code WRITE_DELAY=500} (milliseconds), so committed changes may
         * wait in H2's write window before they are flushed. H2 is opened with {@code WRITE_DELAY=0} to flush
         * without that delay. H2 also writes every exception it throws into a {@code .trace.db} file by default.
         * Nacos repositories handle some of those exceptions themselves, for example an optional column read that
         * falls back, so {@code TRACE_LEVEL_FILE=0} keeps that noise out of the data directory. Derby keeps its own
         * defaults.
         *
         * @param nacosHome Nacos home directory
         * @return JDBC URL
         */
        String jdbcUrl(String nacosHome) {
            if (this == H2) {
                return "jdbc:h2:file:" + dataDir(nacosHome).resolve(H2_DATABASE_NAME)
                    + ";WRITE_DELAY=0;TRACE_LEVEL_FILE=0";
            }
            return "jdbc:derby:" + dataDir(nacosHome) + ";create=true";
        }
        
        /**
         * Classpath resource of the schema script bundled with the datasource dialect plugin.
         *
         * @return resource path
         */
        String schemaResource() {
            return "META-INF/" + schemaFileName;
        }
        
        String schemaFileName() {
            return schemaFileName;
        }
    }
    
}
