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

import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import com.alibaba.nacos.dns.record.RecordType;
import com.alibaba.nacos.dns.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.dns.constant.DnsConstants.GET_DOMAIN_FAILED;
import static com.alibaba.nacos.dns.constant.DnsConstants.NACOS_DNS_CONTEXT;

/**
 * @author paderlol
 */
@RestController
@RequestMapping(value = NACOS_DNS_CONTEXT + "/record")
public class RecordController {

    @Autowired
    private RecordService recordService;

    @GetMapping("/{domainName}/{type}")
    public ResponseEntity getRecord(@PathVariable("domainName") String domainName,
        @PathVariable(value = "type", required = false) RecordType recordType) {
        try {
            return ResponseEntity.ok().body(recordService.getRecord(domainName, recordType));
        } catch (Exception e) {
            if (e instanceof DomainNotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GET_DOMAIN_FAILED);
            }
        }
    }

}
