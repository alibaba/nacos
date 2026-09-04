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
 * Result object for publishing configuration, providing detailed feedback.
 *
 * <p>Unlike the legacy {@code publishConfig} methods that only return a
 * boolean, this result object provides detailed error information including
 * error code and message, enabling callers to distinguish between different
 * failure modes (e.g., CAS conflict, permission denied, server error).</p>
 *
 * @author nacos
 * @since 3.3.0
 */
public class PublishConfigResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether the publish was successful.
     */
    private boolean success;

    /**
     * Error code when the publish failed.
     * 0 indicates success. Common values:
     * <ul>
     *     <li>403 - No permission</li>
     *     <li>500 - Server internal error</li>
     *     <li>CAS failure - content conflict</li>
     * </ul>
     */
    private int errorCode;

    /**
     * Detailed error message when the publish failed.
     */
    private String errorMessage;

    /**
     * MD5 of the published content (returned by server on success).
     */
    private String md5;

    public PublishConfigResult() {
    }

    private PublishConfigResult(Builder builder) {
        this.success = builder.success;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.md5 = builder.md5;
    }

    /**
     * Create a success result.
     *
     * @return success result
     */
    public static PublishConfigResult success() {
        return new Builder().success(true).build();
    }

    /**
     * Create a success result with MD5.
     *
     * @param md5 MD5 of the published content
     * @return success result
     */
    public static PublishConfigResult success(String md5) {
        return new Builder().success(true).md5(md5).build();
    }

    /**
     * Create a failure result.
     *
     * @param errorCode    error code
     * @param errorMessage error message
     * @return failure result
     */
    public static PublishConfigResult fail(int errorCode, String errorMessage) {
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

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    @Override
    public String toString() {
        return "PublishConfigResult{success=" + success + ", errorCode=" + errorCode
            + ", errorMessage='" + errorMessage + "', md5='" + md5 + "'}";
    }

    /**
     * Builder for {@link PublishConfigResult}.
     */
    public static final class Builder {

        private boolean success;

        private int errorCode;

        private String errorMessage;

        private String md5;

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

        public Builder md5(String md5) {
            this.md5 = md5;
            return this;
        }

        public PublishConfigResult build() {
            return new PublishConfigResult(this);
        }
    }
}
