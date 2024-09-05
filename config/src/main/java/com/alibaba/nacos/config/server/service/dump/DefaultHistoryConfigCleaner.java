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

import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * The type Default history config cleaner.
 *
 * @author Sunrisea
 */
public class DefaultHistoryConfigCleaner implements HistoryConfigCleaner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHistoryConfigCleaner.class);
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Override
    public void cleanHistoryConfig() {
        Timestamp startTime = getBeforeStamp(TimeUtils.getCurrentTime(), 24 * getRetentionDays());
        int pageSize = 1000;
        LOGGER.warn("clearConfigHistory, getBeforeStamp:{}, pageSize:{}", startTime, pageSize);
        getHistoryConfigInfoPersistService().removeConfigHistory(startTime, pageSize);
    }
    
    private HistoryConfigInfoPersistService getHistoryConfigInfoPersistService() {
        if (historyConfigInfoPersistService == null) {
            historyConfigInfoPersistService = ApplicationUtils.getBean(HistoryConfigInfoPersistService.class);
        }
        return historyConfigInfoPersistService;
    }
    
    private Timestamp getBeforeStamp(Timestamp date, int step) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR_OF_DAY, -step);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return Timestamp.valueOf(format.format(cal.getTime()));
    }
    
    private int getRetentionDays() {
        return PropertyUtil.getConfigRententionDays();
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
}
