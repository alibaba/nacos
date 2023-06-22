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

package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

/**
 * nacos rule parser.
 *
 * @author shiyiyue
 */
public class NacosRuleParser implements RuleParser {
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        
        return StringUtils.isBlank(ruleContent) ? new TpsControlRule()
                : JacksonUtils.toObj(ruleContent, TpsControlRule.class);
    }
    
    @Override
    public ConnectionControlRule parseConnectionRule(String ruleContent) {
        return StringUtils.isBlank(ruleContent) ? new ConnectionControlRule()
                : JacksonUtils.toObj(ruleContent, ConnectionControlRule.class);
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
    
}
