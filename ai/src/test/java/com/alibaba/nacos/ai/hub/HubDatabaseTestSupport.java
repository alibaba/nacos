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
 *
 */

package com.alibaba.nacos.ai.hub;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Isolated JDBC support for the S0 Hub database experiment.
 *
 * <p>Reads the checkout's DDL, not a hand-written approximation. Uses an isolated
 * Derby in-memory database with an empty schema. Never executes source DROP
 * statements. Cleanup only drops tables successfully created by this instance.</p>
 *
 */
final class HubDatabaseTestSupport implements AutoCloseable {
    
    private static final Set<String> TABLES = Set.of(
        "tenant_info", "ai_resource", "ai_resource_version");
    
    private static final String IDENTIFIER = "[\\\"`]?([a-zA-Z_][a-zA-Z0-9_]*)[\\\"`]?";
    
    private static final Pattern CREATE = Pattern.compile(
        "(?is)^CREATE\\s+TABLE\\s+" + IDENTIFIER + "\\s*\\(");
    
    private static final Pattern ALTER = Pattern.compile(
        "(?is)^ALTER\\s+TABLE\\s+" + IDENTIFIER + "\\s+ADD\\s+CONSTRAINT\\s+");
    
    private static final Pattern INDEX = Pattern.compile(
        "(?is)^CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+[^\\s]+\\s+ON\\s+"
            + IDENTIFIER + "(?:\\s|\\()");
    
    private final String database;
    
    private final String url;
    
    private final Properties credentials = new Properties();
    
    private final List<String> createdTables = new ArrayList<>();
    
    private Connection connection;
    
    private HubDatabaseTestSupport() throws Exception {
        database = "derby";
        url = "jdbc:derby:memory:hub_s0_4_" + UUID.randomUUID().toString().replace("-", "");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
    }
    
    static HubDatabaseTestSupport open() throws Exception {
        HubDatabaseTestSupport support = new HubDatabaseTestSupport();
        try {
            support.initialize();
            return support;
        } catch (Exception failure) {
            try {
                support.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
    
    private void initialize() throws Exception {
        connection = newConnection();
        DatabaseMetaData metadata = connection.getMetaData();
        String schema = currentSchema();
        if (schema == null && !"mysql".equals(database)) {
            throw new IllegalStateException("Cannot safely determine the current schema");
        }
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), schema,
            "%", new String[] {"TABLE", "BASE TABLE"})) {
            if (tables.next()) {
                throw new IllegalStateException(
                    "Refusing a non-empty schema; use a disposable test schema");
            }
        }
        if ("mysql".equals(database)) {
            String mode = queryString("SELECT @@SESSION.sql_mode");
            if (!mode.contains("STRICT_TRANS_TABLES") && !mode.contains("STRICT_ALL_TABLES")) {
                throw new IllegalStateException(
                    "MySQL experiment requires strict SQL mode; current mode: " + mode);
            }
        }
        if ("oracle".equals(database)) {
            String mode = environment("NACOS_HUB_TEST_ORACLE_LENGTH", "").toUpperCase(Locale.ROOT);
            if (!mode.isEmpty()) {
                if (!Set.of("BYTE", "CHAR").contains(mode)) {
                    throw new IllegalArgumentException("Oracle length mode must be BYTE or CHAR");
                }
                execute("ALTER SESSION SET NLS_LENGTH_SEMANTICS=" + mode);
            }
        }
        String module = "postgresql".equals(database) ? "postgresql" : database;
        String file = "postgresql".equals(database) ? "pg-schema.sql" : database + "-schema.sql";
        Path schemaFile = findRoot().resolve("plugin-default-impl/nacos-default-datasource-plugin")
            .resolve("nacos-datasource-plugin-" + module).resolve("src/main/resources/META-INF")
            .resolve(file);
        byte[] bytes = Files.readAllBytes(schemaFile);
        List<String> selected = selectDdl(new String(bytes, StandardCharsets.UTF_8));
        for (String sql : selected) {
            execute(sql);
            Matcher matcher = CREATE.matcher(sql);
            if (matcher.find()) {
                createdTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        System.out.println("[Hub S0-4] database=" + metadata.getDatabaseProductName()
            + ", version=" + metadata.getDatabaseProductVersion());
        System.out.println("[Hub S0-4] schemaFile=" + schemaFile);
        System.out.println("[Hub S0-4] schemaSHA256="
            + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        if ("oracle".equals(database)) {
            System.out.println("[Hub S0-4] Oracle charset=" + queryString(
                "SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET'"));
            try (Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery(
                    "SELECT table_name, char_used, char_length, data_length FROM user_tab_columns "
                        + "WHERE table_name IN ('AI_RESOURCE','AI_RESOURCE_VERSION') AND column_name='NAME'")) {
                while (columns.next()) {
                    System.out.println("[Hub S0-4] " + columns.getString(1) + ".NAME: "
                        + columns.getString(2) + ", charLength=" + columns.getInt(3)
                        + ", bytes=" + columns.getInt(4));
                }
            }
        }
    }
    
    /**
     * Selects only the three target tables and their indexes/constraints.
     */
    static List<String> selectDdl(String source) {
        List<String> result = new ArrayList<>();
        Set<String> found = new HashSet<>();
        for (String sql : splitSql(source)) {
            Matcher create = CREATE.matcher(sql);
            Matcher alter = ALTER.matcher(sql);
            Matcher index = INDEX.matcher(sql);
            if (create.find() && TABLES.contains(create.group(1).toLowerCase(Locale.ROOT))) {
                result.add(sql);
                found.add(create.group(1).toLowerCase(Locale.ROOT));
            } else if (alter.find() && TABLES.contains(alter.group(1).toLowerCase(Locale.ROOT))) {
                if (!sql.toUpperCase(Locale.ROOT)
                    .matches("(?s).*\\b(PRIMARY\\s+KEY|UNIQUE)\\b.*")) {
                    throw new IllegalArgumentException("Unexpected target-table ALTER statement");
                }
                result.add(sql);
            } else if (index.find() && TABLES.contains(index.group(1).toLowerCase(Locale.ROOT))) {
                result.add(sql);
            }
        }
        if (!found.equals(TABLES)) {
            throw new IllegalArgumentException(
                "Target CREATE TABLE statements missing from checkout Schema: " + found);
        }
        return result;
    }
    
    /**
     * Splits these Schema files without treating quoted semicolons or comments as SQL.
     */
    static List<String> splitSql(String input) {
        List<String> statements = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : 0;
            if (quote != 0) {
                sql.append(current);
                if (current == quote) {
                    if (next == quote) {
                        sql.append(next);
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                sql.append(current);
            } else if (current == '-' && next == '-') {
                while (i + 1 < input.length() && input.charAt(i + 1) != '\n') {
                    i++;
                }
                sql.append(' ');
            } else if (current == '/' && next == '*') {
                int end = input.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed SQL comment");
                }
                i = end + 1;
                sql.append(' ');
            } else if (current == ';') {
                if (!sql.toString().isBlank()) {
                    statements.add(sql.toString().trim());
                }
                sql.setLength(0);
            } else {
                sql.append(current);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed SQL quote");
        }
        if (!sql.toString().isBlank()) {
            statements.add(sql.toString().trim());
        }
        return statements;
    }
    
    Connection newConnection() throws SQLException {
        Connection result = DriverManager.getConnection(
            "derby".equals(database) ? url + ";create=true" : url, credentials);
        result.setAutoCommit(true);
        return result;
    }
    
    Connection connection() {
        return connection;
    }
    
    void reset() throws SQLException {
        if (createdTables.size() != TABLES.size()) {
            throw new IllegalStateException("Refusing cleanup before fixture initialization");
        }
        execute("DELETE FROM ai_resource_version");
        execute("DELETE FROM ai_resource");
        execute("DELETE FROM tenant_info");
    }
    
    void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
    
    int update(String sql, Object... args) throws SQLException {
        return update(connection, sql, args);
    }
    
    static int update(Connection target, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }
    
    long queryLong(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Expected one numeric result");
                }
                long value = result.getLong(1);
                if (result.wasNull() || result.next()) {
                    throw new IllegalStateException("Expected exactly one non-null numeric result");
                }
                return value;
            }
        }
    }
    
    String queryString(String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Expected one string result");
                }
                String value = result.getString(1);
                if (result.next()) {
                    throw new IllegalStateException("Expected exactly one string result");
                }
                return value;
            }
        }
    }
    
    Set<List<String>> uniqueKeys(String table) throws SQLException {
        String tableName = databaseIdentifier(table);
        boolean derby = "derby".equals(database);
        Set<String> constraintIndexes =
            derby ? derbyUniqueConstraintIndexes(tableName) : Collections.emptySet();
        Map<String, TreeMap<Short, String>> indexes = new HashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(connection.getCatalog(),
            currentSchema(), tableName, !derby, false)) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (index != null && column != null
                    && (!result.getBoolean("NON_UNIQUE") || constraintIndexes.contains(index))) {
                    indexes.computeIfAbsent(index, key -> new TreeMap<>())
                        .put(result.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
                }
            }
        }
        Set<List<String>> result = new HashSet<>();
        indexes.values().forEach(columns -> result.add(new ArrayList<>(columns.values())));
        return result;
    }
    
    private Set<String> derbyUniqueConstraintIndexes(String tableName) throws SQLException {
        // Derby backs UNIQUE constraints containing nullable columns with non-unique indexes.
        String sql = "SELECT g.CONGLOMERATENAME FROM SYS.SYSCONSTRAINTS c "
            + "JOIN SYS.SYSKEYS k ON c.CONSTRAINTID = k.CONSTRAINTID "
            + "JOIN SYS.SYSCONGLOMERATES g ON k.CONGLOMERATEID = g.CONGLOMERATEID "
            + "JOIN SYS.SYSTABLES t ON c.TABLEID = t.TABLEID "
            + "JOIN SYS.SYSSCHEMAS s ON t.SCHEMAID = s.SCHEMAID "
            + "WHERE c.TYPE = 'U' AND s.SCHEMANAME = ? AND t.TABLENAME = ?";
        Set<String> indexes = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currentSchema());
            statement.setString(2, tableName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    indexes.add(result.getString(1));
                }
            }
        }
        return indexes;
    }
    
    int columnSize(String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(),
            currentSchema(), databaseIdentifier(table), databaseIdentifier(column))) {
            if (!columns.next()) {
                throw new IllegalStateException("Column not found: " + table + "." + column);
            }
            return columns.getInt("COLUMN_SIZE");
        }
    }
    
    Object tenantTime() {
        return Set.of("oracle", "postgresql").contains(database)
            ? Timestamp.from(java.time.Instant.now()) : Long.valueOf(System.currentTimeMillis());
    }
    
    boolean isDuplicate(SQLException failure) {
        return "23505".equals(failure.getSQLState())
            || ("mysql".equals(database) && failure.getErrorCode() == 1062)
            || ("oracle".equals(database) && failure.getErrorCode() == 1);
    }
    
    boolean isTooLong(SQLException failure) {
        return "22001".equals(failure.getSQLState())
            || ("mysql".equals(database) && failure.getErrorCode() == 1406)
            || ("oracle".equals(database) && failure.getErrorCode() == 12899);
    }
    
    private String databaseIdentifier(String name) {
        return Set.of("derby", "oracle").contains(database) ? name.toUpperCase(Locale.ROOT) : name;
    }
    
    private String currentSchema() throws SQLException {
        if ("mysql".equals(database)) {
            return null;
        }
        if ("oracle".equals(database)) {
            return queryString("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM dual");
        }
        return connection.getSchema();
    }
    
    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }
    
    private static Path findRoot() {
        String configured = System.getProperty("nacos.hub.test.repoRoot");
        Path start = Path.of(configured == null ? System.getProperty("user.dir") : configured)
            .toAbsolutePath().normalize();
        for (Path path = start; path != null; path = path.getParent()) {
            if (Files
                .isDirectory(path.resolve("plugin-default-impl/nacos-default-datasource-plugin"))) {
                return path;
            }
        }
        throw new IllegalStateException(
            "Cannot find checkout root; set -Dnacos.hub.test.repoRoot=<absolute path>");
    }
    
    private static String environment(String name, String fallback) {
        String result = System.getenv(name);
        return result == null ? fallback : result;
    }
    
    @Override
    public void close() throws Exception {
        if (connection == null) {
            return;
        }
        SQLException failure = null;
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            List<String> reverse = new ArrayList<>(createdTables);
            Collections.reverse(reverse);
            for (String table : reverse) {
                try {
                    execute("DROP TABLE " + table);
                } catch (SQLException cleanup) {
                    if (failure == null) {
                        failure = cleanup;
                    } else {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
        } finally {
            connection.close();
            if ("derby".equals(database)) {
                try (Connection ignored = DriverManager.getConnection(url + ";drop=true")) {
                    // Derby normally signals successful database drop with SQLState 08006.
                } catch (SQLException drop) {
                    if (!"08006".equals(drop.getSQLState())) {
                        if (failure == null) {
                            failure = drop;
                        } else {
                            failure.addSuppressed(drop);
                        }
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
