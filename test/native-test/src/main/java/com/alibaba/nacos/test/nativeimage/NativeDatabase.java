/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.nativeimage;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The database of a Server: a Testcontainers container with the schema of the datasource plugin
 * applied. The datasource is a structural choice of the Native build, so the platform of a
 * database has to be the one the executable was built for.
 *
 * @author Nacos
 */
public abstract class NativeDatabase implements AutoCloseable {

    private static final String SCHEMA_IN_CONTAINER = "/tmp/nacos-schema.sql";

    private static final String NAME = "nacos";

    private JdbcDatabaseContainer<?> container;

    /**
     * Creates the database of a platform.
     *
     * @param platform {@code mysql} or {@code postgresql}
     * @return a database that is not started yet
     */
    public static NativeDatabase of(String platform) {
        switch (platform.toLowerCase(Locale.ROOT)) {
            case "mysql":
                return new Mysql();
            case "postgresql":
                return new Postgresql();
            default:
                throw new IllegalArgumentException("Unknown database: " + platform);
        }
    }

    /**
     * The value of {@code spring.sql.init.platform}.
     *
     * @return the platform
     */
    public abstract String platform();

    /**
     * Starts the container and applies the schema.
     */
    public void start() {
        Path schema = NativeTestSettings.repositoryRoot().resolve(schemaFile());
        if (!Files.isRegularFile(schema)) {
            throw new IllegalStateException("Missing " + schema);
        }
        container = createContainer();
        container.withCopyFileToContainer(MountableFile.forHostPath(schema), SCHEMA_IN_CONTAINER);
        container.start();
        try {
            ExecResult result = applySchema(container);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("The schema failed with exit code "
                        + result.getExitCode() + ": " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("The schema could not be applied", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * The properties of {@code conf/application.properties} that connect a Server to this database.
     *
     * @return properties, in the order they are written
     */
    public Map<String, String> serverProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.sql.init.platform", platform());
        properties.put("nacos.plugin.datasource.db.num", "1");
        properties.put("nacos.plugin.datasource.db.url.0",
                jdbcUrl(container.getHost(), container.getMappedPort(port())));
        properties.put("nacos.plugin.datasource.db.user", NAME);
        properties.put("nacos.plugin.datasource.db.password", NAME);
        return properties;
    }

    /**
     * The log of the container.
     *
     * @return the log, empty before the start
     */
    public String logs() {
        return container == null ? "" : container.getLogs();
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }

    /**
     * Creates the container, with the name, the user and the password {@code nacos}.
     *
     * @return a container that is not started
     */
    abstract JdbcDatabaseContainer<?> createContainer();

    /**
     * The schema file of the datasource plugin, relative to the root of the repository.
     *
     * @return a path
     */
    abstract String schemaFile();

    /**
     * Applies the schema that was copied to {@link #SCHEMA_IN_CONTAINER}.
     *
     * @param started the started container
     * @return the result of the command
     * @throws IOException          if the command cannot be run
     * @throws InterruptedException if the thread is interrupted
     */
    abstract ExecResult applySchema(JdbcDatabaseContainer<?> started)
            throws IOException, InterruptedException;

    abstract int port();

    abstract String jdbcUrl(String host, int port);

    private static final class Mysql extends NativeDatabase {

        @Override
        public String platform() {
            return "mysql";
        }

        @Override
        JdbcDatabaseContainer<?> createContainer() {
            // The Server keeps little memory, because the Native compiler needs most of the host.
            return new MySQLContainer("mysql:8.4").withDatabaseName(NAME).withUsername(NAME)
                    .withPassword(NAME)
                    .withCommand("--performance-schema=OFF", "--innodb-buffer-pool-size=64M");
        }

        @Override
        String schemaFile() {
            return "plugin-default-impl/nacos-default-datasource-plugin/nacos-datasource-plugin-mysql"
                    + "/src/main/resources/META-INF/mysql-schema.sql";
        }

        @Override
        ExecResult applySchema(JdbcDatabaseContainer<?> started)
                throws IOException, InterruptedException {
            return started.execInContainer("sh", "-c", "mysql --protocol=TCP -h 127.0.0.1 -u" + NAME
                    + " -p" + NAME + ' ' + NAME + " < " + SCHEMA_IN_CONTAINER);
        }

        @Override
        int port() {
            return MySQLContainer.MYSQL_PORT;
        }

        @Override
        String jdbcUrl(String host, int port) {
            return "jdbc:mysql://" + host + ':' + port + '/' + NAME
                    + "?characterEncoding=utf8&connectTimeout=3000&socketTimeout=6000"
                    + "&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"
                    + "&allowPublicKeyRetrieval=true";
        }
    }

    private static final class Postgresql extends NativeDatabase {

        @Override
        public String platform() {
            return "postgresql";
        }

        @Override
        JdbcDatabaseContainer<?> createContainer() {
            return new PostgreSQLContainer("postgres:17-alpine").withDatabaseName(NAME)
                    .withUsername(NAME).withPassword(NAME);
        }

        @Override
        String schemaFile() {
            return "plugin-default-impl/nacos-default-datasource-plugin"
                    + "/nacos-datasource-plugin-postgresql/src/main/resources/META-INF/pg-schema.sql";
        }

        @Override
        ExecResult applySchema(JdbcDatabaseContainer<?> started)
                throws IOException, InterruptedException {
            return started.execInContainer("psql", "-U", NAME, "-d", NAME, "-v", "ON_ERROR_STOP=1",
                    "-q", "-f", SCHEMA_IN_CONTAINER);
        }

        @Override
        int port() {
            return PostgreSQLContainer.POSTGRESQL_PORT;
        }

        @Override
        String jdbcUrl(String host, int port) {
            return "jdbc:postgresql://" + host + ':' + port + '/' + NAME + "?currentSchema=public";
        }
    }
}
