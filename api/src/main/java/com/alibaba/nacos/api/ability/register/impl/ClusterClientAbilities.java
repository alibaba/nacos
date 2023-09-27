package com.alibaba.nacos.api.ability.register.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbstractAbilityRegistry;

import java.util.Map;

/**
 * It is used to register cluster client abilities.
 *
 * @author Daydreamer
 **/
public class ClusterClientAbilities extends AbstractAbilityRegistry {

    private static final ClusterClientAbilities INSTANCE = new ClusterClientAbilities();

    {
        /*
         * example:
         *   There is a function named "compression".
         *   The key is from <p>AbilityKey</p>, the value is whether turn on.
         *
         *   You can add a new public field in <p>AbilityKey</p> like:
         *       <code>DATA_COMPRESSION("compression", "description about this ability")</code>
         *
         *   And then you need to declare whether turn on in the ability table, you can:
         *       <code>supportedAbilities.put(AbilityKey.DATA_COMPRESSION, true);</code> means that current client support compression.
         *
         */
        // put ability here, which you want current client supports
    }

    /**
     * get static ability current cluster client supports.
     *
     * @return static ability
     */
    public static Map<AbilityKey, Boolean> getStaticAbilities() {
        return INSTANCE.getSupportedAbilities();
    }
}
