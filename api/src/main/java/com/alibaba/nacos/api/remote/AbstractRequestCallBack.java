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

package com.alibaba.nacos.api.remote;

/**
 * abstract request call back.
 *
 * @author liuzunfei
 * @version $Id: AbstractRequestCallBack.java, v 0.1 2020年09月07日 3:30 PM liuzunfei Exp $
 */
public abstract class AbstractRequestCallBack implements RequestCallBack {
    
    long timeoutMills;
    
    public AbstractRequestCallBack(long timeoutMill) {
        this.timeoutMills = timeoutMill;
    }
    
    public AbstractRequestCallBack() {
        this(3000L);
    }
    
    @Override
    public long getTimeout() {
        return timeoutMills;
    }
}
