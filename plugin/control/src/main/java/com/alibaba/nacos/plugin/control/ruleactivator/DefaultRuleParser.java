package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

public class DefaultRuleParser implements RuleParser {
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        
        return StringUtils.isBlank(ruleContent) ? new TpsControlRule()
                : JacksonUtils.toObj(ruleContent, TpsControlRule.class);
    }
    
    @Override
    public ConnectionLimitRule parseConnectionRule(String ruleContent) {
        return StringUtils.isBlank(ruleContent) ? new ConnectionLimitRule()
                : JacksonUtils.toObj(ruleContent, ConnectionLimitRule.class);
    }
    
    @Override
    public String getName() {
        return "default";
    }
    
    
}
