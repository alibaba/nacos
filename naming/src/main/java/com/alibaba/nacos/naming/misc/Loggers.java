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
package com.alibaba.nacos.naming.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author nacos
 */
public class Loggers {

    public static final Logger PUSH = LoggerFactory.getLogger("com.alibaba.nacos.naming.push");

    public static final Logger CHECK_RT = LoggerFactory.getLogger("com.alibaba.nacos.naming.rt");

    public static final Logger SRV_LOG = LoggerFactory.getLogger("com.alibaba.nacos.naming.main");

    public static final Logger EVT_LOG = LoggerFactory.getLogger("com.alibaba.nacos.naming.event");

    public static final Logger RAFT = LoggerFactory.getLogger("com.alibaba.nacos.naming.raft");

    public static final Logger EPHEMERAL = LoggerFactory.getLogger("com.alibaba.nacos.naming.ephemeral");

    public static final Logger PERFORMANCE_LOG = LoggerFactory.getLogger("com.alibaba.nacos.naming.performance");

    public static final Logger DEBUG_LOG = LoggerFactory.getLogger("com.alibaba.nacos.naming.debug");
}
