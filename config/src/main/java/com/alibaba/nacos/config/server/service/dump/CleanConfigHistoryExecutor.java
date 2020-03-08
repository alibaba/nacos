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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.TimerTaskService;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class CleanConfigHistoryExecutor {

    private static final Logger log = LoggerFactory.getLogger(CleanConfigHistoryExecutor.class);

    private PersistService persistService;
    private MemberManager memberManager;
    private int retentionDays = 30;

    public CleanConfigHistoryExecutor(DumpService dumpService, MemberManager memberManager) {
        this.persistService = dumpService.getPersistService();
        this.memberManager = memberManager;
    }

    public void start() {
        TimerTaskService.scheduleWithFixedDelay(this::execute, 10, 10, TimeUnit.MINUTES);
    }

    private void execute() {
        log.warn("clearConfigHistory start");
        if (memberManager.isFirstIp()) {
            try {
                Timestamp startTime = getBeforeStamp(TimeUtils.getCurrentTime(), 24 * getRetentionDays());
                int totalCount = persistService.findConfigHistoryCountByTime(startTime);
                if (totalCount > 0) {
                    int pageSize = 1000;
                    int removeTime = (totalCount + pageSize - 1) / pageSize;
                    log.warn("clearConfigHistory, getBeforeStamp:{}, totalCount:{}, pageSize:{}, removeTime:{}",
                            startTime, totalCount, pageSize, removeTime);
                    while (removeTime > 0) {
                        // 分页删除，以免批量太大报错
                        persistService.removeConfigHistory(startTime, pageSize);
                        removeTime--;
                    }
                }
            } catch (Throwable e) {
                log.error("clearConfigHistory error", e);
            }
        }
    }

    private Timestamp getBeforeStamp(Timestamp date, int step) {
        Calendar cal = Calendar.getInstance();
        /**
         *  date 换成已经已知的Date对象
         */
        cal.setTime(date);
        /**
         *  before 6 hour
         */
        cal.add(Calendar.HOUR_OF_DAY, -step);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return Timestamp.valueOf(format.format(cal.getTime()));
    }

    private int getRetentionDays() {
        String val = SpringUtils.getProperty("nacos.config.retention.days");
        if (null == val) {
            return retentionDays;
        }

        int tmp = 0;
        try {
            tmp = Integer.parseInt(val);
            if (tmp > 0) {
                retentionDays = tmp;
            }
        } catch (NumberFormatException nfe) {
            fatalLog.error("read nacos.config.retention.days wrong", nfe);
        }

        return retentionDays;
    }

}
