/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.handler;

import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.plugin.config.model.ConfigChangeHandleReport;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.PriorityQueue;

/**
 * ConfigChangeHandler.
 *
 * @author liyunfei
 */
public class ConfigChangePluginHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangePluginHandler.class);
    
    /**
     * handle plugin service pointcut to config method.
     *
     * @param priorityQueue priorityQueue which save plugin service pointcut the same method.
     * @param pjd           aspect pjd.
     * @param handleType    handle type,such as publish,import,remove,update.
     * @param additionInfo  addition information to delivery.
     */
    public static Object handle(PriorityQueue<ConfigChangeService> priorityQueue, ProceedingJoinPoint pjd,
            String handleType, Map<String, Object> additionInfo) {
        ConfigChangeHandleReport handleReport = new ConfigChangeHandleReport(handleType);
        if (additionInfo != null) {
            handleReport.setAdditionInfo(additionInfo);
        }
        Object retVal = null;
        PriorityQueue<ConfigChangeService> syncPriorityQueue = new PriorityQueue<>(5);
        PriorityQueue<ConfigChangeService> asyncPriorityQueue = new PriorityQueue<>(3);
        
        for (ConfigChangeService ccs : priorityQueue) {
            if ("async".equals(ccs.executeType())) {
                asyncPriorityQueue.add(ccs);
                continue;
            }
            syncPriorityQueue.add(ccs);
        }
        
        for (ConfigChangeService ccs : syncPriorityQueue) {
            try {
              
                retVal = ccs.execute(pjd, handleReport);
                
                if (retVal instanceof RestResult) {
                    if (asyncPriorityQueue.isEmpty()) {
                        return retVal;
                    }
                    RestResult restResult = (RestResult) retVal;
                    handleReport.setRetVal(restResult);
                    handleReport.setMsg(restResult.getMessage());
                    break;
                }
                
                if (retVal instanceof ConfigPublishResponse || retVal instanceof ConfigRemoveResponse) {
                    
                    if (asyncPriorityQueue.isEmpty()) {
                        return retVal;
                    }
                    
                    if (retVal instanceof ConfigPublishResponse) {
                        ConfigPublishResponse configPublishResponse = (ConfigPublishResponse) retVal;
                        handleReport.setRetVal(configPublishResponse);
                        handleReport.setMsg(configPublishResponse.getMessage());
                        break;
                    }
    
                    ConfigRemoveResponse configRemoveResponse = (ConfigRemoveResponse) retVal;
                    handleReport.setRetVal(configRemoveResponse);
                    handleReport.setMsg(configRemoveResponse.getMessage());
                    break;
    
                }
                
                if (!(Boolean) retVal) {
                    if (asyncPriorityQueue.isEmpty()) {
                        return retVal;
                    }
                    handleReport.setRetVal(false);
                    break;
                }
                
            } catch (Throwable throwable) {
                if (asyncPriorityQueue.isEmpty()) {
                    LOGGER.error("execute sync plugin services failed {}", throwable.getMessage());
                    return RestResultUtils.failed(throwable.getMessage());
                }
                handleReport.setRetVal(false);
                handleReport.setMsg(throwable.getMessage());
                break;
            }
        }
        
        for (ConfigChangeService ccs : asyncPriorityQueue) {
            try {
                retVal = ccs.execute(pjd, handleReport);
            } catch (Throwable throwable) {
                LOGGER.error("execute async plugin services failed {}", throwable.getMessage());
            }
        }
        
        return retVal;
    }
}
