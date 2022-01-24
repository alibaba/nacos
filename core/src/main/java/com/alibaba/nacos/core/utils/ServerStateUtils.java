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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * ServerStateUtils.
 *
 * @author zrlw
 */
public class ServerStateUtils {
    
    /**
     * Check if current server is temporarily waiting for first beat from all clients.
     *
     * @return if current server is temporarily waiting for first beat from all clients.
     */
    public static boolean isWaitingForFirstBeatFromAllClients() {
        DistroProtocol distroProtocol = ApplicationUtils.getBean(DistroProtocol.class);
        if (!distroProtocol.isInitialized()) {
            return true;
        }
        
        if (System.currentTimeMillis() - distroProtocol.getInitializedTime() < Constants.DEFAULT_HEART_BEAT_TIMEOUT) {
            return true;
        }
        
        return false;
    }
}
