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
package com.alibaba.nacos.config.server.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.StringUtils;

/**
 * local data source
 * 
 * @author Nacos
 *
 */
@Service("localDataSourceService")
public class LocalDataSourceServiceImpl implements DataSourceService {
	private static final String JDBC_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
	private static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";
	private static String appHome = System.getProperty("user.home") + File.separator + "nacos";
	private static final String NACOS_HOME_KEY = "nacos.home";
	private static final String USER_NAME = "nacos";
	private static final String PASSWORD = "nacos";

    private JdbcTemplate jt;
    private TransactionTemplate tjt;

    @PostConstruct
    public void init() {
		String nacosBaseDir = System.getProperty(NACOS_HOME_KEY);
		if (!StringUtils.isBlank(nacosBaseDir)) {
			setAppHome(nacosBaseDir);
		}
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(JDBC_DRIVER_NAME);
        ds.setUrl("jdbc:derby:" + appHome + File.separator + DERBY_BASE_DIR + ";create=true");
        ds.setUsername(USER_NAME);
        ds.setPassword(PASSWORD);
        ds.setInitialSize(20);
        ds.setMaxActive(30);
        ds.setMaxIdle(50);
        ds.setMaxWait(10000L);
        ds.setPoolPreparedStatements(true);
        ds.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES
                .toMillis(10L));
        ds.setTestWhileIdle(true);

        jt = new JdbcTemplate();
        jt.setMaxRows(50000);
        jt.setQueryTimeout(5000);
        jt.setDataSource(ds);
        DataSourceTransactionManager tm = new DataSourceTransactionManager();
        tjt = new TransactionTemplate(tm);
        tm.setDataSource(ds);
        tjt.setTimeout(5000);

        if (PropertyUtil.isStandaloneMode()) {
            reload();
        }
    }

    @Override
	public void reload() {
		DataSource ds = jt.getDataSource();
		if (ds == null) {
			throw new RuntimeException("datasource is null");
		}
		try {
			execute(ds.getConnection(), "schema.sql");
		} catch (Exception e) {
			throw new RuntimeException("load schema.sql error." + e);
		}
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
    public String getCurrentDBUrl() {
        return "jdbc:derby:" + appHome + File.separator + DERBY_BASE_DIR + ";create=true";
    }

    @Override
    public String getHealth() {
        return "UP";
    }

    /**
     * 读取SQL文件
     * @param sqlFile sql
     * @return sqls
     * @throws Exception Exception
     */
    private List<String> loadSql(String sqlFile) throws Exception {
        List<String> sqlList = new ArrayList<String>();
        InputStream sqlFileIn = null;
        try {
			if (StringUtils.isBlank(System.getProperty(NACOS_HOME_KEY))) {
                ClassLoader classLoader = getClass().getClassLoader();
                URL url = classLoader.getResource(sqlFile);
                sqlFileIn = url.openStream();
            } else {
                File file = new File(System.getProperty(NACOS_HOME_KEY) + File.separator + "conf" + File.separator + sqlFile);
                sqlFileIn = new FileInputStream(file);
            }

            StringBuffer sqlSb = new StringBuffer();
            byte[] buff = new byte[1024];
            int byteRead = 0;
			while ((byteRead = sqlFileIn.read(buff)) != -1) {
				sqlSb.append(new String(buff, 0, byteRead, Constants.ENCODE));
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
			if (sqlFileIn != null) {
				sqlFileIn.close();
			}
		}
    }

    /**
     * 执行SQL语句
     * @param conn connect
     * @param sqlFile sql
     * @throws Exception Exception
     */
    private void execute(Connection conn, String sqlFile) throws Exception {
        Statement stmt = null;
        try {
            List<String> sqlList = loadSql(sqlFile);
            stmt = conn.createStatement();
            for (String sql : sqlList) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    LogUtil.defaultLog.info(e.getMessage());
                }
            }
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

	public static String getAppHome() {
		return appHome;
	}

	public static void setAppHome(String appHome) {
		LocalDataSourceServiceImpl.appHome = appHome;
	}

	
}
