package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

public class MseRuleParser implements RuleParser {
    
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        return null;
    }
    
    @Override
    public ConnectionLimitRule parseConnectionRule(String ruleContent) {
        return null;
    }
}
