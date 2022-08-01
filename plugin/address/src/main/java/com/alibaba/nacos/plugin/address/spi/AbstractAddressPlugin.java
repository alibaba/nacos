/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.address.spi;

import java.util.List;
import java.util.function.Consumer;

/**
 * Date 2022/7/30.
 *
 * @author GuoJiangFu
 */
public abstract class AbstractAddressPlugin implements AddressPlugin {
    
    protected List<String> serverList;
    
    protected Consumer<List<String>> addressListener;
    
    @Override
    public List<String> getServerList() {
        return serverList;
    }
    
    @Override
    public void registerListener(Consumer<List<String>> addressListener) {
        this.addressListener = addressListener;
    }
    
    @Override
    public void shutdown() {
    
    }
}
