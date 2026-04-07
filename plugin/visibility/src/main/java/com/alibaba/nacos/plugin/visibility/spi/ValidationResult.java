/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.visibility.spi;

/**
 * Result of single-resource visibility validation.
 *
 * @author xiweng.yy
 */
public class ValidationResult {
    
    private final boolean allowed;
    
    private final String reason;
    
    private ValidationResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    
    public static ValidationResult allow() {
        return new ValidationResult(true, null);
    }
    
    public static ValidationResult deny(String reason) {
        return new ValidationResult(false, reason);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public String getReason() {
        return reason;
    }
}
