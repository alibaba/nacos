package com.alibaba.nacos.plugin.control.ruleactivator.mse;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.ruleactivator.DefaultRuleParser;
import com.alibaba.nacos.plugin.control.tps.mse.FlowedRuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class MseRuleParser extends DefaultRuleParser {
    
    @Override
    public String getName() {
        return "flowed";
    }
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        TpsControlRule tpsControlRule = super.parseTpsRule(ruleContent);
        if (tpsControlRule == null) {
            return null;
        }
        JsonNode jsonNode = JacksonUtils.toObj(ruleContent);
        if (jsonNode.get("pointRule") != null) {
            FlowedRuleDetail flowedRuleDetail = JacksonUtils
                    .toObj(jsonNode.get("pointRule").toPrettyString(), FlowedRuleDetail.class);
            tpsControlRule.setPointRule(flowedRuleDetail);
        }
        
        if (jsonNode.get("monitorKeyRule") != null) {
            Map<String, FlowedRuleDetail> monitorKeyRule = JacksonUtils
                    .toObj(jsonNode.get("monitorKeyRule").toPrettyString(),
                            new TypeReference<Map<String, FlowedRuleDetail>>() {
                            });
            Map<String, RuleDetail> monitorKeyRule1 = tpsControlRule.getMonitorKeyRule();
            for (Map.Entry<String, FlowedRuleDetail> entry : monitorKeyRule.entrySet()) {
                monitorKeyRule1.put(entry.getKey(), entry.getValue());
            }
        }
        return tpsControlRule;
    }
}
