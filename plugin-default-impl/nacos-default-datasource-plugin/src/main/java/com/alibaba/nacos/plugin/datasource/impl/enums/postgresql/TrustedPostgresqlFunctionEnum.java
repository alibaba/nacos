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

package com.alibaba.nacos.plugin.datasource.impl.enums.postgresql;

import java.util.HashMap;
import java.util.Map;

/**
 * The TrustedSqlFunctionEnum enum class is used to enumerate and manage a list of trusted built-in SQL functions.
 * By using this enum, you can verify whether a given SQL function is part of the trusted functions list
 * to avoid potential SQL injection risks.
 *
 * @author caoyanan
 */
public enum TrustedPostgresqlFunctionEnum {

    /**
     * NOW().
     */
    NOW("NOW()", "NOW()");

    private static final Map<String, TrustedPostgresqlFunctionEnum> LOOKUP_MAP = new HashMap<>();

    static {
        for (TrustedPostgresqlFunctionEnum entry : TrustedPostgresqlFunctionEnum.values()) {
            LOOKUP_MAP.put(entry.functionName, entry);
        }
    }

    private final String functionName;

    private final String function;

    TrustedPostgresqlFunctionEnum(String functionName, String function) {
        this.functionName = functionName;
        this.function = function;
    }

    /**
     * Get the function name.
     *
     * @param functionName function name
     * @return function
     */
    public static String getFunctionByName(String functionName) {
        TrustedPostgresqlFunctionEnum entry = LOOKUP_MAP.get(functionName);
        if (entry != null) {
            return entry.function;
        }
        throw new IllegalArgumentException(String.format("Invalid function name: %s", functionName));
    }
}
