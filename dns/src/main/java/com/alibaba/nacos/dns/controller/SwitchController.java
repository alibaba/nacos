/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.controller;

import com.alibaba.nacos.dns.record.RecordType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.alibaba.nacos.dns.constant.DnsConstants.NACOS_DNS_CONTEXT;

@RestController
@RequestMapping(value = NACOS_DNS_CONTEXT + "/switches")
public class SwitchController {

    @GetMapping("/{domainName}")
    public ResponseEntity getSwitch(@PathVariable("domainName") String domainName,
        @PathVariable(value = "type", required = false) RecordType recordType) {

        return null;
    }


}
