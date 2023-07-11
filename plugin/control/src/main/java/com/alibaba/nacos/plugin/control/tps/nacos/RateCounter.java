/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.tps.nacos;

import java.util.concurrent.TimeUnit;

/**
 * abstract rate counter.
 *
 * @author zunfei.lzf
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RateCounter {
    
    /**
     * rate count name.
     */
    private String name;
    
    /**
     * rate period.
     */
    private TimeUnit period;
    
    public RateCounter(String name, TimeUnit period) {
        this.name = name;
        this.period = period;
    }
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    /**
     * add count for the second of timestamp.
     *
     * @param timestamp timestamp.
     * @param count     count.
     * @return
     */
    public abstract long add(long timestamp, long count);
    
    /**
     * get count of the second of timestamp.
     *
     * @param timestamp timestamp.
     * @return
     */
    public abstract long getCount(long timestamp);
    
    public String getName() {
        return name;
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfMinute(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(Long.valueOf(substring) / 60 * 60 + "000");
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfSecond(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(substring + "000");
    }
    
    /**
     * get trim mills of second.
     *
     * @param timeStamp timestamp milliseconds.
     * @return
     */
    public static long getTrimMillsOfHour(long timeStamp) {
        String millString = String.valueOf(timeStamp);
        String substring = millString.substring(0, millString.length() - 3);
        return Long.valueOf(Long.valueOf(substring) / (60 * 60) * (60 * 60) + "000");
    }
}
