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
 * ssl context refresher spi holder.
 *
 * @author liuzunfei
 * @version $Id: RequestFilters.java, v 0.1 2023年03月17日 12:00 PM liuzunfei Exp $
 */
public interface SslContextChangeAware {
    
    /**
     * init rpc server ssl context.
     *
     * @param baseRpcServer rpc server.
     */
    void init(BaseRpcServer baseRpcServer);
    
    /**
     * do something on ssl context change.
     */
    void onSslContextChange();
    
    /**
     * shutdown to clear context.
     */
    void shutdown();
}
