/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

/**
 * Config persistence context for current thread.
 *
 * <p>Used to control some persistence behaviors (e.g. whether to write history records)
 * for internal batch operations such as data migration or skill upload.</p>
 */
public final class ConfigPersistContext {
    
    private static final ThreadLocal<Boolean> SKIP_HISTORY =
        ThreadLocal.withInitial(() -> Boolean.FALSE);
    
    private ConfigPersistContext() {
    }
    
    /**
     * Whether current thread should skip writing config history.
     */
    public static boolean isSkipHistory() {
        Boolean v = SKIP_HISTORY.get();
        return v != null && v;
    }
    
    /**
     * Set whether to skip history for current thread.
     *
     * <p>Callers should use {@link #withSkipHistory()} whenever possible to ensure cleanup.</p>
     */
    public static void setSkipHistory(boolean skipHistory) {
        if (skipHistory) {
            SKIP_HISTORY.set(Boolean.TRUE);
        } else {
            clear();
        }
    }
    
    /**
     * Clear thread local context.
     */
    public static void clear() {
        SKIP_HISTORY.remove();
    }
    
    /**
     * Enable skip-history in try-with-resources style.
     */
    public static Guard withSkipHistory() {
        return new Guard(true);
    }
    
    /**
     * A guard which restores previous value when closed.
     */
    public static final class Guard implements AutoCloseable {
        
        private final Boolean previous;
        
        private Guard(boolean skipHistory) {
            this.previous = SKIP_HISTORY.get();
            setSkipHistory(skipHistory);
        }
        
        @Override
        public void close() {
            if (previous == null || !previous) {
                clear();
            } else {
                SKIP_HISTORY.set(previous);
            }
        }
    }
}
