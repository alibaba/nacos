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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.utils.DerbyUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * The Derby database basic operation.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface BaseDatabaseOperate extends DatabaseOperate {
    
    /**
     * query one result by sql then convert result to target type.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param cls          target type
     * @param <R>          target type
     * @return R
     */
    default <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Class<R> cls) {
        try {
            return jdbcTemplate.queryForObject(sql, cls);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] can't get connection : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException : {}", ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * query one result by sql and args then convert result to target type.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param args         args
     * @param cls          target type
     * @param <R>          target type
     * @return R
     */
    default <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> cls) {
        try {
            return jdbcTemplate.queryForObject(sql, args, cls);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", sql, args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * query one result by sql and args then convert result to target type through {@link RowMapper}.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param args         args
     * @param mapper       {@link RowMapper}
     * @param <R>          target type
     * @return R
     */
    default <R> R queryOne(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.queryForObject(sql, args, mapper);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", sql, args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * query many result by sql and args then convert result to target type through {@link RowMapper}.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param args         args
     * @param mapper       {@link RowMapper}
     * @param <R>          target type
     * @return result list
     */
    default <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, RowMapper<R> mapper) {
        try {
            return jdbcTemplate.query(sql, args, mapper);
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", sql, args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * query many result by sql and args then convert result to target type.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param args         args
     * @param rClass       target type class
     * @param <R>          target type
     * @return result list
     */
    default <R> List<R> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args, Class<R> rClass) {
        try {
            return jdbcTemplate.queryForList(sql, args, rClass);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", sql, args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * query many result by sql and args then convert result to List&lt;Map&lt;String, Object&gt;&gt;.
     *
     * @param jdbcTemplate {@link JdbcTemplate}
     * @param sql          sql
     * @param args         args
     * @return List&lt;Map&lt;String, Object&gt;&gt;
     */
    default List<Map<String, Object>> queryMany(JdbcTemplate jdbcTemplate, String sql, Object[] args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (CannotGetJdbcConnectionException e) {
            FATAL_LOG.error("[db-error] {}", e.toString());
            throw e;
        } catch (DataAccessException e) {
            FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", sql, args,
                    ExceptionUtil.getAllExceptionMsg(e));
            throw e;
        }
    }
    
    /**
     * execute update operation.
     *
     * @param transactionTemplate {@link TransactionTemplate}
     * @param jdbcTemplate        {@link JdbcTemplate}
     * @param contexts            {@link List} ModifyRequest list
     * @return {@link Boolean}
     */
    default Boolean update(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate,
            List<ModifyRequest> contexts) {
        return update(transactionTemplate, jdbcTemplate, contexts, null);
    }
    
    /**
     * execute update operation, to fix #3617.
     *
     * @param transactionTemplate {@link TransactionTemplate}
     * @param jdbcTemplate        {@link JdbcTemplate}
     * @param contexts            {@link List} ModifyRequest list
     * @return {@link Boolean}
     */
    default Boolean update(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate,
            List<ModifyRequest> contexts, BiConsumer<Boolean, Throwable> consumer) {
        return transactionTemplate.execute(status -> {
            String[] errSql = new String[] {null};
            Object[][] args = new Object[][] {null};
            try {
                contexts.forEach(pair -> {
                    errSql[0] = pair.getSql();
                    args[0] = pair.getArgs();
                    LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "current sql : {}", errSql[0]);
                    LoggerUtils.printIfDebugEnabled(LogUtil.DEFAULT_LOG, "current args : {}", args[0]);
                    jdbcTemplate.update(pair.getSql(), pair.getArgs());
                });
                if (consumer != null) {
                    consumer.accept(Boolean.TRUE, null);
                }
                return Boolean.TRUE;
            } catch (BadSqlGrammarException | DataIntegrityViolationException e) {
                FATAL_LOG.error("[db-error] sql : {}, args : {}, error : {}", errSql[0], args[0], e.toString());
                if (consumer != null) {
                    consumer.accept(Boolean.FALSE, e);
                }
                return Boolean.FALSE;
            } catch (CannotGetJdbcConnectionException e) {
                FATAL_LOG.error("[db-error] sql : {}, args : {}, error : {}", errSql[0], args[0], e.toString());
                throw e;
            } catch (DataAccessException e) {
                FATAL_LOG.error("[db-error] DataAccessException sql : {}, args : {}, error : {}", errSql[0], args[0],
                        ExceptionUtil.getAllExceptionMsg(e));
                throw e;
            }
        });
    }
    
    /**
     * Perform data import.
     *
     * @param template {@link JdbcTemplate}
     * @param requests {@link List} ModifyRequest list
     * @return {@link Boolean}
     */
    default Boolean doDataImport(JdbcTemplate template, List<ModifyRequest> requests) {
        final String[] sql = requests.stream().map(ModifyRequest::getSql).map(DerbyUtils::insertStatementCorrection)
                .toArray(String[]::new);
        int[] affect = template.batchUpdate(sql);
        return IntStream.of(affect).count() == requests.size();
    }
    
}
