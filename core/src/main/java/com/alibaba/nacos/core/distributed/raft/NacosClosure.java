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

import com.alibaba.nacos.consistency.entity.Response;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import com.google.protobuf.Message;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosClosure implements Closure {

    private final Message message;
    private final Closure closure;
    private final NacosStatus nacosStatus = new NacosStatus();

    public NacosClosure(Message message, Closure closure) {
        this.message = message;
        this.closure = closure;
    }

    @Override
    public void run(Status status) {
        nacosStatus.setStatus(status);
        closure.run(nacosStatus);
    }

    public void setResponse(Response response) {
        this.nacosStatus.setResponse(response);
    }

    public void setThrowable(Throwable throwable) {
        this.nacosStatus.setThrowable(throwable);
    }

    public Message getMessage() {
        return message;
    }

    // Pass the Throwable inside the state machine to the outer layer

    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    public static class NacosStatus extends Status {

        private Status status;

        private Response response = null;

        private Throwable throwable = null;

        public void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public void reset() {
            status.reset();
        }

        @Override
        public boolean isOk() {
            return status.isOk();
        }

        @Override
        public void setCode(int code) {
            status.setCode(code);
        }

        @Override
        public int getCode() {
            return status.getCode();
        }

        @Override
        public RaftError getRaftError() {
            return status.getRaftError();
        }

        @Override
        public void setErrorMsg(String errMsg) {
            status.setErrorMsg(errMsg);
        }

        @Override
        public void setError(int code, String fmt, Object... args) {
            status.setError(code, fmt, args);
        }

        @Override
        public void setError(RaftError error, String fmt, Object... args) {
            status.setError(error, fmt, args);
        }

        @Override
        public String toString() {
            return status.toString();
        }

        @Override
        public Status copy() {
            NacosStatus copy = new NacosStatus();
            copy.status = this.status;
            copy.response = this.response;
            copy.throwable = this.throwable;
            return copy;
        }

        @Override
        public String getErrorMsg() {
            return status.getErrorMsg();
        }

        public Response getResponse() {
            return response;
        }

        public void setResponse(Response response) {
            this.response = response;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }


    }
}
