package com.alibaba.nacos.naming.healthcheck;

/**
 * Health check mode
 *
 * @author dungu.zpf
 */
public enum HealthCheckMode {
    /**
     * Health check sent from server.
     */
    server,
    /**
     * Health check sent from client.
     */
    client,
    /**
     * Health check disabled.
     */
    none
}
