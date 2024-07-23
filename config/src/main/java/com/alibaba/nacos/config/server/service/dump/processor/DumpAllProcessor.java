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

package com.alibaba.nacos.config.server.service.dump.processor;

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.model.Page;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * Dump all processor.
 *
 * @author Nacos
 * @date 2020/7/5 12:19 PM
 */
public class DumpAllProcessor implements NacosTaskProcessor {
    
    public DumpAllProcessor(ConfigInfoPersistService configInfoPersistService) {
        this.configInfoPersistService = configInfoPersistService;
    }
    
    @Override
    public boolean process(NacosTask task) {
        if (!(task instanceof DumpAllTask)) {
            DEFAULT_LOG.error("[all-dump-error] ,invalid task type,DumpAllProcessor should process DumpAllTask type.");
            return false;
        }
        DumpAllTask dumpAllTask = (DumpAllTask) task;
        
        long currentMaxId = configInfoPersistService.findConfigMaxId();
        long lastMaxId = 0;
        ThreadPoolExecutor executorService = null;
        if (dumpAllTask.isStartUp()) {
            executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().availableProcessors(), 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(PropertyUtil.getAllDumpPageSize() * 2),
                    r -> new Thread(r, "dump all executor"), new ThreadPoolExecutor.CallerRunsPolicy());
        } else {
            executorService = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                    r -> new Thread(r, "dump all executor"), new ThreadPoolExecutor.CallerRunsPolicy());
        }
        
        DEFAULT_LOG.info("start dump all config-info...");
        
        while (lastMaxId < currentMaxId) {
            
            long start = System.currentTimeMillis();
            
            Page<ConfigInfoWrapper> page = configInfoPersistService.findAllConfigInfoFragment(lastMaxId,
                    PropertyUtil.getAllDumpPageSize(), dumpAllTask.isStartUp());
            long dbTimeStamp = System.currentTimeMillis();
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                break;
            }
            
            for (ConfigInfoWrapper cf : page.getPageItems()) {
                lastMaxId = Math.max(cf.getId(), lastMaxId);
                //if not start up, page query will not return content, check md5 and lastModified first ,if changed ,get single content info to dump.
                if (!dumpAllTask.isStartUp()) {
                    final String groupKey = GroupKey2.getKey(cf.getDataId(), cf.getGroup(), cf.getTenant());
                    boolean newLastModified = cf.getLastModified() > ConfigCacheService.getLastModifiedTs(groupKey);
                    //check md5 & update local disk cache.
                    String localContentMd5 = ConfigCacheService.getContentMd5(groupKey);
                    boolean md5Update = !localContentMd5.equals(cf.getMd5());
                    if (newLastModified || md5Update) {
                        LogUtil.DUMP_LOG.info("[dump-all] find change config {}, {}, md5={}", groupKey,
                                cf.getLastModified(), cf.getMd5());
                        cf = configInfoPersistService.findConfigInfo(cf.getDataId(), cf.getGroup(), cf.getTenant());
                    } else {
                        continue;
                    }
                }
                
                if (cf == null) {
                    continue;
                }
                if (cf.getDataId().equals(AggrWhitelist.AGGRIDS_METADATA)) {
                    AggrWhitelist.load(cf.getContent());
                }
                
                if (cf.getDataId().equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
                    ClientIpWhiteList.load(cf.getContent());
                }
                
                if (cf.getDataId().equals(SwitchService.SWITCH_META_DATA_ID)) {
                    SwitchService.load(cf.getContent());
                }
                
                final String content = cf.getContent();
                final String dataId = cf.getDataId();
                final String group = cf.getGroup();
                final String tenant = cf.getTenant();
                final long lastModified = cf.getLastModified();
                final String type = cf.getType();
                final String encryptedDataKey = cf.getEncryptedDataKey();
                
                executorService.execute(() -> {
                    final String md5Utf8 = MD5Utils.md5Hex(content, ENCODE_UTF8);
                    boolean result = ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5Utf8,
                            lastModified, type, encryptedDataKey);
                    if (result) {
                        LogUtil.DUMP_LOG.info("[dump-all-ok] {}, {}, length={},md5UTF8={}",
                                GroupKey2.getKey(dataId, group), lastModified, content.length(), md5Utf8);
                    } else {
                        LogUtil.DUMP_LOG.info("[dump-all-error] {}", GroupKey2.getKey(dataId, group));
                    }
                    
                });
                
            }
            
            long diskStamp = System.currentTimeMillis();
            DEFAULT_LOG.info("[all-dump] submit all task for {} / {}, dbTime={},diskTime={}", lastMaxId, currentMaxId,
                    (dbTimeStamp - start), (diskStamp - dbTimeStamp));
        }
        
        //wait all task are finished and then shutdown executor.
        try {
            int unfinishedTaskCount = 0;
            while ((unfinishedTaskCount = executorService.getQueue().size() + executorService.getActiveCount()) > 0) {
                DEFAULT_LOG.info("[all-dump] wait {} dump tasks to be finished", unfinishedTaskCount);
                Thread.sleep(1000L);
            }
            executorService.shutdown();
            
        } catch (Exception e) {
            DEFAULT_LOG.error("[all-dump] wait  dump tasks to be finished error", e);
        }
        DEFAULT_LOG.info("success to  dump all config-info。");
        return true;
    }
    
    final ConfigInfoPersistService configInfoPersistService;
}
