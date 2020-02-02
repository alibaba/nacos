/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.config.server.configuration.DataSource4ClusterV2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Aspect
@Component
public class XIDAspect {

    @Autowired
    private DataSource4ClusterV2 dataSource;

    @Pointcut(value = "@annotation(com.alibaba.nacos.config.server.annoation.OpenXID)")
    private void openXID() {
    }

    @Around("openXID()")
    public Object aroundXID(ProceedingJoinPoint pjp) throws Throwable {
        final String methodName = pjp.getSignature().getName();
        final Object[] args = pjp.getArgs();
        final String xid = dataSource.openDistributeTransaction();
        LogUtil.defaultLog.info("open distribute transaction, method : {}, args : {}, xid : {}", methodName, args, xid);
        try {
            Object result = pjp.proceed();

            // If all the above transactions are successful, a commit operation is performed,
            // and all Connections are notified of the commit operation.

            dataSource.commitLocal();
            return result;
        } catch (Throwable e) {

            // If an exception occurs during the transaction, rollback the transaction

            LogUtil.defaultLog.error("distribute transaction has error and execute rollback. xid : {}, error : {}", xid, e);
            dataSource.rollbackLocal();
            throw e;
        } finally {
            dataSource.freedLocal();
        }
    }

}
