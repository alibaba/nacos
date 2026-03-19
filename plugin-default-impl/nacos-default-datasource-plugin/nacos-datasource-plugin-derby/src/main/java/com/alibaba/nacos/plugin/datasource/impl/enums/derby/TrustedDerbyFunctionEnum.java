/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.impl.enums.derby;

import java.util.HashMap;
import java.util.Map;

/**
 * Trusted Derby Function Enum.
 *
 * @author xiweng.yy
 */
public enum TrustedDerbyFunctionEnum {

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
        for (TrustedDerbyFunctionEnum entry : TrustedDerbyFunctionEnum.values()) {
            LOOKUP_MAP.put(entry.functionName, entry.function);
        }
    }

    private final String functionName;

    private final String function;

    TrustedDerbyFunctionEnum(String functionName, String function) {
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
        String function = LOOKUP_MAP.get(functionName);
        if (null != function) {
            return function;
        }
        throw new IllegalArgumentException(String.format("Invalid function name: %s", functionName));
    }
}
