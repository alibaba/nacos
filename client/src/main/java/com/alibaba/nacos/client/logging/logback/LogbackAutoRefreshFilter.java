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

package com.alibaba.nacos.client.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.config.filter.IConfigRequest;
import com.alibaba.nacos.api.config.filter.IConfigResponse;
import com.alibaba.nacos.api.config.filter.IFilterConfig;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.common.ConfigConstants;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.common.utils.ResourceUtils;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.File;
import java.util.Properties;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

/**
 * refresh logback.
 *
 * @author hujun
 */
public class LogbackAutoRefreshFilter implements IConfigFilter {
    
    public static final String LOGBACK_DATA_ID = "logback.xml";
    
    private static volatile boolean isLoad;
    
    @Override
    public void doFilter(IConfigRequest request, IConfigResponse response, IConfigFilterChain filterChain)
            throws NacosException {
        String dataId = response.getParameter(ConfigConstants.DATA_ID).toString();
        if (!isLoad && LOGBACK_DATA_ID.equals(dataId)) {
            loadUserConfiguration();
            isLoad = true;
        }
    }
    
    /**
     * reload user logback config.
     */
    public static void loadUserConfiguration() {
        String location = LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH + File.separator + Constants.AGENT_NAME
                + LocalConfigInfoProcessor.SUFFIX + File.separator + LocalConfigInfoProcessor.ENV_CHILD + File.separator
                + DEFAULT_GROUP + File.separator + LogbackAutoRefreshFilter.LOGBACK_DATA_ID;
        
        try {
            LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
            new ContextInitializer(loggerContext).configureByResource(ResourceUtils.getResourceUrl(location));
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize user Nacos logging from " + location, e);
        }
    }
    
    @Override
    public void init(IFilterConfig filterConfig) {
    
    }
    
    @Override
    public void init(Properties properties) {
    
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
    
    @Override
    public String getFilterName() {
        return "LogbackAutoRefreshFilter";
    }
}
