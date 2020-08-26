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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.verify;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.DistroComponentHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroData;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.List;

/**
 * Distro verify task.
 *
 * @author xiweng.yy
 */
public class DistroVerifyTask implements Runnable {
    
    private final ServerMemberManager serverMemberManager;
    
    private final DistroComponentHolder distroComponentHolder;
    
    public DistroVerifyTask(ServerMemberManager serverMemberManager, DistroComponentHolder distroComponentHolder) {
        this.serverMemberManager = serverMemberManager;
        this.distroComponentHolder = distroComponentHolder;
    }
    
    @Override
    public void run() {
        try {
            List<Member> targetServer = serverMemberManager.allMembersWithoutSelf();
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("server list is: {}", targetServer);
            }
            DistroData distroData = distroComponentHolder.getDataStorage().getVerifyData(new DistroKey());
            if (null == distroData) {
                return;
            }
            distroData.setType(ApplyAction.VERIFY);
            for (Member member : targetServer) {
                distroComponentHolder.getTransportAgent().syncVerifyData(distroData, member.getAddress());
            }
        } catch (Exception e) {
            Loggers.DISTRO.error("[DISTRO-FAILED] verify task failed.", e);
        }
    }
}
