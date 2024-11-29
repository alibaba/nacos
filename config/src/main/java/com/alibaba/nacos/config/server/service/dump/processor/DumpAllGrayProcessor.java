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
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.model.Page;

import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;
import static com.alibaba.nacos.config.server.utils.PropertyUtil.getAllDumpPageSize;

/**
 * Dump all gray processor.
 *
 * @author Nacos
 * @datete 2024/02/20
 */
public class DumpAllGrayProcessor implements NacosTaskProcessor {
    
    public DumpAllGrayProcessor(ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.configInfoGrayPersistService = configInfoGrayPersistService;
    }
    
    @Override
    public boolean process(NacosTask task) {
        int rowCount = configInfoGrayPersistService.configInfoGrayCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / PAGE_SIZE);
        
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoGrayWrapper> page = configInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(pageNo, PAGE_SIZE);
            if (page != null) {
                for (ConfigInfoGrayWrapper cf : page.getPageItems()) {
                    boolean result = ConfigCacheService
                            .dumpGray(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getGrayName(), cf.getGrayRule(), cf.getContent(),
                                    cf.getLastModified(), cf.getEncryptedDataKey());
                    LogUtil.DUMP_LOG.info("[dump-all-gray-ok] result={}, {}, {}, length={}, md5={}, grayName={}", result,
                            GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(),
                            cf.getContent().length(), cf.getMd5(), cf.getGrayName());
                }
                
                actualRowCount += page.getPageItems().size();
                DEFAULT_LOG.info("[all-dump-gray] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }
    
    static final int PAGE_SIZE = getAllDumpPageSize();
    
    final ConfigInfoGrayPersistService configInfoGrayPersistService;
}
