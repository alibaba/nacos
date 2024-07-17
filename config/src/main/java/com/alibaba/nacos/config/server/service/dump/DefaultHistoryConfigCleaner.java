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
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * The type Default history config cleaner.
 *
 * @author zhuoguang
 */
public class DefaultHistoryConfigCleaner implements HistoryConfigCleaner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHistoryConfigCleaner.class);
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    private ProtocolManager protocolManager;
    
    private ServerMemberManager memberManager;
    
    private int retentionDays = 30;
    
    @Override
    public void cleanHistoryConfig() {
        return;
    }
    
    private HistoryConfigInfoPersistService getHistoryConfigInfoPersistService() {
        if (historyConfigInfoPersistService == null) {
            historyConfigInfoPersistService = ApplicationUtils.getBean(HistoryConfigInfoPersistService.class);
        }
        return historyConfigInfoPersistService;
    }
    
    private ProtocolManager getProtocolManager() {
        if (protocolManager == null) {
            protocolManager = ApplicationUtils.getBean(ProtocolManager.class);
        }
        return protocolManager;
    }
    
    private ServerMemberManager getMemberManager() {
        if (memberManager == null) {
            memberManager = ApplicationUtils.getBean(ServerMemberManager.class);
        }
        return memberManager;
    }
    
    public void startCleanTask() {
        ConfigExecutor.scheduleConfigTask(this::cleanConfigHistory, 10, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Clean config history.
     */
    void cleanConfigHistory() {
        LOGGER.warn("clearConfigHistory start");
        if (canExecute()) {
            try {
                Timestamp startTime = getBeforeStamp(TimeUtils.getCurrentTime(), 24 * getRetentionDays());
                int pageSize = 1000;
                LOGGER.warn("clearConfigHistory, getBeforeStamp:{}, pageSize:{}", startTime, pageSize);
                getHistoryConfigInfoPersistService().removeConfigHistory(startTime, pageSize);
            } catch (Throwable e) {
                LOGGER.error("clearConfigHistory error : {}", e.toString());
            }
        }
    }
    
    private Timestamp getBeforeStamp(Timestamp date, int step) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR_OF_DAY, -step);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return Timestamp.valueOf(format.format(cal.getTime()));
    }
    
    private int getRetentionDays() {
        String val = EnvUtil.getProperty("nacos.config.retention.days");
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
            FATAL_LOG.error("read nacos.config.retention.days wrong", nfe);
        }
        
        return retentionDays;
    }
    
    private boolean canExecute() {
        if (DatasourceConfiguration.isEmbeddedStorage()) {
            if (EnvUtil.getStandaloneMode()) {
                return true;
            }
            // if is derby + raft mode, only leader can execute
            CPProtocol protocol = getProtocolManager().getCpProtocol();
            return protocol.isLeader(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP);
        } else {
            return getMemberManager().isFirstIp();
        }
        
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
}
