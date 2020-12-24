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
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;

import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * Dump all tag processor.
 *
 * @author Nacos
 * @date 2020/7/5 12:18 PM
 */
public class DumpAllTagProcessor implements NacosTaskProcessor {
    
    public DumpAllTagProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.getPersistService();
    }
    
    @Override
    public boolean process(NacosTask task) {
        int rowCount = persistService.configInfoTagCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / PAGE_SIZE);
        
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoTagWrapper> page = persistService.findAllConfigInfoTagForDumpAll(pageNo, PAGE_SIZE);
            if (page != null) {
                for (ConfigInfoTagWrapper cf : page.getPageItems()) {
                    boolean result = ConfigCacheService
                            .dumpTag(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getTag(), cf.getContent(),
                                    cf.getLastModified());
                    LogUtil.DUMP_LOG.info("[dump-all-Tag-ok] result={}, {}, {}, length={}, md5={}", result,
                            GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(),
                            cf.getContent().length(), cf.getMd5());
                }
                
                actualRowCount += page.getPageItems().size();
                DEFAULT_LOG.info("[all-dump-tag] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }
    
    static final int PAGE_SIZE = 1000;
    
    final DumpService dumpService;
    
    final PersistService persistService;
}
