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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface DatabaseOperate {

    /**
     * Data query transaction
     *
     * @param sql sqk text
     * @param cls target type
     * @param <R> return type
     * @return query result
     */
    <R> R queryOne(String sql, Class<R> cls);

    /**
     * Data query transaction
     *
     * @param sql  sqk text
     * @param args sql parameters
     * @param cls  target type
     * @param <R>  return type
     * @return query result
     */
    <R> R queryOne(String sql, Object[] args, Class<R> cls);

    /**
     * Data query transaction
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param mapper Database query result converter
     * @param <R>    return type
     * @return query result
     */
    <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper);

    /**
     * Data query transaction
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param mapper Database query result converter
     * @param <R>    return type
     * @return query result
     */
    <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper);

    /**
     * Data query transaction
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param rClass target type
     * @param <R>    return type
     * @return query result
     */
    <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass);

    /**
     * Data query transaction
     *
     * @param sql  sqk text
     * @param args sql parameters
     * @return query result
     */
    List<Map<String, Object>> queryMany(String sql, Object[] args);

    /**
     * data modify transaction
     *
     * @param modifyRequests {@link List<  ModifyRequest  >}
     * @param consumer {@link BiConsumer}
     * @return is success
     */
    Boolean update(List<ModifyRequest> modifyRequests, BiConsumer<Boolean, Throwable> consumer);

    /**
     * data modify transaction
     *
     * @param modifyRequests {@link List<  ModifyRequest  >}
     * @return is success
     */
    default Boolean update(List<ModifyRequest> modifyRequests) {
        return update(modifyRequests, null);
    }

    /**
     * data modify transaction
     * The SqlContext to be executed in the current thread will be executed and automatically cleared
     *
     * @return is success
     */
    default Boolean blockUpdate() {
        try {
            return update(EmbeddedStorageContextUtils.getCurrentSqlContext(), null);
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }

    /**
     * data modify transaction
     * The SqlContext to be executed in the current thread will be executed and automatically cleared
     *
     * @return is success
     */
    default CompletableFuture<Boolean> futureUpdate() {
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            update(EmbeddedStorageContextUtils.getCurrentSqlContext(), (o, throwable) -> {
                if (Objects.nonNull(throwable)) {
                    future.completeExceptionally(throwable);
                    return;
                }
                future.complete(o);
            });
            return future;
        } finally {
            EmbeddedStorageContextUtils.cleanAllContext();
        }
    }

}
