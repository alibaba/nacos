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

import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import com.alibaba.nacos.plugin.config.util.ConfigChangeParamUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.PriorityQueue;

/**
 * Handle and execute before config change plugin service and after config change plugin service.
 *
 * @author liyunfei
 */
public class ConfigChangePluginHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangePluginHandler.class);
    
    private static final Integer DEFAULT_BEFORE_QUEUE_CAPACITY = 2;
    
    private static final Integer DEFAULT_AFTER_QUEUE_CAPACITY = 1;
    
    /**
     * Handle plugin service pointcut to config method.
     *
     * @param priorityQueue       priorityQueue which save plugin service pointcut the same method.
     * @param pjp                 aspect pjp.
     * @param configChangeRequest config change request,which take request args and type infos.
     */
    public static Object handle(PriorityQueue<ConfigChangeService> priorityQueue, ProceedingJoinPoint pjp,
            ConfigChangeRequest configChangeRequest) {
        ConfigChangePointCutTypes handleType = configChangeRequest.getRequestType();
        ConfigChangeResponse configChangeResponse = new ConfigChangeResponse(handleType);
        PriorityQueue<ConfigChangeService> beforeExecutePriorityQueue = new PriorityQueue<>(
                DEFAULT_BEFORE_QUEUE_CAPACITY);
        PriorityQueue<ConfigChangeService> afterExecutePriorityQueue = new PriorityQueue<>(
                DEFAULT_AFTER_QUEUE_CAPACITY);
        for (ConfigChangeService ccs : priorityQueue) {
            if (ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE.equals(ccs.executeType())) {
                beforeExecutePriorityQueue.add(ccs);
                continue;
            }
            afterExecutePriorityQueue.add(ccs);
        }
        
        //before plugin service execute
        for (ConfigChangeService ccs : beforeExecutePriorityQueue) {
            try {
                ccs.execute(pjp, configChangeRequest, configChangeResponse);
                boolean execNextFlag = ConfigChangeParamUtil.convertResponseToBoolean(configChangeResponse);
                if (!execNextFlag) {
                    break;
                }
            } catch (Throwable throwable) {
                LOGGER.warn("execute sync plugin services failed {}", throwable.getMessage());
                configChangeResponse.setRetVal(false); // true/false mean success/failed
                configChangeResponse.setMsg(throwable.getMessage()); // ex info , other info
                break;
            }
        }
        
        // no before plugin service execute
        if (configChangeResponse.getRetVal() == null) {
            try {
                configChangeResponse.setRetVal(pjp.proceed());
            } catch (Throwable throwable) {
                configChangeResponse.setRetVal(false); // failed
                configChangeResponse.setMsg(throwable.getMessage());
            }
        }
        
        // after plugin service execute
        ConfigExecutor.executeAsyncConfigChangePluginTask(() -> {
            for (ConfigChangeService ccs : afterExecutePriorityQueue) {
                try {
                    ccs.execute(pjp, configChangeRequest, configChangeResponse);
                } catch (Throwable throwable) {
                    LOGGER.warn("execute async plugin services failed {}", throwable.getMessage());
                }
            }
        });
        
        return configChangeResponse.getRetVal();
    }
}
