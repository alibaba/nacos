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

package com.alibaba.nacos.config.server.model.app;

/**
 * Created by qingliang on 2017/7/20.
 *
 * @author Nacos
 */
public class MonitorInfo {
    
    /**
     * Total memory can use.
     */
    private long totalMemory;
    
    /**
     * Free memory.
     */
    private long freeMemory;
    
    /**
     * Max memory can use.
     */
    private volatile long maxMemory;
    
    /**
     * Cpu ratio.
     */
    private double cpuRatio;
    
    /**
     * System load.
     */
    private double load;
    
    /**
     * Young gc time counter.
     */
    private int ygc;
    
    /**
     * Young gc time.
     */
    private double ygct;
    
    /**
     * Full gc time counter.
     */
    private int fgc;
    
    /**
     * Full gc time.
     */
    private double fgct;
    
    /**
     * Gc time.
     */
    private double gct;
    
    public long getFreeMemory() {
        return freeMemory;
    }
    
    public void setFreeMemory(long freeMemory) {
        this.freeMemory = freeMemory;
    }
    
    public long getMaxMemory() {
        return maxMemory;
    }
    
    public void setMaxMemory(long maxMemory) {
        this.maxMemory = maxMemory;
    }
    
    public long getTotalMemory() {
        return totalMemory;
    }
    
    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }
    
    public double getCpuRatio() {
        return cpuRatio;
    }
    
    public void setCpuRatio(int cpuRatio) {
        this.cpuRatio = cpuRatio;
    }
    
    public double getLoad() {
        return load;
    }
    
    public void setLoad(int load) {
        this.load = load;
    }
    
    public int getYgc() {
        return ygc;
    }
    
    public void setYgc(int ygc) {
        this.ygc = ygc;
    }
    
    public double getYgct() {
        return ygct;
    }
    
    public void setYgct(int ygct) {
        this.ygct = ygct;
    }
    
    public int getFgc() {
        return fgc;
    }
    
    public void setFgc(int fgc) {
        this.fgc = fgc;
    }
    
    public double getFgct() {
        return fgct;
    }
    
    public void setFgct(int fgct) {
        this.fgct = fgct;
    }
    
    public double getGct() {
        return gct;
    }
    
    public void setGct(int gct) {
        this.gct = gct;
    }
    
    @Override
    public String toString() {
        return "MonitorInfo{" + "totalMemory=" + totalMemory + ", freeMemory=" + freeMemory + ", maxMemory=" + maxMemory
                + ", cpuRatio=" + cpuRatio + ", load=" + load + ", ygc=" + ygc + ", ygct=" + ygct + ", fgc=" + fgc
                + ", fgct=" + fgct + ", gct=" + gct + '}';
    }
}

