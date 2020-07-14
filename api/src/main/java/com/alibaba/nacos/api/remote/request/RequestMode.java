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
package com.alibaba.nacos.api.remote.request;

/**
 * @author liuzunfei
 * @version $Id: RequestMode.java, v 0.1 2020年07月13日 3:46 PM liuzunfei Exp $
 */
public enum RequestMode {

    COMMON("COMMON","common request "),

    CHANGE_LISTEN("CHANGE_LISTEN","listen change");

    public String mode;
    public String desc;

    /**
     * Private constructor
     * @param mode
     * @param desc
     */
    private RequestMode(String mode,String desc){
        this.mode=mode;
        this.desc=desc;
    }


}
