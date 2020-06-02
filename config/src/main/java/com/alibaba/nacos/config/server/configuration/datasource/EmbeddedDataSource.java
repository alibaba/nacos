package com.alibaba.nacos.config.server.configuration.datasource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Slf4j
public class EmbeddedDataSource implements DataSource, InitializingBean {

    private static final String SQL_SCHEMA = "META-INF/schema.sql";
    public static final String SCHEMA_PATTERN = "NACOS";
    private final DataSource dataSource;
    @Value("#{systemProperties['nacos.home']}")
    private String home;

    public EmbeddedDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @Override
    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return this.dataSource.getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return this.dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return this.dataSource.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return this.dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.dataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return this.dataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.dataSource.getParentLogger();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        executeScript();
    }

    private void executeScript() throws SQLException {
        try (final Connection connection = dataSource.getConnection();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = connection.getMetaData().getSchemas(null, SCHEMA_PATTERN)) {
            if (!resultSet.next()) {
                log.info("Initialize Nacos sql data in derby");
                final List<String> sqlSchemas = readInitSqlSchema();
                for (String sqlSchema : sqlSchemas) {
                    statement.execute(sqlSchema);
                }
            }
        }
    }

    private List<String> readInitSqlSchema() {
        try {
            File file = loadSqlFile();
            String sqlScript = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            return Arrays.stream(sqlScript.split(";")).map(sql -> sql.replaceAll("--.*", "").trim())
                    .filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Load schema.sql error." + e);
        }

    }

    private File loadSqlFile() throws FileNotFoundException {
        if (StringUtils.isBlank(home)) {
            ClassLoader classLoader = getClass().getClassLoader();
            URL resource = classLoader.getResource(SQL_SCHEMA);
            return ResourceUtils.getFile(Objects.requireNonNull(resource));
        } else {
            return new File(
                    home + File.separator + "conf" + File.separator + "schema.sql");
        }
    }
}
