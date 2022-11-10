package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import org.slf4j.Logger;

import java.util.Collection;

public class RuleParserProxy {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private static RuleParser instance;
    
    static {
        Collection<RuleParser> ruleParsers = NacosServiceLoader.load(RuleParser.class);
        String ruleParserName = ControlConfigs.getInstance().getRuleParser();
        
        for (RuleParser ruleParser : ruleParsers) {
            if (ruleParser.getName().equalsIgnoreCase(ruleParserName)) {
                LOGGER.info("Found  rule parser of name={},class={}", ruleParserName,
                        ruleParser.getClass().getSimpleName());
                instance = ruleParser;
                break;
            }
        }
        if (instance == null) {
            LOGGER.warn("Fail to rule parser of name ：" + ruleParserName);
            instance = new DefaultRuleParser();
        }
    }
    
    public static RuleParser getInstance() {
        return instance;
    }
}
