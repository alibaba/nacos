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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;

import java.sql.Timestamp;
import java.util.List;

/**
 * Dump change processor.
 *
 * @author Nacos
 * @date 2020/7/5 12:19 PM
 */
public class DumpChangeConfigWorker implements Runnable {
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    Timestamp startTime;
    
    public DumpChangeConfigWorker(DumpService dumpService, Timestamp startTime) {
        this.configInfoPersistService = dumpService.getConfigInfoPersistService();
        this.historyConfigInfoPersistService = dumpService.getHistoryConfigInfoPersistService();
        this.startTime = startTime;
    }
    
    /**
     * do check change.
     */
    public void run() {
        
        try {
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            LogUtil.DEFAULT_LOG.info("DumpChange start ,from time {},current time {}", startTime, currentTime);
            
            LogUtil.DEFAULT_LOG.info("Start to check delete configs from  time {}", startTime);
            
            int pageSize = 100;
            long startDeletedConfigTime = System.currentTimeMillis();
            LogUtil.DEFAULT_LOG.info("Check delete configs from  time {}", startTime);
            
            long deleteCursorId = 0L;
            
            while (true) {
                List<ConfigInfoWrapper> configDeleted = historyConfigInfoPersistService.findDeletedConfig(startTime,
                        deleteCursorId, pageSize);
                for (ConfigInfo configInfo : configDeleted) {
                    if (configInfoPersistService.findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant()) == null) {
                        ConfigCacheService.remove(configInfo.getDataId(), configInfo.getGroup(),
                                configInfo.getTenant());
                        LogUtil.DEFAULT_LOG.info("[dump-delete-ok] {}",
                                new Object[] {GroupKey2.getKey(configInfo.getDataId(), configInfo.getGroup())});
                    }
                }
                if (configDeleted.size() < pageSize) {
                    break;
                }
                deleteCursorId = configDeleted.get(configDeleted.size() - 1).getId();
                
            }
            LogUtil.DEFAULT_LOG.info("Check delete configs finished,cost:{}",
                    System.currentTimeMillis() - startDeletedConfigTime);
            
            LogUtil.DEFAULT_LOG.info("Check changeConfig start");
            long startChangeConfigTime = System.currentTimeMillis();
            
            long changeCursorId = 0L;
            while (true) {
                LogUtil.DEFAULT_LOG.info("Check changed configs from  time {},lastMaxId={}", startTime, changeCursorId);
                List<ConfigInfoWrapper> changeConfigs = configInfoPersistService.findChangeConfig(startTime,
                        changeCursorId, pageSize);
                for (ConfigInfoWrapper cf : changeConfigs) {
                    ConfigCacheService.dumpChange(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getContent(),
                            cf.getLastModified(), cf.getEncryptedDataKey());
                    final String content = cf.getContent();
                    final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE_UTF8);
                    
                    LogUtil.DEFAULT_LOG.info("[dump-change-check-ok] {}, {}, length={}, md5={}",
                            new Object[] {GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(),
                                    content.length(), md5});
                }
                if (changeConfigs.size() < pageSize) {
                    break;
                }
                changeCursorId = changeConfigs.get(changeConfigs.size() - 1).getId();
            }
            
            ConfigCacheService.reloadConfig();
            long endChangeConfigTime = System.currentTimeMillis();
            LogUtil.DEFAULT_LOG.info("Check changed configs finished,cost:{},set next start time to {}",
                    endChangeConfigTime - startChangeConfigTime, currentTime);
            startTime = currentTime;
        } catch (Throwable e) {
            LogUtil.DEFAULT_LOG.error("Check changed configs error", e);
        }
    }
}
