package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.interceptor.InterceptorHolder;
import com.alibaba.nacos.plugin.control.tps.interceptor.InterceptResult;
import com.alibaba.nacos.plugin.control.tps.interceptor.TpsInterceptor;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * abstract tps control manager
 */
public class TpsControlManager {
    
    /**
     * point name -> tps barrier
     */
    private final Map<String, TpsBarrier> points = new ConcurrentHashMap<>(16);
    
    /**
     * point name -> tps control rule
     */
    private final Map<String, TpsControlRule> rules = new ConcurrentHashMap<>(16);
    
    
    /**
     * apple tps rule.
     *
     * @param pointName
     */
    public synchronized void registerTpsPoint(String pointName) {
        if (!points.containsKey(pointName)) {
            points.put(pointName, new TpsBarrier(pointName));
            if (rules.containsKey(pointName)) {
                points.get(pointName).applyRule(rules.get(pointName));
            }
        }
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName
     * @param rule
     */
    public synchronized void applyTpsRule(String pointName, TpsControlRule rule) {
        if (rule == null) {
            rules.remove(pointName);
        } else {
            rules.put(pointName, rule);
            
        }
        if (points.containsKey(pointName)) {
            points.get(pointName).applyRule(rule);
        }
    }
    
    
    public Map<String, TpsBarrier> getPoints() {
        return points;
    }
    
    public Map<String, TpsControlRule> getRules() {
        return rules;
    }
    
    
    /**
     * check tps result.
     *
     * @param tpsRequest TpsRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
        
        for (TpsInterceptor tpsInterceptor : InterceptorHolder.getInterceptors()) {
            InterceptResult intercept = tpsInterceptor.intercept(tpsRequest);
            if (intercept.equals(InterceptResult.CHECK_PASS)) {
                return new TpsCheckResponse(true, TpsResultCode.CHECK_PASS,
                        "pass by interceptor :" + tpsInterceptor.getName());
            } else if (intercept.equals(InterceptResult.CHECK_DENY)) {
                return new TpsCheckResponse(false, TpsResultCode.CHECK_DENY,
                        "deny by interceptor :" + tpsInterceptor.getName());
            }
        }
        
        if (points.containsKey(tpsRequest.getPointName())) {
            return points.get(tpsRequest.getPointName()).applyTps(tpsRequest);
        }
        return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
        
    }
}
