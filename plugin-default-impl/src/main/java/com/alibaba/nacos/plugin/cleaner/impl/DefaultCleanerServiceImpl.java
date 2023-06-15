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

package com.alibaba.nacos.plugin.cleaner.impl;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.plugin.cleaner.api.ExcuteSwitch;
import com.alibaba.nacos.plugin.cleaner.config.CleanerConfig;
import com.alibaba.nacos.plugin.cleaner.impl.config.DefaultCleanerConfig;
import com.alibaba.nacos.plugin.cleaner.impl.service.PersistService;
import com.alibaba.nacos.plugin.cleaner.impl.service.PersistServiceHandlerImpl;
import com.alibaba.nacos.plugin.cleaner.impl.utils.TimeUtils;
import com.alibaba.nacos.plugin.cleaner.spi.CleanerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * a default impl for cleanerService.
 *
 * @author vivid
 */

public class DefaultCleanerServiceImpl implements CleanerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCleanerServiceImpl.class);

    public static String name = "default";

    protected HistoryConfigInfoPersistService historyConfigInfoPersistService;

    private static int clearConfigHistoryPageSize = 1000;

    private static long clearConfigHistorySleepInMillis = 100L;

    PersistService persistService;

    CleanerConfig cleanerConfig;

    ExcuteSwitch excuteSwitch;

    private static final ScheduledExecutorService TIMER_EXECUTOR = ExecutorFactory.Managed
            .newScheduledExecutorService("config", 10,
                    new NameThreadFactory("com.alibaba.nacos.config.cleaner.timer"));

    @Override
    public String name() {
        return name;
    }

    public DefaultCleanerServiceImpl() {
        this.cleanerConfig = new DefaultCleanerConfig();
    }

    @Override
    public void startConfigHistoryTask(ExcuteSwitch excuteSwitch, JdbcTemplate jdbcTemplate) {
        this.excuteSwitch = excuteSwitch;
        persistService = new PersistServiceHandlerImpl(jdbcTemplate, cleanerConfig);
        TIMER_EXECUTOR.scheduleWithFixedDelay(this::run, 1, cleanerConfig.getFrequency(), TimeUnit.SECONDS);
    }

    private void run() {
        LOGGER.info("configHistory cleaner start");
        if (excuteSwitch.canExcute()) {
            try {
                Timestamp startTime = TimeUtils.getBeforeStamp(TimeUtils.getCurrentTime(), 24 * cleanerConfig.getRetentionDays());

                int totalCount = historyConfigInfoPersistService.findConfigHistoryCountByTime(startTime);

                if (totalCount > 0) {
                    int pageSize = clearConfigHistoryPageSize;
                    int removeTime = (totalCount + pageSize - 1) / pageSize;
                    LOGGER.warn(
                            "clearConfigHistory, getBeforeStamp:{}, totalCount:{}, pageSize:{}, removeTime:{}",
                            startTime, totalCount, pageSize, removeTime);
                    while (removeTime > 0) {
                        // delete paging to avoid reporting errors in batches
                        historyConfigInfoPersistService.removeConfigHistory(startTime, pageSize);
                        removeTime--;

                        try {
                            Thread.sleep(clearConfigHistorySleepInMillis);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.error("configHistory cleaner, error : {}", e.toString());
            }
        }
    }

}
