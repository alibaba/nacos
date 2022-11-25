package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

/**
 * parse rule content from raw string.
 */
public interface RuleParser {
    
    /**
     * parse tps rule content.
     *
     * @param ruleContent ruleContent.
     * @return
     */
    TpsControlRule parseTpsRule(String ruleContent);
    
    /**
     * parse connection rule.
     *
     * @param ruleContent ruleContent.
     * @return
     */
    ConnectionControlRule parseConnectionRule(String ruleContent);
    
    /**
     * get name.
     *
     * @return
     */
    String getName();
}
