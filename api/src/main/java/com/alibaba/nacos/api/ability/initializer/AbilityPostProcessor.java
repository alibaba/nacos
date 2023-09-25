package com.alibaba.nacos.api.ability.initializer;

import com.alibaba.nacos.api.ability.constant.AbilityKey;

import java.util.Map;

/**
 * Nacos ability post processor, load by spi.
 *
 * @author Daydreamer-ia
 */
public interface AbilityPostProcessor {


    /**
     * process before loading by <code>Ability Controller </code>.
     *
     * @param abilities abilities
     */
    void process(Map<AbilityKey, Boolean> abilities);

}
