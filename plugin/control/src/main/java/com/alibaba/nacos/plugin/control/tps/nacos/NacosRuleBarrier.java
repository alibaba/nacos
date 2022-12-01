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

package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

/**
 * nacos rule barrier.
 *
 * @author shiyiyue
 */
public class NacosRuleBarrier extends RuleBarrier {
    
    @Override
    public String getBarrierName() {
        return null;
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        return null;
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
    
    }
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
    
    }
    
    @Override
    public TpsMetrics getMetrics(long timeStamp) {
        return null;
    }
}
