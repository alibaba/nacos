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
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
        response.getHeader().setFlag(org.xbill.DNS.Flags.QR);
        response.getHeader().setFlag(org.xbill.DNS.Flags.RA);
        
        Record question = query.getQuestion();
        if (question == null) {
            response.getHeader().setRcode(Rcode.FORMERR);
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
        
        // Add question section
        response.addRecord(question, Section.QUESTION);
        
        // Add answer records
        Name queryName = question.getName();
        for (InetAddress addr : addresses) {
            Record record;
            if (type == Type.A && addr.getAddress().length == 4) {
                record = new ARecord(queryName, DClass.IN, properties.getTtl(), addr);
            } else if (type == Type.AAAA && addr.getAddress().length == 16) {
                record = new AAAARecord(queryName, DClass.IN, properties.getTtl(), addr);
            } else {
                continue;
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
                    .map(inst -> {
                        try {
                            return InetAddress.getByName(inst.getIp());
                        } catch (UnknownHostException e) {
                            LOGGER.warn("Invalid IP address: {}", inst.getIp());
                            return null;
                        }
                    })
                    .filter(addr -> addr != null)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            LOGGER.error("Failed to query Nacos service: {}/{}", groupName, serviceName, e);
            return null;
        }
    }
}
