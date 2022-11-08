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

package com.alibaba.nacos.plugin.datasource.aspect;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;


/**
 * Mapper aspect.
 *
 * @author hyx
 **/

@Aspect
public class MapperAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapperAspect.class);

    private static final String NACOS_PLUGIN_DATASOURCE_LOG = "nacos.plugin.datasource.log";
    
    private static final String CONFIG_MAPPER_LOG = "execution (* com.alibaba.nacos.plugin.datasource.impl..*.*(..)) "
            + "&&! execution (* com.alibaba.nacos.plugin.datasource.impl..*.getTableName())"
            + "&&! execution (* com.alibaba.nacos.plugin.datasource.impl..*.getDataSource())";
    
    /**
     * mapper log.
     * @param proceedingJoinPoint join point
     * @return sql
     */
    @Around(CONFIG_MAPPER_LOG)
    public String log(ProceedingJoinPoint proceedingJoinPoint) {
        String sql = null;
        try {
            sql = (String) proceedingJoinPoint.proceed();
            
            String logProperty = EnvUtil.getProperty(NACOS_PLUGIN_DATASOURCE_LOG);
            if (!Boolean.parseBoolean(logProperty)) {
                return sql;
            }
            
            Object target = proceedingJoinPoint.getTarget();
            String className = target.getClass().getSimpleName();
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            String name = signature.getName();
            LOGGER.info("[{}] method:{}, sql:{}\n", className, name, sql);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return sql;
    }
}
