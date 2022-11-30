package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.mse.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MseRuleBarrier extends FlowedRuleBarrier {
    
    String pattern;
    
    int order;
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public MseRuleBarrier(String pointName, String ruleName, TimeUnit period) {
        super(pointName, ruleName, period);
    }
    
    @Override
    public String getBarrierName() {
        return "flowedlocalsimplecount";
    }
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
        
        super.applyRuleDetail(ruleDetail);
        if (!(ruleDetail instanceof MseRuleDetail)) {
            return;
        }
        MseRuleDetail mseRuleDetail = (MseRuleDetail) ruleDetail;
        this.pattern = mseRuleDetail.getPattern();
        this.order = mseRuleDetail.getOrder();
        
        //interceptor,only apply for point
        Collection<TpsInterceptor> interceptors = InterceptorHolder.getInterceptors();
        List<TpsInterceptor> pointerInterceptor = interceptors.stream()
                .filter(a -> a.getPointName().equalsIgnoreCase(this.getPointName())).collect(Collectors.toList());
        Set<String> disabledInterceptors = ((MseRuleDetail) ruleDetail).getDisabledInterceptors();
        for (TpsInterceptor tpsInterceptor : pointerInterceptor) {
            if (tpsInterceptor.getPointName().equalsIgnoreCase(this.getPointName()) && this.getPointName()
                    .equalsIgnoreCase(ruleDetail.getRuleName())) {
                if (disabledInterceptors != null && disabledInterceptors.contains(tpsInterceptor.getName())) {
                    tpsInterceptor.setDisabled(true);
                } else {
                    tpsInterceptor.setDisabled(false);
                }
            }
        }
        
    }
}
