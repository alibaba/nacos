/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos Inner API auth enabled.
 *
 * <p>
 *     For old version such as 2.x, grpc inner api and some http api without identity header.
 *     But in 3.x, the inner api also need to do identity check, which might cause the data is not consistent due to the inner api no identity.
 *     So we need to check whether servers are all upgraded to new versions which support request inner api with identity header.
 *     After that, we can enable the inner api auth check.
 * </p>
 * <p>
 *     This class is only working during upgrading step for 2.x to 3.x. After nacos cluster upgrade to 3.x, this class will stop working.
 *     It will cause the downgrade from 3.x to 2.x is not smoothly(lossless).
 * </p>
 * <p>
 *     The support will be removed after 3.x not support upgrading from 2.x to 3.x in community.
 *     It might be 3.1.0 or 3.2.0, depend on community's decision.
 * </p>
 *
 * @author xiweng.yy
 */
@Component
public class InnerApiAuthEnabled {
    
    private final ServerMemberManager serverMemberManager;
    
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    
    public InnerApiAuthEnabled(ServerMemberManager serverMemberManager) {
        this.serverMemberManager = serverMemberManager;
    }
    
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * Check whether all servers are all upgraded to new version. If so enabled inner api auth check.
     * After enabled, checking will directly return.
     */
    @Scheduled(fixedRate = 3000)
    public void doCheck() {
        if (enabled.get()) {
            return;
        }
        for (Member each : serverMemberManager.allMembers()) {
            String version = (String) each.getExtendVal(MemberMetaDataConstants.VERSION);
            if (StringUtils.isBlank(version) || !version.startsWith("3.")) {
                return;
            }
        }
        Loggers.CLUSTER.info("All Nacos server upgrade to upper 3.x, enabled inner api auth identity check");
        enabled.set(true);
    }
}
