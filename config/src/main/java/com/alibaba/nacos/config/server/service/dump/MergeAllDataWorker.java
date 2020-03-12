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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.merge.MergeTaskProcessor;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.GlobalExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
class MergeAllDataWorker implements Runnable {

    private static final AtomicInteger FINISHED = new AtomicInteger();

    private static final Logger log = LoggerFactory.getLogger(MergeAllDataWorker.class);

    private static final int PAGE_SIZE = 10000;

    private static int total = 0;

    private List<ConfigInfoChanged> configInfoList;

    private PersistService persistService;

    public MergeAllDataWorker(List<ConfigInfoChanged> configInfoList) {
        this.configInfoList = configInfoList;
        this.persistService = SpringUtils.getBean(PersistService.class);
    }

    public void start() {
        GlobalExecutor.executeOnMerge(this);
    }

    @Override
    public void run() {
        for (ConfigInfoChanged configInfo : configInfoList) {
            String dataId = configInfo.getDataId();
            String group = configInfo.getGroup();
            String tenant = configInfo.getTenant();
            try {
                List<ConfigInfoAggr> datumList = new ArrayList<ConfigInfoAggr>();
                int rowCount = persistService.aggrConfigInfoCount(dataId, group, tenant);
                int pageCount = (int) Math.ceil(rowCount * 1.0 / PAGE_SIZE);
                for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                    Page<ConfigInfoAggr> page = persistService.findConfigInfoAggrByPage(dataId, group, tenant,
                            pageNo, PAGE_SIZE);
                    if (page != null) {
                        datumList.addAll(page.getPageItems());
                        log.info("[merge-query] {}, {}, size/total={}/{}", dataId, group, datumList.size(),
                                rowCount);
                    }
                }

                final Timestamp time = TimeUtils.getCurrentTime();
                // 聚合
                if (datumList.size() > 0) {
                    ConfigInfo cf = MergeTaskProcessor.merge(dataId, group, tenant, datumList);
                    String aggrContent = cf.getContent();
                    String localContentMD5 = ConfigService.getContentMd5(GroupKey.getKey(dataId, group));
                    String aggrContentMD5 = MD5.getInstance().getMD5String(aggrContent);
                    if (!StringUtils.equals(localContentMD5, aggrContentMD5)) {
                        persistService.insertOrUpdate(null, null, cf, time, null, false);
                        log.info("[merge-ok] {}, {}, size={}, length={}, md5={}, content={}", dataId, group,
                                datumList.size(), cf.getContent().length(), cf.getMd5(),
                                ContentUtils.truncateContent(cf.getContent()));
                    }
                }
                // 删除
                else {
                    persistService.removeConfigInfo(dataId, group, tenant, LOCAL_IP, null);
                    log.warn("[merge-delete] delete config info because no datum. dataId=" + dataId + ", groupId="
                            + group);
                }

            } catch (Throwable e) {
                log.info("[merge-error] " + dataId + ", " + group + ", " + e.toString(), e);
            }
            FINISHED.incrementAndGet();
            if (FINISHED.get() % 100 == 0) {
                log.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
            }
        }
        log.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
    }
}
