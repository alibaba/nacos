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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import org.springframework.stereotype.Component;

/**
 * Distro tag generator.
 *
 * @author xiweng.yy
 */
@Component
public class DistroTagGeneratorImpl implements DistroTagGenerator {
    
    private final DistroTagGenerator serviceNameTag = new DistroServiceNameTagGenerator();
    
    private final DistroTagGenerator ipPortTag = new DistroIpPortTagGenerator();
    
    private final UpgradeJudgement upgradeJudgement;
    
    public DistroTagGeneratorImpl(UpgradeJudgement upgradeJudgement) {
        this.upgradeJudgement = upgradeJudgement;
    }
    
    @Override
    public String getResponsibleTag(ReuseHttpServletRequest request) {
        return getTagGenerator().getResponsibleTag(request);
    }
    
    /**
     * Get tag generator according to cluster member ability.
     *
     * <p>
     * If all member is upper than 2.x. Using {@link DistroIpPortTagGenerator}. Otherwise use 1.x {@link
     * DistroServiceNameTagGenerator}.
     * </p>
     *
     * @return actual tag generator
     */
    private DistroTagGenerator getTagGenerator() {
        return upgradeJudgement.isUseGrpcFeatures() ? ipPortTag : serviceNameTag;
    }
}
