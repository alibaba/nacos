/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Component;

/**
 * ConfigConnectionEventListener.
 *
 * @author liuzunfei
 * @version $Id: ConfigConnectionEventListener.java, v 0.1 2020年07月20日 2:27 PM liuzunfei Exp $
 */
@Component
public class ConfigConnectionEventListener extends ClientConnectionEventListener {
    
    final ConfigChangeListenContext configChangeListenContext;
    
    public ConfigConnectionEventListener(ConfigChangeListenContext configChangeListenContext) {
        this.configChangeListenContext = configChangeListenContext;
    }
    
    @Override
    public void clientConnected(Connection connect) {
        //Do nothing.
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        String connectionId = connect.getMetaInfo().getConnectionId();
        Loggers.REMOTE_DIGEST.info("[{}]client disconnected,clear config listen context", connectionId);
        configChangeListenContext.clearContextForConnectionId(connectionId);
    }
    
}
