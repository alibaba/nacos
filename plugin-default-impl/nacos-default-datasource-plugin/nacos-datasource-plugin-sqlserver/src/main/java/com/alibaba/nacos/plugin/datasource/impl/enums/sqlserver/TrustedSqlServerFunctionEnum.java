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

package com.alibaba.nacos.plugin.datasource.impl.enums.sqlserver;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL Server trusted function enum.
 *
 * @author blake.qiu、ThinkGem
 */
public enum TrustedSqlServerFunctionEnum {
    
    /**
     * NOW() function maps to SQL Server's SYSDATETIME().
     */
    NOW("NOW()", "SYSDATETIME()");
    
    private static final Map<String, TrustedSqlServerFunctionEnum> LOOKUP_MAP = new HashMap<>();
    
    static {
        for (TrustedSqlServerFunctionEnum entry : TrustedSqlServerFunctionEnum.values()) {
            LOOKUP_MAP.put(entry.functionName, entry);
        }
    }
    
    private final String functionName;
    
    private final String function;
    
    TrustedSqlServerFunctionEnum(String functionName, String function) {
        this.functionName = functionName;
        this.function = function;
    }
    
    /**
     * Get the SQL Server function by function name.
     *
     * @param functionName the function name (e.g. NOW())
     * @return the SQL Server function (e.g. SYSDATETIME())
     * @throws IllegalArgumentException if the function name is not in the trusted list
     */
    public static String getFunctionByName(String functionName) {
        TrustedSqlServerFunctionEnum entry = LOOKUP_MAP.get(functionName);
        if (entry != null) {
            return entry.function;
        }
        throw new IllegalArgumentException(String.format("Invalid function name: %s", functionName));
    }
}
