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

package com.alibaba.nacos.plugin.ai.pipeline.model;

import java.util.Objects;

/**
 * A single audit dimension reported by a publish pipeline security plugin.
 *
 * @author qiacheng.cxy
 */
public class Checkpoint {
    
    /**
     * Human-readable name of the audit criterion.
     */
    private String title;
    
    /**
     * Whether this criterion passed.
     */
    private boolean passed;
    
    public Checkpoint() {
    }
    
    public Checkpoint(String title, boolean passed) {
        this.title = title;
        this.passed = passed;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public boolean getPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Checkpoint that = (Checkpoint) o;
        return Objects.equals(title, that.title) && Objects.equals(passed, that.passed);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(title, passed);
    }
}
