package com.alibaba.nacos.plugin.environment.spi;

import java.util.Map;
import java.util.Set;

/**
 * CustomEnvironment Plugin Service.
 *
 * @author : huangtianhui
 */
public interface CustomEnvironmentPluginService {
    /**
     * customValue interface.
     *
     * @param property property key value
     * @return custom key value
     */
    Map<String, Object> customValue(Map<String, Object> property);

    /**
     * propertyKey interface.
     *
     * @return propertyKey property Key
     */
    Set<String> propertyKey();

    /**
     * order  The larger the priority, the higher the priority.
     *
     * @return order
     */
    Integer order();

    /**
     * pluginName.
     *
     * @return
     */
    String pluginName();
}
