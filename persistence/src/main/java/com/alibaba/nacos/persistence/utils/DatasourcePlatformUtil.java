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

/**
 * get datasource platform util.
 *
 * @author lixiaoshuang
 */
public class DatasourcePlatformUtil {
    
    /**
     * get datasource platform.
     *
     * @param defaultPlatform default platform.
     * @return
     */
    public static String getDatasourcePlatform(String defaultPlatform) {
        String result = EnvUtil.getProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY);
        if (StringUtils.isNotBlank(result)) {
            return result.trim();
        }
        result = EnvUtil.getProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY);
        return StringUtils.isBlank(result) ? defaultPlatform : result.trim();
    }
    
    /**
     * Check whether the datasource platform selects a local embedded database engine.
     *
     * <p>Derby is matched case-insensitively, as it always was. H2 is matched exactly, as the dialect plugin lookup
     * matches its name. A platform that spells H2 in another case is not the embedded H2 engine, and the startup
     * checks reject it.
     *
     * @param platform datasource platform as returned by {@link #getDatasourcePlatform(String)}
     * @return {@code true} for {@code derby} and {@code h2}; {@code false} for any other platform
     */
    public static boolean isEmbeddedPlatform(String platform) {
        return PersistenceConstant.DERBY.equalsIgnoreCase(platform)
            || PersistenceConstant.H2.equals(platform);
    }
}
