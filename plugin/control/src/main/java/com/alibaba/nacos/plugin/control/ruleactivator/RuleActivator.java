package com.alibaba.nacos.plugin.control.ruleactivator;

/**
 * @author shiyiyue
 * @date 2022-10-26 11:43:00
 */
public interface RuleActivator {
    
    String getName();
    
    void saveConnectionRule(String ruleContent) throws Exception;
    
    String getConnectionRule();
    
    void saveTpsRule(String pointName, String ruleContent) throws Exception;
    
    String getTpsRule(String pointName);
    
}
