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

package com.alibaba.nacos.config.server;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.Test;

import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DerbyTest {

    private static final String JDBC_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String USER_NAME = "nacos";
    private static final String PASSWORD = "nacos";
    private static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";

    @Test
    public void test_connect_derby() throws SQLException, ClassNotFoundException {
        Class.forName(JDBC_DRIVER_NAME);

        Connection connection = DriverManager.getConnection("jdbc:derby:" + NACOS_HOME + File.separator + DERBY_BASE_DIR + ";create=true",
                USER_NAME, PASSWORD);

        connection.prepareStatement("INSERT INTO users (username, password, enabled) VALUES ('qwasdasdqwq', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE)").executeUpdate();

    }

}
