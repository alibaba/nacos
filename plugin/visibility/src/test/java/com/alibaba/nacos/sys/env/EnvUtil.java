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

package com.alibaba.nacos.sys.env;

/**
 * Test EnvUtil for visibility plugin, which avoids a production dependency on nacos-sys.
 *
 * @author xiweng.yy
 */
public final class EnvUtil {
    
    private static Object properties;
    
    private static boolean throwException;
    
    private EnvUtil() {
    }
    
    public static Object getProperties() {
        if (throwException) {
            throw new IllegalStateException("failed to load properties");
        }
        return properties == null ? System.getProperties() : properties;
    }
    
    public static void setProperties(Object properties) {
        EnvUtil.properties = properties;
    }
    
    public static void setThrowException(boolean throwException) {
        EnvUtil.throwException = throwException;
    }
    
    public static void reset() {
        properties = null;
        throwException = false;
    }
}
