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

package com.alibaba.nacos.core.remote;

/**
 * call back for push.
 *
 * @author liuzunfei
 * @version $Id: PushCallBack.java, v 0.1 2020年09月01日 5:55 PM liuzunfei Exp $
 */
public interface PushCallBack {
    
    /**
     * get timeout mills.
     *
     * @return
     */
    public long getTimeout();
    
    /**
     * called on success.
     **/
    public void onSuccess();
    
    /**
     * called on failed.
     *
     * @param e exception throwed.
     */
    public void onException(Exception e);
}
