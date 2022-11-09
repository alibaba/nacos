package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

/**
 * parse rule content from raw string.
 */
public interface RuleParser {
    
    /**
     * @param ruleContent
     * @return
     */
    TpsControlRule parseTpsRule(String ruleContent);
    
    /**
     * @param ruleContent
     * @return
     */
    ConnectionLimitRule parseConnectionRule(String ruleContent);
    
    /**
     *
     * @return
     */
    String getName();
}
