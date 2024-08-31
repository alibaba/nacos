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

package com.alibaba.nacos.persistence.repository.embedded.sql.limiter;

import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.persistence.repository.embedded.sql.SelectRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlTypeLimiterTest {
    
    SqlTypeLimiter sqlLimiter;
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        sqlLimiter = new SqlTypeLimiter();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testDoLimitForModifyRequestForDdl() throws SQLException {
        ModifyRequest createTable = new ModifyRequest("create table test(id int,name varchar(255))");
        ModifyRequest createIndex = new ModifyRequest("create index test_index on test(id)");
        ModifyRequest alterTable = new ModifyRequest("alter table test add column age int");
        List<ModifyRequest> modifyRequests = new LinkedList<>();
        modifyRequests.add(createTable);
        modifyRequests.add(createIndex);
        modifyRequests.add(alterTable);
        sqlLimiter.doLimitForModifyRequest(modifyRequests);
    }
    
    @Test
    void testDoLimitForModifyRequestForDdlForEmptyToken() throws SQLException {
        ModifyRequest create = new ModifyRequest("create ");
        assertThrows(SQLException.class, () -> sqlLimiter.doLimitForModifyRequest(create));
    }
    
    @Test
    void testDoLimitForModifyRequestForDdlForOneToken() throws SQLException {
        ModifyRequest create = new ModifyRequest("create");
        assertThrows(SQLException.class, () -> sqlLimiter.doLimitForModifyRequest(create));
    }
    
    @Test
    void testDoLimitForModifyRequestForDdlForInvalidSecondToken() throws SQLException {
        ModifyRequest create = new ModifyRequest("create xxx");
        assertThrows(SQLException.class, () -> sqlLimiter.doLimitForModifyRequest(create));
    }
    
    @Test
    void testDoLimitForModifyRequestForDml() throws SQLException {
        ModifyRequest insert = new ModifyRequest("insert into test(id,name) values(1,'test')");
        ModifyRequest update = new ModifyRequest("update test set name='test' where id=1");
        ModifyRequest delete = new ModifyRequest("delete from test where id=1");
        List<ModifyRequest> modifyRequests = new LinkedList<>();
        modifyRequests.add(insert);
        modifyRequests.add(update);
        modifyRequests.add(delete);
        sqlLimiter.doLimitForModifyRequest(modifyRequests);
    }
    
    @Test
    void testDoLimitForModifyRequestForDmlInvalid() throws SQLException {
        ModifyRequest insert = new ModifyRequest("insert into test(id,name) values(1,'test')");
        ModifyRequest invalid = new ModifyRequest("CALL SALES.TOTAL_REVENUES()");
        List<ModifyRequest> modifyRequests = new LinkedList<>();
        modifyRequests.add(insert);
        modifyRequests.add(invalid);
        assertThrows(SQLException.class, () -> sqlLimiter.doLimitForModifyRequest(modifyRequests));
    }
    
    @Test
    void testDoLimitForSelectRequest() throws SQLException {
        SelectRequest selectRequest = SelectRequest.builder().sql("select * from test").build();
        sqlLimiter.doLimitForSelectRequest(selectRequest);
    }
    
    @Test
    void testDoLimitForSelectRequestInvalid() throws SQLException {
        SelectRequest selectRequest = SelectRequest.builder().sql("select * from test").build();
        SelectRequest invalid = SelectRequest.builder().sql("CALL SALES.TOTAL_REVENUES()").build();
        List<SelectRequest> selectRequests = new LinkedList<>();
        selectRequests.add(selectRequest);
        selectRequests.add(invalid);
        assertThrows(SQLException.class, () -> sqlLimiter.doLimitForSelectRequest(selectRequests));
    }
    
    @Test
    void testDoLimit() {
        List<String> sql = new LinkedList<>();
        sql.add("create table test(id int,name varchar(255))");
        sql.add("select * from test");
        sql.add("CALL SALES.TOTAL_REVENUES();");
        assertThrows(SQLException.class, () -> sqlLimiter.doLimit(sql));
    }
    
    @Test
    void testDoLimitForDisabledLimit() throws SQLException {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.persistence.sql.derby.limit.enabled", "false");
        EnvUtil.setEnvironment(environment);
        sqlLimiter = new SqlTypeLimiter();
        sqlLimiter.doLimit("CALL SALES.TOTAL_REVENUES();");
    }
}