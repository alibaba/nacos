package com.alibaba.nacos.plugin.control.ruleactivator.mse;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.connection.mse.MseConnectionLimitRule;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.ruleactivator.NacosRuleParser;
import com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsControlRule;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class MseRuleParser extends NacosRuleParser {
    
    @Override
    public String getName() {
        return "flowed";
    }
    
    private MseTpsControlRule parse(String ruleContent) {
        return StringUtils.isBlank(ruleContent) ? new MseTpsControlRule()
                : JacksonUtils.toObj(ruleContent, MseTpsControlRule.class);
    }
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        MseTpsControlRule tpsControlRule = parse(ruleContent);
        if (tpsControlRule == null) {
            return null;
        }
        JsonNode jsonNode = JacksonUtils.toObj(ruleContent);
        if (jsonNode.get("pointRule") != null) {
            MseRuleDetail mseRuleDetail = JacksonUtils
                    .toObj(jsonNode.get("pointRule").toPrettyString(), MseRuleDetail.class);
            tpsControlRule.setPointRule(mseRuleDetail);
        }
        
        return tpsControlRule;
    }
    
    @Override
    public ConnectionLimitRule parseConnectionRule(String ruleContent) {
        return StringUtils.isBlank(ruleContent) ? new MseConnectionLimitRule()
                : JacksonUtils.toObj(ruleContent, MseConnectionLimitRule.class);
    }
}
