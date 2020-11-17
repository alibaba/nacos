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

package com.alibaba.nacos.address;

import com.alibaba.nacos.common.utils.IPUtil;
import org.junit.Test;

public class ParamCheckUtilTests {
    
    @Test
    public void checkIPs() {
        String[] ips = {"127.0.0.1"};
        System.out.println(IPUtil.checkIPs(ips));
        
        String[] illlegalIps = {"127.100.19", "127.0.0.1"};
        System.err.println(IPUtil.checkIPs(illlegalIps));
    }
}
