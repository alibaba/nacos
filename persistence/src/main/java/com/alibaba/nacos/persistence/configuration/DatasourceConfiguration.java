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
public class DatasourceConfiguration
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    /**
     * Standalone mode uses DB.
     */
    public static boolean useExternalDb = false;
    
    /**
     * Inline storage value = ${nacos.standalone}.
     */
    public static boolean embeddedStorage = EnvUtil.getStandaloneMode();
    
    public static boolean isUseExternalDb() {
        return useExternalDb;
    }
    
    public static void setUseExternalDb(boolean useExternalDb) {
        DatasourceConfiguration.useExternalDb = useExternalDb;
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
        checkEmbeddedPlatformSupported(platform, EnvUtil.getStandaloneMode());
        checkEmbeddedDriverAvailable(platform, DatasourceConfiguration.class.getClassLoader());
        boolean useExternalStorage =
            !PersistenceConstant.EMPTY_DATASOURCE_PLATFORM.equalsIgnoreCase(platform)
                && !DatasourcePlatformUtil.isEmbeddedPlatform(platform);
        setUseExternalDb(useExternalStorage);
        
        // must initialize after setUseExternalDb
        // This value is true in stand-alone mode and false in cluster mode
        // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
        // default value is depend on ${nacos.standalone}
        
        if (isUseExternalDb()) {
            setEmbeddedStorage(false);
        } else {
            boolean embeddedStorage =
                isEmbeddedStorage() || Boolean.getBoolean(PersistenceConstant.EMBEDDED_STORAGE);
            setEmbeddedStorage(embeddedStorage);
            
            // If the embedded data source storage is not turned on, it is automatically
            // upgraded to the external data source storage, as before
            if (!embeddedStorage) {
                setUseExternalDb(true);
            }
        }
    }
    
    /**
     * H2 embedded storage is standalone-only: cluster embedded storage replicates through the CP state machine and
     * reopens the local database from Derby-specific snapshot code, which H2 does not implement.
     *
     * @param platform   selected datasource platform, blank when the default embedded platform is used
     * @param standalone whether the server runs in standalone mode
     * @throws IllegalStateException when {@code h2} is selected outside standalone mode
     */
    static void checkEmbeddedPlatformSupported(String platform, boolean standalone) {
        if (PersistenceConstant.H2.equalsIgnoreCase(platform) && !standalone) {
            throw new IllegalStateException("Datasource dialect '" + PersistenceConstant.H2
                + "' embedded storage supports standalone mode only. Set nacos.standalone=true, or"
                + " select mysql, postgresql, or oracle for cluster deployments. Check property "
                + PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY + ".");
        }
    }
    
    /**
     * The H2 engine loads {@code org.h2.Driver} by name. The default plugin bundle does not ship the driver, so a
     * server that selects {@code h2} without it fails here, and the message names the jars to add, instead of
     * failing later inside the connection pool.
     *
     * @param platform    selected datasource platform, blank when the default embedded platform is used
     * @param classLoader the class loader that must see the driver
     * @throws IllegalStateException when {@code h2} is selected and the driver is not on the class path
     */
    static void checkEmbeddedDriverAvailable(String platform, ClassLoader classLoader) {
        if (!PersistenceConstant.H2.equalsIgnoreCase(platform)) {
            return;
        }
        try {
            Class.forName("org.h2.Driver", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Datasource dialect '" + PersistenceConstant.H2
                + "' needs the H2 JDBC driver org.h2.Driver on the class path. Add the H2 driver"
                + " (com.h2database:h2) and nacos-datasource-plugin-h2 to the plugins directory of the server,"
                + " or build the Native Image with the native-h2 profile.", e);
        }
    }
    
    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        loadDatasourceConfiguration();
    }
}
