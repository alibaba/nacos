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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.core.utils.Loggers;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class Task implements Runnable {

    protected ServerMemberManager memberManager;

    public void setMemberManager(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }

    @Override
    public void run() {
        try {
            executeBody();
        } catch (Throwable t) {
            Loggers.CORE.error("this task execute has error : {}", t);
        }
    }

    // init some resource

    public void init() {

    }

    /**
     * Task executive
     */
    protected abstract void executeBody();

}
