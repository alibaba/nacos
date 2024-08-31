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

package com.alibaba.nacos.persistence.utils;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataSource Connection CheckUtil Unit Test.
 *
 * @author Long Yu
 */
class ConnectionCheckUtilTest {
    
    @Test
    void testCheckConnectionThrowException() throws SQLException {
        assertThrows(RuntimeException.class, () -> {
            HikariDataSource ds = mock(HikariDataSource.class);
            when(ds.getConnection()).thenThrow(new RuntimeException());
            ConnectionCheckUtil.checkDataSourceConnection(ds);
            verify(ds).getConnection();
        });
    }
    
    @Test
    void testCheckConnectionNormal() throws SQLException {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection);
        ConnectionCheckUtil.checkDataSourceConnection(ds);
        verify(ds).getConnection();
        verify(connection).close();
    }
    
}
