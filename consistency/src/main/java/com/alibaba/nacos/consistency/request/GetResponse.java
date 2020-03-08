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

package com.alibaba.nacos.consistency.request;

import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class GetResponse<T> implements Serializable {

    private static final long serialVersionUID = 3558534873792008265L;

    private T data;
    private String exceptionName;
    private String errMsg;

    public static <T> GetResponseBuilder<T> builder() {
        return new GetResponseBuilder<>();
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getExceptionName() {
        return exceptionName;
    }

    public void setExceptionName(String exceptionName) {
        this.exceptionName = exceptionName;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public boolean success() {
        return data != null || (StringUtils.isEmpty(errMsg) && StringUtils.isEmpty(exceptionName));
    }

    public static final class GetResponseBuilder<T> {
        private T data;
        private String exceptionName;
        private String errMsg;

        private GetResponseBuilder() {
        }

        public GetResponseBuilder<T> data(T data) {
            this.data = data;
            return this;
        }

        public GetResponseBuilder<T> exceptionName(String exceptionName) {
            this.exceptionName = exceptionName;
            return this;
        }

        public GetResponseBuilder<T> errMsg(String errMsg) {
            this.errMsg = errMsg;
            return this;
        }

        public GetResponse<T> build() {
            GetResponse<T> getResponse = new GetResponse<>();
            getResponse.setData(data);
            getResponse.setExceptionName(exceptionName);
            getResponse.setErrMsg(errMsg);
            return getResponse;
        }
    }
}
