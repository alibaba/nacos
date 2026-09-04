/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config;

import java.io.Serializable;

/**
 * Result object for removing configuration, providing detailed feedback.
 *
 * <p>Unlike the legacy {@code removeConfig} method that only returns a
 * boolean, this result object provides detailed error information including
 * error code and message, enabling callers to distinguish between different
 * failure modes.</p>
 *
 * @author nacos
 * @since 3.3.0
 */
public class RemoveConfigResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether the removal was successful.
     */
    private boolean success;

    /**
     * Error code when the removal failed.
     * 0 indicates success.
     */
    private int errorCode;

    /**
     * Detailed error message when the removal failed.
     */
    private String errorMessage;

    public RemoveConfigResult() {
    }

    private RemoveConfigResult(Builder builder) {
        this.success = builder.success;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
    }

    /**
     * Create a success result.
     *
     * @return success result
     */
    public static RemoveConfigResult success() {
        return new Builder().success(true).build();
    }

    /**
     * Create a failure result.
     *
     * @param errorCode    error code
     * @param errorMessage error message
     * @return failure result
     */
    public static RemoveConfigResult fail(int errorCode, String errorMessage) {
        return new Builder().success(false).errorCode(errorCode).errorMessage(errorMessage)
            .build();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "RemoveConfigResult{success=" + success + ", errorCode=" + errorCode
            + ", errorMessage='" + errorMessage + "'}";
    }

    /**
     * Builder for {@link RemoveConfigResult}.
     */
    public static final class Builder {

        private boolean success;

        private int errorCode;

        private String errorMessage;

        private Builder() {
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorCode(int errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public RemoveConfigResult build() {
            return new RemoveConfigResult(this);
        }
    }
}
