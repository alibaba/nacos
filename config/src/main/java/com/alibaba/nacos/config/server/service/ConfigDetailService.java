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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * config detail service.
 *
 * @author 985492783@qq.com
 * @date 2023/2/9 5:25
 */
@Service
public class ConfigDetailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigDetailService.class);
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private BlockingQueue<SearchEvent> eventLinkedBlockingQueue = new LinkedBlockingQueue<>();
    
    private ScheduledExecutorService clientEventExecutor;
    
    private static final long WAIT_TIMEOUT = 4000L;
    
    private static final int MAX_THREAD = 2;
    
    public ConfigDetailService(ConfigInfoPersistService configInfoPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        initWorker();
    }
    
    private void initWorker() {
        clientEventExecutor = new ScheduledThreadPoolExecutor(MAX_THREAD, r -> {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.config.search.worker");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < MAX_THREAD; i++) {
            clientEventExecutor.submit(() -> {
                while (true) {
                    try {
                        SearchEvent event = eventLinkedBlockingQueue.take();
                        Page<ConfigInfo> result = null;
                        if (Constants.CONFIG_SEARCH_BLUR.equals(event.getType())) {
                            result = configInfoPersistService.findConfigInfoLike4Page(event.pageNo, event.pageSize,
                                    event.dataId, event.group, event.tenant, event.configAdvanceInfo);
                        } else {
                            result = configInfoPersistService.findConfigInfo4Page(event.pageNo, event.pageSize,
                                    event.dataId, event.group, event.tenant, event.configAdvanceInfo);
                        }
                        synchronized (event) {
                            event.setResponse(result);
                            event.notifyAll();
                        }
                    } catch (InterruptedException e) {
                        LOGGER.error("catch search worker error: {}", e.getMessage());
                    }
                }
            });
        }
    }
    
    /**
     * block thread and use workerThread to search config.
     */
    public Page<ConfigInfo> findConfigInfoPage(String search, int pageNo, int pageSize, String dataId, String group,
            String tenant, Map<String, Object> configAdvanceInfo) {
        SearchEvent searchEvent = new SearchEvent(search, pageNo, pageSize, dataId, group, tenant,
                configAdvanceInfo);
        Page<ConfigInfo> result = null;
        try {
            synchronized (searchEvent) {
                boolean offer = eventLinkedBlockingQueue.offer(searchEvent);
                if (!offer) {
                    throw new RuntimeException("config detail event offer fail.");
                }
                searchEvent.wait(WAIT_TIMEOUT);
                result = searchEvent.getResponse();
            }
        } catch (RuntimeException e) {
            LOGGER.error("config detail block queue add error: {}.", e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            LOGGER.error("get config detail timeout: {}.", e.getMessage());
            throw new RuntimeException(e);
        }
        if (result == null) {
            throw new RuntimeException("config detail has no result.");
        }
        return result;
    }
    
    public static class SearchEvent {
        private String type;
        
        private int pageNo;
        
        private int pageSize;
        
        private String dataId;
        
        private String group;
        
        private String tenant;
        
        private Map<String, Object> configAdvanceInfo;
        
        private Page<ConfigInfo> response;
    
        public SearchEvent() {
        }
    
        public SearchEvent(String type, int pageNo, int pageSize, String dataId, String group, String tenant,
                Map<String, Object> configAdvanceInfo) {
            this.type = type;
            this.pageNo = pageNo;
            this.pageSize = pageSize;
            this.dataId = dataId;
            this.group = group;
            this.tenant = tenant;
            this.configAdvanceInfo = configAdvanceInfo;
        }
    
        public String getType() {
            return type;
        }
    
        public int getPageNo() {
            return pageNo;
        }
    
        public int getPageSize() {
            return pageSize;
        }
    
        public String getDataId() {
            return dataId;
        }
    
        public String getGroup() {
            return group;
        }
    
        public String getTenant() {
            return tenant;
        }
    
        public Map<String, Object> getConfigAdvanceInfo() {
            return configAdvanceInfo;
        }
    
        public Page<ConfigInfo> getResponse() {
            return response;
        }
    
        public void setResponse(Page<ConfigInfo> response) {
            this.response = response;
        }
    }
}
