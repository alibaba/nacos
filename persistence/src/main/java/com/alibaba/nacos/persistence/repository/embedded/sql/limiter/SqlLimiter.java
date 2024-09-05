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

import java.sql.SQLException;
import java.util.List;

/**
 * SQL limiter.
 *
 * @author xiweng.yy
 */
public interface SqlLimiter {
    
    /**
     * Do SQL limit for modify request.
     *
     * @param modifyRequest modify request
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimitForModifyRequest(ModifyRequest modifyRequest) throws SQLException;
    
    /**
     * Do SQL limit for modify request.
     *
     * @param modifyRequests modify request
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimitForModifyRequest(List<ModifyRequest> modifyRequests) throws SQLException;
    
    /**
     * Do SQL limit for select request.
     *
     * @param selectRequest select request
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimitForSelectRequest(SelectRequest selectRequest) throws SQLException;
    
    /**
     * Do SQL limit for select request.
     *
     * @param selectRequests select request
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimitForSelectRequest(List<SelectRequest> selectRequests) throws SQLException;
    
    /**
     * Do SQL limit for sql.
     *
     * @param sql SQL
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimit(String sql) throws SQLException;
    
    /**
     * Do SQL limit for sql.
     *
     * @param sql SQL
     * @throws SQLException when SQL match the limit rule.
     */
    void doLimit(List<String> sql) throws SQLException;
}
