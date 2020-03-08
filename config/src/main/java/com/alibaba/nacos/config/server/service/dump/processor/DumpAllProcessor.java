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

import com.alibaba.nacos.config.server.manager.AbstractTask;
import com.alibaba.nacos.config.server.manager.TaskProcessor;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DumpAllProcessor implements TaskProcessor {

    static final int PAGE_SIZE = 1000;
    final DumpService dumpService;
    final PersistService persistService;

    public DumpAllProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.getPersistService();
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        long currentMaxId = persistService.findConfigMaxId();
        long lastMaxId = 0;
        while (lastMaxId < currentMaxId) {
            Page<ConfigInfoWrapper> page = persistService.findAllConfigInfoFragment(lastMaxId,
                    PAGE_SIZE);
            if (page != null && page.getPageItems() != null && !page.getPageItems().isEmpty()) {
                for (ConfigInfoWrapper cf : page.getPageItems()) {
                    long id = cf.getId();
                    lastMaxId = id > lastMaxId ? id : lastMaxId;
                    if (cf.getDataId().equals(AggrWhitelist.AGGRIDS_METADATA)) {
                        AggrWhitelist.load(cf.getContent());
                    }

                    if (cf.getDataId().equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
                        ClientIpWhiteList.load(cf.getContent());
                    }

                    if (cf.getDataId().equals(SwitchService.SWITCH_META_DATAID)) {
                        SwitchService.load(cf.getContent());
                    }

                    boolean result = ConfigService.dump(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getContent(),
                            cf.getLastModified(), cf.getType());

                    final String content = cf.getContent();
                    final String md5 = MD5.getInstance().getMD5String(content);
                    LogUtil.dumpLog.info("[dump-all-ok] {}, {}, length={}, md5={}",
                            GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(), content.length(), md5);
                }
                defaultLog.info("[all-dump] {} / {}", lastMaxId, currentMaxId);
            } else {
                lastMaxId += PAGE_SIZE;
            }
        }
        return true;
    }
}
