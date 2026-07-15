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

package com.alibaba.nacos.api.model.response;

import java.util.List;

/**
 * Server loader metric summary.
 *
 * @author yunye
 * @since 3.0.0
 */
public class ServerLoaderMetrics {
    
    /**
     * load metric of all servers.
     */
    private List<ServerLoaderMetric> detail;
    
    /**
     * The number of all server nodes.
     */
    private int memberCount;
    
    /**
     * The number of server nodes with load metric data.
     */
    private int metricsCount;
    
    /**
     * Whether all server nodes return load indicators.
     */
    private boolean completed;
    
    /**
     * The maximum number of SDK connections for the server node.
     */
    private int max;
    
    /**
     * The minimum number of SDK connections for the server node.
     */
    private int min;
    
    /**
     * total / server size.
     */
    private int avg;
    
    /**
     * (total / server size) * 1.1 .
     */
    private String threshold;
    
    /**
     * The total number of SDK connections for all server nodes.
     */
    private int total;
    
    public List<ServerLoaderMetric> getDetail() {
        return detail;
    }
    
    public void setDetail(List<ServerLoaderMetric> detail) {
        this.detail = detail;
    }
    
    public int getMemberCount() {
        return memberCount;
    }
    
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    public int getMetricsCount() {
        return metricsCount;
    }
    
    public void setMetricsCount(int metricsCount) {
        this.metricsCount = metricsCount;
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
    
    public int getMax() {
        return max;
    }
    
    public void setMax(int max) {
        this.max = max;
    }
    
    public int getMin() {
        return min;
    }
    
    public void setMin(int min) {
        this.min = min;
    }
    
    public int getAvg() {
        return avg;
    }
    
    public void setAvg(int avg) {
        this.avg = avg;
    }
    
    public String getThreshold() {
        return threshold;
    }
    
    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
}
