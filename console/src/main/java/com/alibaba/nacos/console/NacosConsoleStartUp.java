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

package com.alibaba.nacos.console;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.listener.startup.AbstractNacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos Server Web API start up phase.
 *
 * @author xiweng.yy
 */
public class NacosConsoleStartUp extends AbstractNacosStartUp {
    
    private static final String MODE_PROPERTY_KEY_STAND_MODE = "nacos.mode";
    
    private static final String MODE_PROPERTY_KEY_FUNCTION_MODE = "nacos.function.mode";
    
    private static final String NACOS_MODE_STAND_ALONE = "stand alone";
    
    private static final String DEFAULT_FUNCTION_MODE = "All";
    
    private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";
    
    private static final String NACOS_APPLICATION_CONF = "nacos_application_conf";
    
    private static final Map<String, Object> SOURCES = new ConcurrentHashMap<>();
    
    private boolean isConsoleDeploymentType;
    
    public NacosConsoleStartUp() {
        super(NacosStartUp.CONSOLE_START_UP_PHASE);
    }
    
    @Override
    protected String getPhaseNameInStartingInfo() {
        return "Nacos Console";
    }
    
    @Override
    public String[] makeWorkDir() {
        isConsoleDeploymentType = Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE.equals(
                System.getProperty(Constants.NACOS_DEPLOYMENT_TYPE));
        if (isConsoleDeploymentType) {
            try {
                Path path = Paths.get(EnvUtil.getNacosHome(), "logs");
                DiskUtils.forceMkdir(new File(path.toUri()));
            } catch (Exception e) {
                throw new NacosRuntimeException(ErrorCode.IOMakeDirError.getCode(), e);
            }
            return new String[] {EnvUtil.getNacosHome() + File.separator + "logs"};
        }
        return super.makeWorkDir();
    }
    
    @Override
    public void injectEnvironment(ConfigurableEnvironment environment) {
        if (isConsoleDeploymentType) {
            EnvUtil.setEnvironment(environment);
        }
    }
    
    @Override
    public void loadPreProperties(ConfigurableEnvironment environment) {
        if (isConsoleDeploymentType) {
            try {
                SOURCES.putAll(EnvUtil.loadProperties(EnvUtil.getApplicationConfFileResource()));
                environment.getPropertySources()
                        .addLast(new OriginTrackedMapPropertySource(NACOS_APPLICATION_CONF, SOURCES));
            } catch (Exception e) {
                throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
            }
        }
    }
    
    @Override
    public void initSystemProperty() {
        if (isConsoleDeploymentType) {
            System.setProperty(LOCAL_IP_PROPERTY_KEY, InetUtils.getSelfIP());
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, NACOS_MODE_STAND_ALONE);
            if (EnvUtil.getFunctionMode() == null) {
                System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, DEFAULT_FUNCTION_MODE);
            } else if (EnvUtil.FUNCTION_MODE_CONFIG.equals(EnvUtil.getFunctionMode())) {
                System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_CONFIG);
            } else if (EnvUtil.FUNCTION_MODE_NAMING.equals(EnvUtil.getFunctionMode())) {
                System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, EnvUtil.FUNCTION_MODE_NAMING);
            }
        }
    }
    
    @Override
    public void logStarted(Logger logger) {
        long endTimestamp = System.currentTimeMillis();
        long startupCost = endTimestamp - getStartTimestamp();
        logger.info("Nacos Console started successfully in {} ms", startupCost);
    }
}
