package com.alibaba.nacos.plugin.control.ruleactivator;

import java.io.IOException;

interface RuleActivator {
    
    void saveConnectionRule(String ruleContent) throws Exception;
    
    String getConnectionRule();
    
    void saveTpsRule(String pointName, String ruleContent) throws Exception;
    
    String getTpsRule(String pointName);
    
}
