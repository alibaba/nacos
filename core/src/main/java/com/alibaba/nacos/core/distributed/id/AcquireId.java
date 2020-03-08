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

package com.alibaba.nacos.core.distributed.id;

import java.io.Serializable;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class AcquireId implements Serializable {

    private static final long serialVersionUID = -2073195123719428170L;

    private long minId;

    private long maxId;

    private String applicant;

    public static AcquireIdBuilder builder() {
        return new AcquireIdBuilder();
    }

    public long getMinId() {
        return minId;
    }

    public void setMinId(long minId) {
        this.minId = minId;
    }

    public long getMaxId() {
        return maxId;
    }

    public void setMaxId(long maxId) {
        this.maxId = maxId;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public static final class AcquireIdBuilder {
        private long minId;
        private long maxId;
        private String applicant;

        private AcquireIdBuilder() {
        }

        public AcquireIdBuilder minId(long minId) {
            this.minId = minId;
            return this;
        }

        public AcquireIdBuilder maxId(long maxId) {
            this.maxId = maxId;
            return this;
        }

        public AcquireIdBuilder applicant(String applicant) {
            this.applicant = applicant;
            return this;
        }

        public AcquireId build() {
            AcquireId acquireId = new AcquireId();
            acquireId.setMinId(minId);
            acquireId.setMaxId(maxId);
            acquireId.setApplicant(applicant);
            return acquireId;
        }
    }
}
