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

package com.alibaba.nacos.core.distributed.raft.jraft;

import com.alibaba.nacos.core.distributed.Log;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosClosure implements Closure {

    private final Log log;
    private final Closure closure;
    private Throwable throwable;

    public NacosClosure(Log log, Closure closure) {
        this.log = log;
        this.closure = closure;
    }

    @Override
    public void run(Status status) {
        if (closure != null) {
            closure.run(new NStatus(status, throwable));
        }
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Closure getClosure() {
        return closure;
    }

    public Log getLog() {
        return log;
    }

    // Pass the Throwable inside the state machine to the outer layer

    public static class NStatus extends Status {

        private Status status;

        private Throwable throwable;

        public NStatus(Status status, Throwable throwable) {
            super();
            this.status = status;
            this.throwable = throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public Throwable getThrowable() {
            return throwable;
        }


    }
}
