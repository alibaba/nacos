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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dump gray config worker.
 *
 * @author shiyiyue
 */
public class DumpChangeGrayConfigWorker implements Runnable {
    
    Timestamp startTime;
    
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private final HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    int pageSize = 100;
    
    public DumpChangeGrayConfigWorker(ConfigInfoGrayPersistService configInfoGrayPersistService, Timestamp startTime,
            HistoryConfigInfoPersistService historyConfigInfoPersistService) {
        this.configInfoGrayPersistService = configInfoGrayPersistService;
        this.startTime = startTime;
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
    }
    
    @Override
    public void run() {
        try {
            if (!PropertyUtil.isDumpChangeOn()) {
                LogUtil.DEFAULT_LOG.info("DumpGrayChange task is not open");
                return;
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            LogUtil.DEFAULT_LOG.info("DumpGrayChange start ,from time {},current time {}", startTime, currentTime);
            
            LogUtil.DEFAULT_LOG.info("Start to check delete configs from  time {}", startTime);
            long startDeletedConfigTime = System.currentTimeMillis();
            long deleteCursorId = 0L;
            while (true) {
                List<ConfigHistoryInfo> historyConfigDeleted = historyConfigInfoPersistService.findDeletedConfig(startTime,
                        deleteCursorId, pageSize, "gray");
                for (ConfigHistoryInfo historyInfo : historyConfigDeleted) {
                    ConfigInfoStateWrapper configInfoStateWrapper = configInfoGrayPersistService.findConfigInfo4GrayState(historyInfo.getDataId(),
                            historyInfo.getGroup(), historyInfo.getTenant(), extractGrayName(historyInfo.getExtraInfo()));
                    if (configInfoStateWrapper == null) {
                        ConfigCacheService.remove(historyInfo.getDataId(), historyInfo.getGroup(),
                                historyInfo.getTenant());
                        LogUtil.DEFAULT_LOG.info("[dump-delete-ok] {}",
                                GroupKey2.getKey(historyInfo.getDataId(), historyInfo.getGroup()));
                    }
                }
                if (historyConfigDeleted.size() < pageSize) {
                    break;
                }
                deleteCursorId = historyConfigDeleted.get(historyConfigDeleted.size() - 1).getId();
            }
            LogUtil.DEFAULT_LOG.info("Check delete configs finished,cost:{}",
                    System.currentTimeMillis() - startDeletedConfigTime);
            
            LogUtil.DEFAULT_LOG.info("Check changeGrayConfig start");
            long startChangeConfigTime = System.currentTimeMillis();
            
            long changeCursorId = 0L;
            while (true) {
                LogUtil.DEFAULT_LOG.info("Check changed gray configs from  time {},lastMaxId={}", startTime,
                        changeCursorId);
                List<ConfigInfoGrayWrapper> changeConfigs = configInfoGrayPersistService.findChangeConfig(startTime,
                        changeCursorId, pageSize);
                for (ConfigInfoGrayWrapper cf : changeConfigs) {
                    final String groupKey = GroupKey2.getKey(cf.getDataId(), cf.getGroup(), cf.getTenant());
                    //check md5 & localtimestamp update local disk cache.
                    boolean newLastModified = cf.getLastModified() > ConfigCacheService.getLastModifiedTs(groupKey);
                    String localContentMd5 = ConfigCacheService.getContentMd5(groupKey);
                    boolean md5Update = !localContentMd5.equals(cf.getMd5());
                    if (newLastModified || md5Update) {
                        LogUtil.DEFAULT_LOG.info("[dump-change-gray] find change config  {}, {}, md5={}",
                                new Object[] {groupKey, cf.getLastModified(), cf.getMd5()});
                        
                        LogUtil.DUMP_LOG.info("[dump-change-gray] find change config  {}, {}, md5={}",
                                new Object[] {groupKey, cf.getLastModified(), cf.getMd5()});
                        ConfigCacheService.dumpGray(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getGrayName(),
                                cf.getGrayRule(), cf.getContent(), cf.getLastModified(), cf.getEncryptedDataKey());
                        final String content = cf.getContent();
                        final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE_GBK);
                        final String md5Utf8 = MD5Utils.md5Hex(content, Constants.ENCODE_UTF8);
                        
                        LogUtil.DEFAULT_LOG.info("[dump-change-gray-ok] {}, {}, length={}, md5={},md5UTF8={}",
                                new Object[] {groupKey, cf.getLastModified(), content.length(), md5, md5Utf8});
                    }
                }
                if (changeConfigs.size() < pageSize) {
                    break;
                }
                changeCursorId = changeConfigs.get(changeConfigs.size() - 1).getId();
            }
            
            long endChangeConfigTime = System.currentTimeMillis();
            LogUtil.DEFAULT_LOG.info(
                    "Check changed gray configs finished,cost:{}, next task running will from start time  {}",
                    endChangeConfigTime - startChangeConfigTime, currentTime);
            startTime = currentTime;
        } catch (Throwable e) {
            LogUtil.DEFAULT_LOG.error("Check changed gray configs error", e);
        } finally {
            ConfigExecutor.scheduleConfigChangeTask(this, PropertyUtil.getDumpChangeWorkerInterval(),
                    TimeUnit.MILLISECONDS);
            LogUtil.DEFAULT_LOG.info("Next dump gray change will scheduled after {} milliseconds",
                    PropertyUtil.getDumpChangeWorkerInterval());
            
        }
    }
    
    private String extractGrayName(String extraInfo)  {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> dataMap = objectMapper.readValue(extraInfo, new TypeReference<Map<String, String>>() { });
            return dataMap.get("gray_name");
        } catch (Exception e) {
            LogUtil.DEFAULT_LOG.error("[dump-change-gray-error] Error extracting gray_name from extraInfo", e);
            return null;
        }
    }
}
