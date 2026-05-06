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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.config.server.utils.LogUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * The pointcut for configuration change operations, it will log when the configuration change fails.
 *
 * @author blake.qiu
 */
@Aspect
@Component
public class ConfigOpFailureAspect {
    
    private static final Logger LOGGER = LogUtil.DEFAULT_LOG;
    
    /**
     * Pointcut for all methods from 'configRepositoryInterface'.
     */
    @Pointcut("within(com.alibaba.nacos.config.server.service.repository..*)")
    public void configRepositoryInterfaceMethods() {
    }
    
    /**
     * Log message when a method from 'configRepositoryInterface' throws an exception.
     */
    @AfterThrowing(pointcut = "configRepositoryInterfaceMethods()", throwing = "exception")
    public void logException(JoinPoint joinPoint, Throwable exception) {
        try {
            Object[] args = joinPoint.getArgs();
            StringBuilder params = new StringBuilder();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (i < args.length - 1) {
                        params.append(args[i]).append(", ");
                    } else {
                        params.append(args[i]);
                    }
                }
            }
            String methodName = joinPoint.getSignature().getName();
            LOGGER.error("An error occurred while executing method [{}].\n Parameters: [{}].", methodName, params,
                    exception);
        } catch (Exception e) {
            LOGGER.error("An error occurred while logging the original exception. method [{}]",
                    joinPoint.getSignature().getName(), e);
        }
    }
}
