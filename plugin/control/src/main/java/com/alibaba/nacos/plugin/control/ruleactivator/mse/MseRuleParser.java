package com.alibaba.nacos.plugin.control.ruleactivator.mse;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.ruleactivator.NacosRuleParser;
import com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail;
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
    
    @Override
    public TpsControlRule parseTpsRule(String ruleContent) {
        TpsControlRule tpsControlRule = super.parseTpsRule(ruleContent);
        if (tpsControlRule == null) {
            return null;
        }
        JsonNode jsonNode = JacksonUtils.toObj(ruleContent);
        if (jsonNode.get("pointRule") != null) {
            MseRuleDetail mseRuleDetail = JacksonUtils
                    .toObj(jsonNode.get("pointRule").toPrettyString(), MseRuleDetail.class);
            tpsControlRule.setPointRule(mseRuleDetail);
        }
        
        if (jsonNode.get("monitorKeyRule") != null) {
            Map<String, MseRuleDetail> monitorKeyRule = JacksonUtils
                    .toObj(jsonNode.get("monitorKeyRule").toPrettyString(),
                            new TypeReference<Map<String, MseRuleDetail>>() {
                            });
            Map<String, RuleDetail> monitorKeyRule1 = tpsControlRule.getMonitorKeyRule();
            for (Map.Entry<String, MseRuleDetail> entry : monitorKeyRule.entrySet()) {
                monitorKeyRule1.put(entry.getKey(), entry.getValue());
            }
        }
        return tpsControlRule;
    }
}
