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
package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.naming.core.Application;

import java.util.List;

/**
 * @author kkyeer
 * @Description: Application Page Response Body
 * @Date:Created in 16:46 2-22
 * @Modified By:
 */
public class ApplicationPageResponse {
    private int count;

    private List<Application> applicationList;

    public int getCount() {
        return count;
    }

    public ApplicationPageResponse setCount(int count) {
        this.count = count;
        return this;
    }

    public List<Application> getApplicationList() {
        return applicationList;
    }

    public ApplicationPageResponse setApplicationList(List<Application> applicationList) {
        this.applicationList = applicationList;
        return this;
    }
}
