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
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.persistence.model.Page;

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
        long currentMaxId = configInfoPersistService.findConfigMaxId();
        long lastMaxId = 0;
        while (lastMaxId < currentMaxId) {
            Page<ConfigInfoWrapper> page = configInfoPersistService.findAllConfigInfoFragment(lastMaxId, PAGE_SIZE);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                break;
            }
            for (ConfigInfoWrapper cf : page.getPageItems()) {
                long id = cf.getId();
                lastMaxId = Math.max(id, lastMaxId);
                if (cf.getDataId().equals(AggrWhitelist.AGGRIDS_METADATA)) {
                    AggrWhitelist.load(cf.getContent());
                }
                
                if (cf.getDataId().equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
                    ClientIpWhiteList.load(cf.getContent());
                }
                
                if (cf.getDataId().equals(SwitchService.SWITCH_META_DATA_ID)) {
                    SwitchService.load(cf.getContent());
                }

                ConfigCacheService.dump(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getContent(),
                        cf.getLastModified(), cf.getType(), cf.getEncryptedDataKey());
                
                final String content = cf.getContent();
                final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
                LogUtil.DUMP_LOG.info("[dump-all-ok] {}, {}, length={}, md5={}",
                        GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(), content.length(),
                        md5);
            }
            DEFAULT_LOG.info("[all-dump] {} / {}", lastMaxId, currentMaxId);
        }
        return true;
    }
    
    static final int PAGE_SIZE = 1000;
    
    final ConfigInfoPersistService configInfoPersistService;
}
