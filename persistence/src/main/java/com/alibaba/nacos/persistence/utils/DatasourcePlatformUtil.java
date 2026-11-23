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

package com.alibaba.nacos.persistence.utils;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Locale;

/**
 * get datasource platform util.
 *
 * @author lixiaoshuang
 */
public class DatasourcePlatformUtil {
    
    /**
     * get datasource platform.
     *
     * <p>The configured name is trimmed and returned in lower case. Every datasource dialect declares its type in lower
     * case, and the mappers are registered under that name.
     *
     * @param defaultPlatform default platform.
     * @return datasource platform in lower case
     */
    public static String getDatasourcePlatform(String defaultPlatform) {
        String result = EnvUtil.getProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY);
        if (StringUtils.isNotBlank(result)) {
            return normalize(result);
        }
        result = EnvUtil.getProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY);
        return StringUtils.isBlank(result) ? defaultPlatform : normalize(result);
    }
    
    private static String normalize(String platform) {
        return platform.trim().toLowerCase(Locale.ROOT);
    }
    
    /**
     * Check whether the datasource platform selects a local embedded database engine.
     *
     * @param platform datasource platform as returned by {@link #getDatasourcePlatform(String)}
     * @return {@code true} for {@code derby} and {@code h2}; {@code false} for any other platform
     */
    public static boolean isEmbeddedPlatform(String platform) {
        return PersistenceConstant.DERBY.equalsIgnoreCase(platform)
            || PersistenceConstant.H2.equalsIgnoreCase(platform);
    }
}
