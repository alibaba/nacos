package com.alibaba.nacos.core.remote.circuitbreaker;


import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Config class for circuit breaker. Can be used as base config class.
 *
 * TODO: design more generic configs
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月06日 12:38 PM chuzefang Exp $
 */
public class CircuitBreakerConfig {

    private String strategyName;

    private Long limit;

    private Long timePeriod;

    public String getStrategyName() {
        return StringUtils.isEmpty(strategyName) ? CircuitBreaker.DEFAULT_RULE : strategyName;
    }

}
