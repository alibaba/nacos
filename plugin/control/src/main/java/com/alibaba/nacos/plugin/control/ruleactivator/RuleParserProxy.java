package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class RuleParserProxy {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private static RuleParser INSTANCE;
    
    static {
        Collection<RuleParser> ruleParsers = NacosServiceLoader.load(RuleParser.class);
        String ruleParserName = ControlConfigs.getInstance().getRuleParser();
        
        for (RuleParser ruleParser : ruleParsers) {
            if (ruleParser.getName().equalsIgnoreCase(ruleParserName)) {
                LOGGER.info("Found  rule parser of name={},class={}" ,ruleParserName,ruleParser.getClass().getSimpleName());
                INSTANCE = ruleParser;
                break;
            }
        }
        if (INSTANCE == null) {
            LOGGER.warn("Fail to found persist rule activator of name ：" + ruleParserName);
            INSTANCE = new DefaultRuleParser();
        }
    }
    
    public static RuleParser getInstance() {
        return INSTANCE;
    }
}
