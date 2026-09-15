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
import org.springframework.core.env.Environment;

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
     * get datasource platform from the given environment.
     *
     * @param environment     environment to read the dialect selection from
     * @param defaultPlatform default platform.
     * @return selected datasource platform, or {@code defaultPlatform} when not configured
     */
    public static String getDatasourcePlatform(Environment environment, String defaultPlatform) {
        if (environment == null) {
            return defaultPlatform;
        }
        String result =
            environment.getProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY);
        if (StringUtils.isNotBlank(result)) {
            return result.trim();
        }
        result = environment.getProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY);
        return StringUtils.isBlank(result) ? defaultPlatform : result.trim();
    }
}
