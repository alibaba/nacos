/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * The type History config cleaner config.
 * @author Sunrisea
 */
public class HistoryConfigCleanerConfig extends AbstractDynamicConfig {
    
    private static final String HISTORY_CONFIG_CLEANER = "historyConfigCleaner";
    
    private static final HistoryConfigCleanerConfig INSTANCE = new HistoryConfigCleanerConfig();
    
    private String activeHistoryConfigCleaner = "nacos";
    
    private HistoryConfigCleanerConfig() {
        super(HISTORY_CONFIG_CLEANER);
        resetConfig();
    }
    
    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static HistoryConfigCleanerConfig getInstance() {
        return INSTANCE;
    }
    
    @Override
    protected void getConfigFromEnv() {
        activeHistoryConfigCleaner = EnvUtil.getProperty("nacos.config.history.clear.name", String.class, "nacos");
        if (StringUtils.isBlank(activeHistoryConfigCleaner)) {
            activeHistoryConfigCleaner = "nacos";
        }
    }
    
    /**
     * Gets active history config cleaner.
     *
     * @return the active history config cleaner
     */
    public String getActiveHistoryConfigCleaner() {
        return activeHistoryConfigCleaner;
    }
    
    /**
     * Sets active history config cleaner.
     *
     * @param activeHistoryConfigCleaner the active history config cleaner
     */
    public void setActiveHistoryConfigCleaner(String activeHistoryConfigCleaner) {
        this.activeHistoryConfigCleaner = activeHistoryConfigCleaner;
    }
    
    @Override
    protected String printConfig() {
        return "activeHistoryConfigCleaner{ " + "activeHistoryConfigCleaner=" + activeHistoryConfigCleaner + "}";
    }
}
