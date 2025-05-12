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

package com.alibaba.nacos.core.listener.startup;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.executor.ThreadPoolManager;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos Server Core start up phase.
 *
 * @author xiweng.yy
 */
public class NacosCoreStartUp extends AbstractNacosStartUp {
    
    private static final String MODE_PROPERTY_KEY_STAND_MODE = "nacos.mode";
    
    private static final String MODE_PROPERTY_KEY_FUNCTION_MODE = "nacos.function.mode";
    
    private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";
    
    private static final String NACOS_APPLICATION_CONF = "nacos_application_conf";
    
    private static final String NACOS_MODE_STAND_ALONE = "stand alone";
    
    private static final String NACOS_MODE_CLUSTER = "cluster";
    
    private static final String DEFAULT_FUNCTION_MODE = "All";
    
    private static final String DATASOURCE_PLATFORM_PROPERTY = "spring.sql.init.platform";
    
    private static final String DERBY_DATABASE = "derby";
    
    private static final String DEFAULT_DATASOURCE_PLATFORM = "";
    
    private static final String DATASOURCE_MODE_EXTERNAL = "external";
    
    private static final String DATASOURCE_MODE_EMBEDDED = "embedded";
    
    private static final Map<String, Object> SOURCES = new ConcurrentHashMap<>();
    
    public NacosCoreStartUp() {
        super(NacosStartUp.CORE_START_UP_PHASE);
    }
    
    @Override
    public String[] makeWorkDir() {
        String[] dirNames = new String[] {"logs", "conf", "data"};
        List<String> result = new ArrayList<>(dirNames.length);
        for (String dirName : dirNames) {
            try {
                Path path = Paths.get(EnvUtil.getNacosHome(), dirName);
                DiskUtils.forceMkdir(new File(path.toUri()));
                result.add(path.toString());
            } catch (Exception e) {
                throw new NacosRuntimeException(ErrorCode.IOMakeDirError.getCode(), e);
            }
        }
        return result.toArray(new String[0]);
    }
    
    @Override
    public void injectEnvironment(ConfigurableEnvironment environment) {
        EnvUtil.setEnvironment(environment);
    }
    
    @Override
    public void loadPreProperties(ConfigurableEnvironment environment) {
        try {
            SOURCES.putAll(EnvUtil.loadProperties(EnvUtil.getApplicationConfFileResource()));
            environment.getPropertySources()
                    .addLast(new OriginTrackedMapPropertySource(NACOS_APPLICATION_CONF, SOURCES));
            registerWatcher();
        } catch (Exception e) {
            throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
        }
    }
    
    @Override
    public void initSystemProperty() {
        if (EnvUtil.getStandaloneMode()) {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, NACOS_MODE_STAND_ALONE);
        } else {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, NACOS_MODE_CLUSTER);
        }
        if (EnvUtil.getFunctionMode() == null) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, DEFAULT_FUNCTION_MODE);
        } else if (EnvUtil.FUNCTION_MODE_CONFIG.equals(EnvUtil.getFunctionMode())) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_CONFIG);
        } else if (EnvUtil.FUNCTION_MODE_NAMING.equals(EnvUtil.getFunctionMode())) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_NAMING);
        }
        
        System.setProperty(LOCAL_IP_PROPERTY_KEY, InetUtils.getSelfIP());
    }
    
    @Override
    public void logStartingInfo(Logger logger) {
        logClusterConf(logger);
        super.logStartingInfo(logger);
    }
    
    @Override
    public void customEnvironment() {
        EnvUtil.customEnvironment();
    }
    
    @Override
    public void started() {
        super.started();
        ApplicationUtils.setStarted(true);
    }
    
    @Override
    protected String getPhaseNameInStartingInfo() {
        return "Nacos Server";
    }
    
    @Override
    public void logStarted(Logger logger) {
        long endTimestamp = System.currentTimeMillis();
        long startupCost = endTimestamp - getStartTimestamp();
        boolean useExternalStorage = judgeStorageMode(EnvUtil.getEnvironment());
        logger.info("Nacos started successfully in {} mode with {} storage in {} ms",
                System.getProperty(MODE_PROPERTY_KEY_STAND_MODE),
                useExternalStorage ? DATASOURCE_MODE_EXTERNAL : DATASOURCE_MODE_EMBEDDED, startupCost);
    }
    
    @Override
    public void failed(Throwable exception, ConfigurableApplicationContext context) {
        super.failed(exception, context);
        ThreadPoolManager.shutdown();
        WatchFileCenter.shutdown();
        NotifyCenter.shutdown();
    }
    
    private void registerWatcher() throws NacosException {
        WatchFileCenter.registerWatcher(EnvUtil.getConfPath(), new FileWatcher() {
            @Override
            public void onChange(FileChangeEvent event) {
                try {
                    Map<String, ?> tmp = EnvUtil.loadProperties(EnvUtil.getApplicationConfFileResource());
                    SOURCES.putAll(tmp);
                    NotifyCenter.publishEvent(ServerConfigChangeEvent.newEvent());
                } catch (IOException ignore) {
                }
            }
            
            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, "application.properties");
            }
        });
    }
    
    private void logClusterConf(Logger logger) {
        if (!EnvUtil.getStandaloneMode()) {
            try {
                List<String> clusterConf = EnvUtil.readClusterConf();
                logger.info("The server IP list of Nacos is {}", clusterConf);
            } catch (IOException e) {
                logger.error("read cluster conf fail", e);
            }
        }
    }
    
    private boolean judgeStorageMode(ConfigurableEnvironment env) {
        
        // External data sources are used by default in cluster mode
        String platform = this.getDatasourcePlatform(env);
        boolean useExternalStorage =
                !DEFAULT_DATASOURCE_PLATFORM.equalsIgnoreCase(platform) && !DERBY_DATABASE.equalsIgnoreCase(platform);
        
        // must initialize after setUseExternalDB
        // This value is true in stand-alone mode and false in cluster mode
        // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
        // default value is depend on ${nacos.standalone}
        
        if (!useExternalStorage) {
            boolean embeddedStorage = EnvUtil.getStandaloneMode() || Boolean.getBoolean("embeddedStorage");
            // If the embedded data source storage is not turned on, it is automatically
            // upgraded to the external data source storage, as before
            if (!embeddedStorage) {
                useExternalStorage = true;
            }
        }
        return useExternalStorage;
    }
    
    private String getDatasourcePlatform(ConfigurableEnvironment env) {
        return env.getProperty(DATASOURCE_PLATFORM_PROPERTY, DEFAULT_DATASOURCE_PLATFORM);
    }
}
