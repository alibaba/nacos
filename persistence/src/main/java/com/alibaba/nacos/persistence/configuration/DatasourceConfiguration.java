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

package com.alibaba.nacos.persistence.configuration;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Configuration about datasource.
 *
 * @author xiweng.yy
 */
public class DatasourceConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    /**
     * Standalone mode uses DB.
     */
    public static boolean useExternalDB = false;
    
    /**
     * Inline storage value = ${nacos.standalone}.
     */
    public static boolean embeddedStorage = EnvUtil.getStandaloneMode();
    
    public static boolean isUseExternalDB() {
        return useExternalDB;
    }
    
    public static void setUseExternalDB(boolean useExternalDB) {
        DatasourceConfiguration.useExternalDB = useExternalDB;
    }
    
    public static boolean isEmbeddedStorage() {
        return embeddedStorage;
    }
    
    public static void setEmbeddedStorage(boolean embeddedStorage) {
        DatasourceConfiguration.embeddedStorage = embeddedStorage;
    }
    
    private void loadDatasourceConfiguration() {
        // External data sources are used by default in cluster mode
        String platform = DatasourcePlatformUtil.getDatasourcePlatform("");
        boolean useExternalStorage =
                !PersistenceConstant.EMPTY_DATASOURCE_PLATFORM.equalsIgnoreCase(platform) && !PersistenceConstant.DERBY
                        .equalsIgnoreCase(platform);
        setUseExternalDB(useExternalStorage);
        
        // must initialize after setUseExternalDB
        // This value is true in stand-alone mode and false in cluster mode
        // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
        // default value is depend on ${nacos.standalone}
        
        if (isUseExternalDB()) {
            setEmbeddedStorage(false);
        } else {
            boolean embeddedStorage = isEmbeddedStorage() || Boolean.getBoolean(PersistenceConstant.EMBEDDED_STORAGE);
            setEmbeddedStorage(embeddedStorage);
            
            // If the embedded data source storage is not turned on, it is automatically
            // upgraded to the external data source storage, as before
            if (!embeddedStorage) {
                setUseExternalDB(true);
            }
        }
    }
    
    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        loadDatasourceConfiguration();
    }
}
