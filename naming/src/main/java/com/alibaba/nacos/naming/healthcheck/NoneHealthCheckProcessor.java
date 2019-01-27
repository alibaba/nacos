package com.alibaba.nacos.naming.healthcheck;

import org.springframework.stereotype.Component;

/**
 * Health checker that does nothing
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class NoneHealthCheckProcessor implements HealthCheckProcessor {

    @Override
    public void process(HealthCheckTask task) {
        return;
    }

    @Override
    public String getType() {
        return "NONE";
    }
}
