package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.google.common.base.Objects;

/**
 * Implementation of health checker for TCP.
 *
 * @author yangyi
 */
public class Tcp extends AbstractHealthChecker {
    public static final String TYPE = "TCP";

    public Tcp() {
        this.type = TYPE;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(TYPE);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Tcp;
    }

    @Override
    public Tcp clone() throws CloneNotSupportedException {
        Tcp config = new Tcp();
        config.setType(this.type);
        return config;
    }
}
