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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
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
public class DumpChangeProcessor implements NacosTaskProcessor {

    final DumpService dumpService;

    final PersistService persistService;

    final Timestamp startTime;

    final Timestamp endTime;
    
    public DumpChangeProcessor(DumpService dumpService, Timestamp startTime, Timestamp endTime) {
        this.dumpService = dumpService;
        this.persistService = dumpService.getPersistService();
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    @Override
    public boolean process(NacosTask task) {
        LogUtil.DEFAULT_LOG.warn("quick start; startTime:{},endTime:{}", startTime, endTime);
        LogUtil.DEFAULT_LOG.warn("updateMd5 start");
        long startUpdateMd5 = System.currentTimeMillis();
        List<ConfigInfoWrapper> updateMd5List = persistService.listAllGroupKeyMd5();
        LogUtil.DEFAULT_LOG.warn("updateMd5 count:{}", updateMd5List.size());
        for (ConfigInfoWrapper config : updateMd5List) {
            final String groupKey = GroupKey2.getKey(config.getDataId(), config.getGroup());
            ConfigCacheService.updateMd5(groupKey, config.getMd5(), config.getLastModified());
        }
        long endUpdateMd5 = System.currentTimeMillis();
        LogUtil.DEFAULT_LOG.warn("updateMd5 done,cost:{}", endUpdateMd5 - startUpdateMd5);
        
        LogUtil.DEFAULT_LOG.warn("deletedConfig start");
        long startDeletedConfigTime = System.currentTimeMillis();
        List<ConfigInfo> configDeleted = persistService.findDeletedConfig(startTime, endTime);
        LogUtil.DEFAULT_LOG.warn("deletedConfig count:{}", configDeleted.size());
        for (ConfigInfo configInfo : configDeleted) {
            if (persistService.findConfigInfo(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant())
                    == null) {
                ConfigCacheService.remove(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
            }
        }
        long endDeletedConfigTime = System.currentTimeMillis();
        LogUtil.DEFAULT_LOG.warn("deletedConfig done,cost:{}", endDeletedConfigTime - startDeletedConfigTime);
        
        LogUtil.DEFAULT_LOG.warn("changeConfig start");
        final long startChangeConfigTime = System.currentTimeMillis();
        List<ConfigInfoWrapper> changeConfigs = persistService.findChangeConfig(startTime, endTime);
        LogUtil.DEFAULT_LOG.warn("changeConfig count:{}", changeConfigs.size());
        for (ConfigInfoWrapper cf : changeConfigs) {
            boolean result = ConfigCacheService
                    .dumpChange(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getContent(), cf.getLastModified());
            final String content = cf.getContent();
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            LogUtil.DEFAULT_LOG.info("[dump-change-ok] {}, {}, length={}, md5={}", GroupKey2.getKey(cf.getDataId(), cf.getGroup()),
                    cf.getLastModified(), content.length(), md5);
        }
        ConfigCacheService.reloadConfig();
        long endChangeConfigTime = System.currentTimeMillis();
        LogUtil.DEFAULT_LOG.warn("changeConfig done,cost:{}", endChangeConfigTime - startChangeConfigTime);
        return true;
    }
}
