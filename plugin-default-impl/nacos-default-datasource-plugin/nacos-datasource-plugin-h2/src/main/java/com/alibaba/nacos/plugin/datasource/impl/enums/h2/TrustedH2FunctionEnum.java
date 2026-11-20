/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.impl.enums.h2;

import java.util.HashMap;
import java.util.Map;

/**
 * Trusted H2 function enum. Only the logical function names listed here are translated into SQL.
 *
 * @author Nacos
 */
public enum TrustedH2FunctionEnum {
    
    /**
     * NOW().
     */
    NOW("NOW()", "CURRENT_TIMESTAMP"),
    
    /**
     * CURRENT_TIMESTAMP().
     */
    CURRENT_TIMESTAMP("CURRENT_TIMESTAMP()", "CURRENT_TIMESTAMP");
    
    private static final Map<String, String> LOOKUP_MAP = new HashMap<>();
    
    static {
        for (TrustedH2FunctionEnum entry : TrustedH2FunctionEnum.values()) {
            LOOKUP_MAP.put(entry.functionName, entry.function);
        }
    }
    
    private final String functionName;
    
    private final String function;
    
    TrustedH2FunctionEnum(String functionName, String function) {
        this.functionName = functionName;
        this.function = function;
    }
    
    /**
     * Get the SQL function for a trusted function name.
     *
     * @param functionName function name
     * @return function
     * @throws IllegalArgumentException when the function name is not trusted
     */
    public static String getFunctionByName(String functionName) {
        String function = LOOKUP_MAP.get(functionName);
        if (null != function) {
            return function;
        }
        throw new IllegalArgumentException(
            String.format("Invalid function name: %s", functionName));
    }
}
