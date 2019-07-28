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

import com.alibaba.nacos.dns.dto.UpdateDomainDto;
import com.alibaba.nacos.dns.exception.DomainNotFoundException;
import com.alibaba.nacos.dns.misc.Loggers;
import com.alibaba.nacos.dns.service.DomainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.alibaba.nacos.dns.constant.DnsConstants.GET_DOMAIN_FAILED;
import static com.alibaba.nacos.dns.constant.DnsConstants.NACOS_DNS_CONTEXT;

/**
 * @author paderlol
 */
@RestController
@RequestMapping(value = NACOS_DNS_CONTEXT + "/domain")
public class DnsController {

    private final DomainService domainService;

    public DnsController(DomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping("/{domainName}")
    public ResponseEntity getDomain(@PathVariable("domainName") String domainName) {

        try {
            return ResponseEntity.ok().body(domainService.getConfig(domainName));
        } catch (Exception e) {
            if (e instanceof DomainNotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            } else {
                Loggers.dnsLogger.error(GET_DOMAIN_FAILED,e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GET_DOMAIN_FAILED);
            }
        }

    }

    @PutMapping("/{domainName}")
    public ResponseEntity updateDomain(@PathVariable("domainName") String domainName,
        @RequestBody UpdateDomainDto updateDomainDto) {

        try {
            domainService.updateConfig(domainName, updateDomainDto);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            if (e instanceof DomainNotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            } else {
                Loggers.dnsLogger.error(GET_DOMAIN_FAILED,e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GET_DOMAIN_FAILED);
            }
        }
    }

}
