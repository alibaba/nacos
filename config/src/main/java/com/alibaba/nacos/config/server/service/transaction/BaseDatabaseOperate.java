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

package com.alibaba.nacos.config.server.service.transaction;

import com.alibaba.nacos.core.utils.ExceptionUtil;
import org.javatuples.Pair;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class BaseDatabaseOperate {
    
    public <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Class<R> cls) {
        try {
            return jdbcTemplate.queryForObject(sql, cls);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] can't get connection : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    
    public <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> cls) {
        try {
            return jdbcTemplate.queryForObject(sql, args, cls);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    
    public <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.queryForObject(sql, args, mapper);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    
    public <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.query(sql, args, mapper);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    
    public <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> rClass) {
        try {
            return jdbcTemplate.queryForList(sql, args, rClass);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    
    public List<Map<String, Object>> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    public Boolean update(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate, List<Pair<String, Object[]>> sqlContext) {
        return transactionTemplate.execute(status -> {
            String[] errSql = new String[]{ null };
            Object[][] args = new Object[][]{ null };
            try {
                sqlContext.forEach(pair -> {
                    errSql[0] = pair.getValue0();
                    args[0] = pair.getValue1();
                    jdbcTemplate.update(pair.getValue0(), pair.getValue1());
                });
            } catch (TransactionException e) {
                fatalLog.error("[db-error] " + e.toString(), e);
                return false;
            } catch (CannotGetJdbcConnectionException e) {
                fatalLog.error("[db-error] " + e.toString(), e);
                throw e;
            } catch (DataAccessException e) {
                fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                        errSql[0],
                        args[0],
                        ExceptionUtil.getAllExceptionMsg(e));
                throw e;
            }
            return Boolean.TRUE;
        });
    }
}
