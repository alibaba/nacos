package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

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
