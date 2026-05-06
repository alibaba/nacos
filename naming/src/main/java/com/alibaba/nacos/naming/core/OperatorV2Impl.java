/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;

/**
 * OperatorV2Impl.
 *
 * @author Nacos
 */

@Service
public class OperatorV2Impl implements Operator {
    
    @Resource
    private SwitchDomain switchDomain;
    
    @Resource
    private SwitchManager switchManager;
    
    @Resource
    private ServerStatusManager serverStatusManager;
    
    @Resource
    private ClientManager clientManager;
    
    @Override
    public SwitchDomain switches() {
        return switchDomain;
    }
    
    @Override
    public void updateSwitch(String entry, String value, boolean debug) throws Exception {
        switchManager.update(entry, value, debug);
    }
    
    @Override
    public MetricsInfoVo metrics(boolean onlyStatus) {
        MetricsInfoVo metricsInfoVo = new MetricsInfoVo();
        metricsInfoVo.setStatus(serverStatusManager.getServerStatus().name());
        if (onlyStatus) {
            return metricsInfoVo;
        }
        
        int connectionBasedClient = 0;
        int ephemeralIpPortClient = 0;
        int persistentIpPortClient = 0;
        int responsibleClientCount = 0;
        Collection<String> allClientId = clientManager.allClientId();
        for (String clientId : allClientId) {
            if (clientId.contains(IpPortBasedClient.ID_DELIMITER)) {
                if (clientId.endsWith(ClientConstants.PERSISTENT_SUFFIX)) {
                    persistentIpPortClient += 1;
                } else {
                    ephemeralIpPortClient += 1;
                }
            } else {
                connectionBasedClient += 1;
            }
            if (clientManager.isResponsibleClient(clientManager.getClient(clientId))) {
                responsibleClientCount += 1;
            }
        }
        
        metricsInfoVo.setServiceCount(MetricsMonitor.getDomCountMonitor().get());
        metricsInfoVo.setInstanceCount(MetricsMonitor.getIpCountMonitor().get());
        metricsInfoVo.setSubscribeCount(MetricsMonitor.getSubscriberCount().get());
        metricsInfoVo.setClientCount(allClientId.size());
        metricsInfoVo.setConnectionBasedClientCount(connectionBasedClient);
        metricsInfoVo.setEphemeralIpPortClientCount(ephemeralIpPortClient);
        metricsInfoVo.setPersistentIpPortClientCount(persistentIpPortClient);
        metricsInfoVo.setResponsibleClientCount(responsibleClientCount);
        metricsInfoVo.setCpu(EnvUtil.getCpu());
        metricsInfoVo.setLoad(EnvUtil.getLoad());
        metricsInfoVo.setMem(EnvUtil.getMem());
        
        return metricsInfoVo;
    }
    
    @Override
    public void setLogLevel(String logName, String logLevel) {
        Loggers.setLogLevel(logName, logLevel);
    }
}