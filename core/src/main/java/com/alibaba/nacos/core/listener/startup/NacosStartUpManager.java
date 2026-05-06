/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.listener.startup;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos start up phase manager.
 *
 * @author xiweng.yy
 */
public class NacosStartUpManager {
    
    private static final NacosStartUpManager INSTANCE = new NacosStartUpManager();
    
    private String currentStartUpPhase;
    
    private final Map<String, NacosStartUp> startUpMap;
    
    private final List<NacosStartUp> startedList;
    
    private NacosStartUpManager() {
        startUpMap = new HashMap<>();
        for (NacosStartUp each : NacosServiceLoader.load(NacosStartUp.class)) {
            startUpMap.put(each.startUpPhase(), each);
        }
        startedList = new ArrayList<>(startUpMap.size());
    }
    
    private NacosStartUp getStartUp(String phase) {
        return startUpMap.get(phase);
    }
    
    /**
     * Mark step into new nacos start up phase.
     * @param phase phase name.
     * @throws IllegalArgumentException when phase is unknown.
     */
    public static void start(String phase) {
        NacosStartUp startUp = INSTANCE.getStartUp(phase);
        if (null == startUp) {
            throw new IllegalArgumentException("Unknown nacos start up phase " + phase);
        }
        INSTANCE.currentStartUpPhase = phase;
        INSTANCE.startedList.add(startUp);
    }
    
    /**
     * Get current nacos start up phase.
     * @return current start up phase.
     * @throws IllegalStateException when nacos not start up.
     */
    public static NacosStartUp getCurrentStartUp() {
        if (StringUtils.isBlank(INSTANCE.currentStartUpPhase)) {
            throw new IllegalStateException("Nacos don't start up.");
        }
        return INSTANCE.getStartUp(INSTANCE.currentStartUpPhase);
    }
    
    /**
     * Get reversed nacos start up which has been started list.
     * @return reversed nacos start up
     */
    public static List<NacosStartUp> getReverseStartedList() {
        List<NacosStartUp> result = new ArrayList<>(INSTANCE.startedList);
        Collections.reverse(result);
        return result;
    }
}
