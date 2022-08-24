/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.spi;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.model.ConfigChangeHandleReport;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AbstractConfigChangeService.
 *
 * @author liyunfei
 */
public interface ConfigChangeService {
    
    /**
     * execute config change service.
     *
     * @param pjp                      ProceedingJoinPoint
     * @param configChangeHandleReport delivery sync plugin service handle situation
     * @return
     * @throws Throwable exception
     */
    Object execute(ProceedingJoinPoint pjp, ConfigChangeHandleReport configChangeHandleReport) throws Throwable;
    
    /**
     * when pointcut the same method,according to order to load plugin service. order is lower,prior is higher.
     *
     * @return
     */
    int getOrder();
    
    /**
     * execute type(aysnc/sync).
     *
     * @return
     */
    String executeType();
    
    /**
     * implements way (nacos/other).
     *
     * @return
     */
    String getImplWay();
    
    /**
     * what kind of plugin service,such as webhook,whiteList and other,need keep a way with the constants config of you
     * enum in {@link ConfigChangeConstants},sample as {@link AbstractWebHookPluginService}.
     *
     * @return
     */
    String getServiceType();
    
    /**
     * the method names of need to pointcut {@link com.alibaba.nacos.config.server.controller.ConfigController}.
     * <p>
     * publishConfig means when publish config produce config change info. importConfig means when import config produce
     * config change info. can point method see to {@link com.alibaba.nacos.plugin.config.apsect.ConfigChangeAspect}
     * </p>
     *
     * @return
     */
    default Set<String> pointcutMethodNames() {
        Set<String> set = new HashSet<>(5);
        String pointcutNames = ConfigChangeConstants.getPointcuts(getServiceType());
        String[] pointcutStrs = pointcutNames.split("\\,");
        return Arrays.stream(pointcutStrs).collect(Collectors.toSet());
    }
    
    /**
     * identify the config change plugin service detail name,which point to what kind of plugin service.
     * nacos/other(such as self,..) + the respond static class name {@link ConfigChangeConstants}
     *
     * @return
     */
    default String getServiceName() {
        return getImplWay() + ":" + getServiceType();
    }
}
