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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface BaseDatabaseOperate {

    default  <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Class<R> cls) {
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

    default <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> cls) {
        try {
            return jdbcTemplate.queryForObject(sql, args, cls);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }


    default <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.queryForObject(sql, args, mapper);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }


    default <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.query(sql, args, mapper);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }


    default <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> rClass) {
        try {
            return jdbcTemplate.queryForList(sql, args, rClass);
        }
        catch (IncorrectResultSizeDataAccessException e) {
            return null;
        }
        catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }


    default List<Map<String, Object>> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                    sql,
                    args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }

    default Boolean update(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate, List<ModifyRequest> contexts) {
        return transactionTemplate.execute(status -> {
            String[] errSql = new String[]{null};
            Object[][] args = new Object[][]{null};
            try {
                contexts.forEach(pair -> {
                    errSql[0] = pair.getSql();
                    args[0] = pair.getArgs();
                    LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog, "current sql : {}", errSql[0]);
                    LoggerUtils.printIfDebugEnabled(LogUtil.defaultLog, "current args : {}", args[0]);
                    jdbcTemplate.update(pair.getSql(), pair.getArgs());
                });
                return Boolean.TRUE;
            }
            catch (BadSqlGrammarException | DataIntegrityViolationException e) {
                fatalLog.error("[db-error] sql : {}, args : {}, error : {}", errSql[0], args[0], e.toString());
                return false;
            }
            catch (CannotGetJdbcConnectionException e) {
                fatalLog.error("[db-error] sql : {}, args : {}, error : {}", errSql[0], args[0], e.toString());
                throw e;
            } catch (DataAccessException e) {
                fatalLog.error("[db-error] DataAccessException sql : {}, args : {}, error : {}",
                        errSql[0],
                        args[0],
                        ExceptionUtil.getAllExceptionMsg(e));
                throw e;
            }
        });
    }
}
