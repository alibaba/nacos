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

package com.alibaba.nacos.config.server.model.log;

import java.sql.Timestamp;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class CleanHistoryRequest {

    private Timestamp startTime;
    private int limitSize;

    public Timestamp getStartTime() {
        return startTime;
    }

    public int getLimitSize() {
        return limitSize;
    }

    public static CleanHistoryRequestBuilder builder() {
        return new CleanHistoryRequestBuilder();
    }

    public static final class CleanHistoryRequestBuilder {
        private Timestamp startTime;
        private int limitSize;

        private CleanHistoryRequestBuilder() {
        }

        public CleanHistoryRequestBuilder startTime(Timestamp startTime) {
            this.startTime = startTime;
            return this;
        }

        public CleanHistoryRequestBuilder limitSize(int limitSize) {
            this.limitSize = limitSize;
            return this;
        }

        public CleanHistoryRequest build() {
            CleanHistoryRequest cleanHistoryRequest = new CleanHistoryRequest();
            cleanHistoryRequest.limitSize = this.limitSize;
            cleanHistoryRequest.startTime = this.startTime;
            return cleanHistoryRequest;
        }
    }
}
