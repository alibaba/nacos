package com.alibaba.nacos.plugin.control.ruleactivator;

public class RuleParserProxy {
    
    public static RuleParser getInstance() {
        return new DefaultRuleParser();
    }
}
