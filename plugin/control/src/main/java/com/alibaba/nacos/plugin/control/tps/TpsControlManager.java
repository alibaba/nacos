package com.alibaba.nacos.plugin.control.tps;

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
    public final Map<String, TpsBarrier> points = new ConcurrentHashMap<>(16);
    
    /**
     * point name -> tps control rule
     */
    public final Map<String, TpsControlRule> rules = new ConcurrentHashMap<>(16);
    
    
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
    
    /**
     * check tps result.
     *
     * @param tpsRequest TpsRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse check(String pointName, TpsCheckRequest tpsRequest) {
        
        if (points.containsKey(pointName)) {
            return points.get(pointName).applyTps(tpsRequest);
        }
        return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
        
    }
}
