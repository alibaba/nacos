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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.File;

import com.alibaba.nacos.client.env.NacosClientProperties;

/**
 * Cache Dir Utils.
 *
 * @author zongkang.guo
 */
public class CacheDirUtil {
    
    private static String cacheDir;
    
    private static final String JM_SNAPSHOT_PATH_PROPERTY = "JM.SNAPSHOT.PATH";
    
    private static final String FILE_PATH_NACOS = "nacos";
    
    private static final String FILE_PATH_NAMING = "naming";
    
    private static final String USER_HOME_PROPERTY = "user.home";
    
    /**
     * Init cache dir.
     *
     * @param namespace  namespace.
     * @param properties nacosClientProperties.
     * @return
     */
    public static String initCacheDir(String namespace, NacosClientProperties properties) {
        
        String jmSnapshotPath = properties.getProperty(JM_SNAPSHOT_PATH_PROPERTY);
        
        String namingCacheRegistryDir = "";
        if (properties.getProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR) != null) {
            namingCacheRegistryDir =
                    File.separator + properties.getProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR);
        }
        
        if (!StringUtils.isBlank(jmSnapshotPath)) {
            cacheDir = jmSnapshotPath + File.separator + FILE_PATH_NACOS + namingCacheRegistryDir + File.separator
                    + FILE_PATH_NAMING + File.separator + namespace;
        } else {
            cacheDir =
                    properties.getProperty(USER_HOME_PROPERTY) + File.separator + FILE_PATH_NACOS + namingCacheRegistryDir
                            + File.separator + FILE_PATH_NAMING + File.separator + namespace;
        }
        
        return cacheDir;
    }
    
    public static String getCacheDir() {
        return cacheDir;
    }
}
