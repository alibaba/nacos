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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    
    public DumpChangeConfigWorker(ConfigInfoPersistService configInfoPersistService,
            HistoryConfigInfoPersistService historyConfigInfoPersistService, Timestamp startTime) {
        this.configInfoPersistService = configInfoPersistService;
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        this.startTime = startTime;
    }
    
    int pageSize = 100;
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    /**
     * do check change.
     */
    public void run() {
        
        try {
            
            if (!PropertyUtil.isDumpChangeOn()) {
                LogUtil.DEFAULT_LOG.info("DumpChange task is not open");
                return;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            LogUtil.DEFAULT_LOG.info("DumpChange start ,from time {},current time {}", startTime, currentTime);
            
            LogUtil.DEFAULT_LOG.info("Start to check delete configs from  time {}", startTime);
            
            long startDeletedConfigTime = System.currentTimeMillis();
            LogUtil.DEFAULT_LOG.info("Check delete configs from  time {}", startTime);
            
            long deleteCursorId = 0L;
            
            while (true) {
                List<ConfigInfoStateWrapper> configDeleted = historyConfigInfoPersistService.findDeletedConfig(startTime,
                        deleteCursorId, pageSize, Constants.FORMAL);
                for (ConfigInfoStateWrapper configInfo : configDeleted) {
                    if (configInfoPersistService.findConfigInfoState(configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant()) == null) {
                        ConfigCacheService.remove(configInfo.getDataId(), configInfo.getGroup(),
                                configInfo.getTenant());
                        LogUtil.DEFAULT_LOG.info("[dump-delete-ok], groupKey: {}, tenant: {}",
                                new Object[] {GroupKey2.getKey(configInfo.getDataId(), configInfo.getGroup())}, configInfo.getTenant());
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
                List<ConfigInfoStateWrapper> changeConfigs = configInfoPersistService.findChangeConfig(startTime,
                        changeCursorId, pageSize);
                for (ConfigInfoStateWrapper cf : changeConfigs) {
                    final String groupKey = GroupKey2.getKey(cf.getDataId(), cf.getGroup(), cf.getTenant());
                    //check md5 & localtimestamp update local disk cache.
                    boolean newLastModified = cf.getLastModified() > ConfigCacheService.getLastModifiedTs(groupKey);
                    String localContentMd5 = ConfigCacheService.getContentMd5(groupKey);
                    boolean md5Update = !localContentMd5.equals(cf.getMd5());
                    if (newLastModified || md5Update) {
                        LogUtil.DEFAULT_LOG.info("[dump-change] find change config  {}, {}, md5={}",
                                new Object[] {groupKey, cf.getLastModified(), cf.getMd5()});
                        ConfigInfoWrapper configInfoWrapper = configInfoPersistService.findConfigInfo(cf.getDataId(),
                                cf.getGroup(), cf.getTenant());
                        LogUtil.DUMP_LOG.info("[dump-change] find change config  {}, {}, md5={}",
                                new Object[] {groupKey, cf.getLastModified(), cf.getMd5()});
                        ConfigCacheService.dump(configInfoWrapper.getDataId(), configInfoWrapper.getGroup(),
                                configInfoWrapper.getTenant(), configInfoWrapper.getContent(),
                                configInfoWrapper.getLastModified(), configInfoWrapper.getType(),
                                configInfoWrapper.getEncryptedDataKey());
                        final String content = configInfoWrapper.getContent();
                        final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE_GBK);
                        final String md5Utf8 = MD5Utils.md5Hex(content, Constants.ENCODE_UTF8);
                        
                        LogUtil.DEFAULT_LOG.info("[dump-change-ok] {}, {}, length={}, md5={},md5UTF8={}",
                                new Object[] {groupKey, configInfoWrapper.getLastModified(), content.length(), md5,
                                        md5Utf8});
                    }
                }
                if (changeConfigs.size() < pageSize) {
                    break;
                }
                changeCursorId = changeConfigs.get(changeConfigs.size() - 1).getId();
            }
            
            long endChangeConfigTime = System.currentTimeMillis();
            LogUtil.DEFAULT_LOG.info(
                    "Check changed configs finished,cost:{}, next task running will from start time  {}",
                    endChangeConfigTime - startChangeConfigTime, currentTime);
            startTime = currentTime;
        } catch (Throwable e) {
            LogUtil.DEFAULT_LOG.error("Check changed configs error", e);
        } finally {
            ConfigExecutor.scheduleConfigChangeTask(this, PropertyUtil.getDumpChangeWorkerInterval(),
                    TimeUnit.MILLISECONDS);
            LogUtil.DEFAULT_LOG.info("Next dump change will scheduled after {} milliseconds",
                    PropertyUtil.getDumpChangeWorkerInterval());
            
        }
    }
}
