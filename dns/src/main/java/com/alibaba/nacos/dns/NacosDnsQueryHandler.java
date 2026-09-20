/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.dns;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DNS query handler that resolves domain names to Nacos service instances.
 *
 * <p>Domain format:
 * <ul>
 *   <li>{serviceName}.{groupName}.nacos - resolves to healthy instances of the service</li>
 *   <li>{serviceName}.nacos - uses default group</li>
 * </ul>
 *
 * @author Nacos
 */
@Component
public class NacosDnsQueryHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsQueryHandler.class);
    
    /** Maximum number of answer records to include in a response. */
    private static final int MAX_ANSWER_RECORDS = 20;
    
    private final InstanceOperatorClientImpl instanceOperator;
    private final NacosDnsProperties properties;
    
    public NacosDnsQueryHandler(InstanceOperatorClientImpl instanceOperator,
        NacosDnsProperties properties) {
        this.instanceOperator = instanceOperator;
        this.properties = properties;
    }
    
    /**
     * Handle a DNS query message and return the response message.
     *
     * @param query the DNS query message
     * @return the DNS response message
     */
    public Message handleQuery(Message query) {
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setFlag(Flags.RA);
        
        Record question = query.getQuestion();
        if (question == null) {
            response.getHeader().setRcode(Rcode.FORMERR);
            return response;
        }
        
        // Only answer IN class queries
        if (question.getDClass() != DClass.IN) {
            response.getHeader().setRcode(Rcode.REFUSED);
            return response;
        }
        
        String domain = question.getName().toString(true);
        int type = question.getType();
        
        LOGGER.debug("DNS query: domain={}, type={}", domain, Type.string(type));
        
        // Only support A record and AAAA record for now
        if (type != Type.A && type != Type.AAAA) {
            response.getHeader().setRcode(Rcode.NOTIMP);
            return response;
        }
        
        // Parse domain and look up service
        List<InetAddress> addresses = resolveToAddresses(domain);
        
        if (addresses == null || addresses.isEmpty()) {
            response.getHeader().setRcode(Rcode.NXDOMAIN);
            return response;
        }
        
        // Filter addresses by requested record type first, then shuffle and limit.
        // This ensures mixed IPv4/IPv6 deployments don't return empty answers.
        List<InetAddress> matching = addresses.stream()
            .filter(addr -> (type == Type.A && addr.getAddress().length == 4)
                || (type == Type.AAAA && addr.getAddress().length == 16))
            .collect(Collectors.toList());
        
        if (matching.isEmpty()) {
            response.getHeader().setRcode(Rcode.NOERROR);
            response.addRecord(question, Section.QUESTION);
            return response;
        }
        
        // Shuffle matching addresses for basic round-robin, then cap at MAX_ANSWER_RECORDS
        Collections.shuffle(matching);
        int limit = Math.min(matching.size(), MAX_ANSWER_RECORDS);
        
        // Add question section
        response.addRecord(question, Section.QUESTION);
        
        // Add answer records
        Name queryName = question.getName();
        for (int i = 0; i < limit; i++) {
            InetAddress addr = matching.get(i);
            Record record;
            if (type == Type.A) {
                record = new ARecord(queryName, DClass.IN, properties.getTtl(), addr);
            } else {
                record = new AAAARecord(queryName, DClass.IN, properties.getTtl(), addr);
            }
            response.addRecord(record, Section.ANSWER);
        }
        
        return response;
    }
    
    /**
     * Resolve a domain name to a list of IP addresses from Nacos service instances.
     */
    private List<InetAddress> resolveToAddresses(String domain) {
        // Remove trailing dot
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        
        // Check domain suffix
        String suffix = "." + properties.getDomainSuffix();
        if (!domain.endsWith(suffix)) {
            LOGGER.debug("Domain {} does not match suffix {}", domain, suffix);
            return null;
        }
        
        // Strip suffix
        String servicePart = domain.substring(0, domain.length() - suffix.length());
        
        // Parse service name and group
        String[] parts = servicePart.split("\\.");
        String serviceName;
        String groupName;
        
        if (parts.length == 1) {
            serviceName = parts[0];
            groupName = properties.getDefaultGroup();
        } else if (parts.length >= 2) {
            serviceName = parts[0];
            groupName = parts[1];
        } else {
            LOGGER.debug("Invalid domain format: {}", domain);
            return null;
        }
        
        LOGGER.debug("Resolving service: serviceName={}, groupName={}, namespace={}",
            serviceName, groupName, properties.getNamespace());
        
        // Query Nacos for healthy instances
        try {
            ServiceInfo serviceInfo = instanceOperator.listInstance(
                properties.getNamespace(), groupName, serviceName,
                null, null, true);
            
            if (serviceInfo == null || serviceInfo.getHosts() == null) {
                return null;
            }
            
            return serviceInfo.getHosts().stream()
                .filter(Instance::isEnabled)
                .filter(Instance::isHealthy)
                .map(inst -> parseIpAddress(inst.getIp()))
                .filter(addr -> addr != null)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            LOGGER.error("Failed to query Nacos service: {}/{}", groupName, serviceName, e);
            return null;
        }
    }
    
    /**
     * Parse an IP string into InetAddress without performing a DNS lookup.
     */
    private InetAddress parseIpAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            LOGGER.warn("Invalid IP address: {}", ip);
            return null;
        }
    }
}
