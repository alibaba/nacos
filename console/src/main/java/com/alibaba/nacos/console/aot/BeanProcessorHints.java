package com.alibaba.nacos.console.aot;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * @author Dioxide.CN
 * @date 2024/9/16
 * @since 1.0
 */
@Component
public class BeanProcessorHints implements BeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // ClientConnectionEventListenerRegistry
        System.out.println("[test] Initializing bean: " + bean.getClass().getName());
        return bean;
    }
    
}
