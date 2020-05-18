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

package com.alibaba.nacos.core.distributed.raft;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.google.protobuf.Message;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosClosure implements Closure {

    private final Message log;
    private final Closure closure;
    private Throwable throwable;
    private Object object;

    public NacosClosure(Message log, Closure closure) {
        this.log = log;
        this.closure = closure;
    }

    @Override
    public void run(Status status) {
        if (closure != null) {
            NStatus status1 = new NStatus(status, throwable);
            status1.setResult(object);
            closure.run(status1);
        }
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Closure getClosure() {
        return closure;
    }

    public Message getLog() {
        return log;
    }

    // Pass the Throwable inside the state machine to the outer layer

    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    public static class NStatus extends Status {

        private Status status;

        private Object result;

        private Throwable throwable;

        public NStatus(Status status, Throwable throwable) {
            super();
            this.status = status;
            this.throwable = throwable;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }


    }
}
