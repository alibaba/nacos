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

package com.alibaba.nacos.naming.cluster;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alipay.sofa.jraft.RouteTable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * Detect and control the working status of local server.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Service
public class ServerStatusManager {
    
    private final ProtocolManager protocolManager;
    
    private final SwitchDomain switchDomain;
    
    private ServerStatus serverStatus = ServerStatus.STARTING;
    
    public ServerStatusManager(ProtocolManager protocolManager, SwitchDomain switchDomain) {
        this.protocolManager = protocolManager;
        this.switchDomain = switchDomain;
    }
    
    @PostConstruct
    public void init() {
        GlobalExecutor.registerServerStatusUpdater(new ServerStatusUpdater());
    }
    
    private void refreshServerStatus() {
        
        if (StringUtils.isNotBlank(switchDomain.getOverriddenServerStatus())) {
            serverStatus = ServerStatus.valueOf(switchDomain.getOverriddenServerStatus());
            return;
        }
        
        if (hasLeader()) {
            serverStatus = ServerStatus.UP;
        } else {
            serverStatus = ServerStatus.DOWN;
        }
    }
    
    private boolean hasLeader() {
        if (protocolManager.getCpProtocol() == null) {
            return false;
        }
        return null != RouteTable.getInstance().selectLeader(Constants.NAMING_PERSISTENT_SERVICE_GROUP);
    }
    
    public ServerStatus getServerStatus() {
        return serverStatus;
    }
    
    public Optional<String> getErrorMsg() {
        if (hasLeader()) {
            return Optional.empty();
        }
        return Optional.of("No leader for raft group " + Constants.NAMING_PERSISTENT_SERVICE_GROUP
                + ", please see logs `alipay-jraft.log` or `naming-raft.log` to see details.");
    }
    
    public class ServerStatusUpdater implements Runnable {
        
        @Override
        public void run() {
            refreshServerStatus();
        }
    }
}
