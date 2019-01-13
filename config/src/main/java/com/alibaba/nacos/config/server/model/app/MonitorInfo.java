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
     * 可使用内存.
     */
    private long totalMemory;
    /**
     * 剩余内存.
     */
    private long freeMemory;
    /**
     * 最大可使用内存.
     */
    private volatile long maxMemory;
    /**
     * cpu使用率.
     */
    private double cpuRatio;
    /**
     * 系统负载.
     */
    private double load;
    /**
     * ygc次数
     */
    private int ygc;
    /**
     * ygc时间
     */
    private double ygct;
    /**
     * fgc次数
     */
    private int fgc;
    /**
     * fgc时间
     */
    private double fgct;
    /**
     * gc时间
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
        return "MonitorInfo{" +
            "totalMemory=" + totalMemory +
            ", freeMemory=" + freeMemory +
            ", maxMemory=" + maxMemory +
            ", cpuRatio=" + cpuRatio +
            ", load=" + load +
            ", ygc=" + ygc +
            ", ygct=" + ygct +
            ", fgc=" + fgc +
            ", fgct=" + fgct +
            ", gct=" + gct +
            '}';
    }
}

