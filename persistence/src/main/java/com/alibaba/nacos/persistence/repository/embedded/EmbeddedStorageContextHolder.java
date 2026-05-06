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

package com.alibaba.nacos.persistence.repository.embedded;

import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded storae context holder.
 *
 * @author xiweng.yy
 */
public class EmbeddedStorageContextHolder {
    
    private static final ThreadLocal<ArrayList<ModifyRequest>> SQL_CONTEXT = ThreadLocal.withInitial(ArrayList::new);
    
    private static final ThreadLocal<Map<String, String>> EXTEND_INFO_CONTEXT = ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Add sql context.
     *
     * @param sql  sql
     * @param args argument list
     */
    public static void addSqlContext(String sql, Object... args) {
        ArrayList<ModifyRequest> requests = SQL_CONTEXT.get();
        ModifyRequest context = new ModifyRequest();
        context.setExecuteNo(requests.size());
        context.setSql(sql);
        context.setArgs(args);
        requests.add(context);
        SQL_CONTEXT.set(requests);
    }
    
    /**
     * Add sql context.
     *
     * @param rollbackOnUpdateFail  roll back when update fail
     * @param sql  sql
     * @param args argument list
     */
    public static void addSqlContext(boolean rollbackOnUpdateFail, String sql, Object... args) {
        ArrayList<ModifyRequest> requests = SQL_CONTEXT.get();
        ModifyRequest context = new ModifyRequest();
        context.setExecuteNo(requests.size());
        context.setSql(sql);
        context.setArgs(args);
        context.setRollBackOnUpdateFail(rollbackOnUpdateFail);
        requests.add(context);
        SQL_CONTEXT.set(requests);
    }
    
    /**
     * Put extend info.
     *
     * @param key   key
     * @param value value
     */
    public static void putExtendInfo(String key, String value) {
        Map<String, String> old = EXTEND_INFO_CONTEXT.get();
        old.put(key, value);
        EXTEND_INFO_CONTEXT.set(old);
    }
    
    /**
     * Put all extend info.
     *
     * @param map all extend info
     */
    public static void putAllExtendInfo(Map<String, String> map) {
        Map<String, String> old = EXTEND_INFO_CONTEXT.get();
        old.putAll(map);
        EXTEND_INFO_CONTEXT.set(old);
    }
    
    /**
     * Determine if key is included.
     *
     * @param key key
     * @return {@code true} if contains key
     */
    public static boolean containsExtendInfo(String key) {
        Map<String, String> extendInfo = EXTEND_INFO_CONTEXT.get();
        boolean exist = extendInfo.containsKey(key);
        EXTEND_INFO_CONTEXT.set(extendInfo);
        return exist;
    }
    
    public static List<ModifyRequest> getCurrentSqlContext() {
        return SQL_CONTEXT.get();
    }
    
    public static Map<String, String> getCurrentExtendInfo() {
        return EXTEND_INFO_CONTEXT.get();
    }
    
    public static void cleanAllContext() {
        SQL_CONTEXT.remove();
        EXTEND_INFO_CONTEXT.remove();
    }
}
