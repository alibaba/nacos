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

package com.alibaba.nacos.common.utils;

/**
 * Obeserver.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author xiweng.yy
 */
public interface Observer {
    
    /**
     * This method is called whenever the observed object is changed. An application calls an {@code Observable}
     * object's {@code notifyObservers} method to have all the object's observers notified of the change.
     *
     * @param o the observable object.
     */
    void update(Observable o);
    
}
