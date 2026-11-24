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

package com.alibaba.nacos.persistence;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2 test utilities.
 *
 * @author Nacos
 */
public final class H2TestUtils {
    
    private H2TestUtils() {
    }
    
    /**
     * Write the schema script that overrides the bundled one, as an operator does in {@code conf/}.
     *
     * @param nacosHome isolated nacos home
     * @param script    the content of {@code conf/h2-schema.sql}
     * @throws IOException when the file cannot be written
     */
    public static void writeSchema(Path nacosHome, String script) throws IOException {
        Path confDir = Files.createDirectories(nacosHome.resolve("conf"));
        Files.write(confDir.resolve("h2-schema.sql"), script.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Count the sessions that are open on the H2 database of a nacos home, the session of this call included.
     *
     * <p>A connection pool that is still open holds at least one session. So {@code 1} means that nothing but this
     * call is connected to the database.
     *
     * @param nacosHome isolated nacos home
     * @return the number of open sessions
     * @throws SQLException when the database cannot be read
     */
    public static int countOpenSessions(Path nacosHome) throws SQLException {
        String jdbcUrl = "jdbc:h2:file:"
            + nacosHome.resolve("data").resolve(PersistenceConstant.H2_BASE_DIR).resolve("nacos")
            + ";WRITE_DELAY=0;TRACE_LEVEL_FILE=0";
        try (Connection probe = DriverManager.getConnection(jdbcUrl, "nacos", "nacos");
            Statement statement = probe.createStatement();
            ResultSet sessions = statement.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")) {
            sessions.next();
            return sessions.getInt(1);
        }
    }
}
