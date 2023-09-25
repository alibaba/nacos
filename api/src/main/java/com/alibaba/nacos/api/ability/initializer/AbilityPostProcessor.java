package com.alibaba.nacos.api.ability.initializer;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityMode;

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
     * @param mode      mode: sdk client, server or cluster client
     * @param abilities abilities
     */
    void process(AbilityMode mode, Map<AbilityKey, Boolean> abilities);

}
