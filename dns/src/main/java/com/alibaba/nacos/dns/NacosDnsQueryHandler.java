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
import io.micrometer.core.instrument.Timer;
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
import org.xbill.DNS.SRVRecord;
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
 * <p>Supported record types: A, AAAA, SRV.
 *
 * @author Nacos
 */
@Component
public class NacosDnsQueryHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsQueryHandler.class);
    
    /** Maximum number of answer records to include in a response. */
    private static final int MAX_ANSWER_RECORDS = 20;
    
    /** Default SRV priority. */
    private static final int SRV_DEFAULT_PRIORITY = 0;
    
    /** Default SRV weight when instance weight is not set. */
    private static final int SRV_DEFAULT_WEIGHT = 1;
    
    /** DNS SRV weight is a 16-bit unsigned field (0–65535). */
    private static final int SRV_MAX_WEIGHT = 65535;
    
    private final InstanceOperatorClientImpl instanceOperator;
    private final NacosDnsProperties properties;
    private final NacosDnsMetrics metrics;
    
    public NacosDnsQueryHandler(InstanceOperatorClientImpl instanceOperator,
        NacosDnsProperties properties, NacosDnsMetrics metrics) {
        this.instanceOperator = instanceOperator;
        this.properties = properties;
        this.metrics = metrics;
    }
    
    /**
     * Check whether a domain matches the configured Nacos suffix.
     *
     * @param domain the domain name (without trailing dot)
     * @return true if the domain should be resolved locally
     */
    public boolean matchesSuffix(String domain) {
        if (domain == null) {
            return false;
        }
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.endsWith("." + properties.getDomainSuffix());
    }
    
    /**
     * Handle a DNS query message and return the response message.
     *
     * @param query the DNS query message
     * @return the DNS response message
     */
    public Message handleQuery(Message query) {
        String type = extractQueryType(query);
        Timer.Sample sample = metrics.startSample();
        String rcode = "SERVFAIL";
        try {
            Message response = doHandleQuery(query);
            rcode = Rcode.string(response.getHeader().getRcode());
            return response;
        } catch (Exception e) {
            LOGGER.error("Unexpected error handling DNS query", e);
            metrics.recordFailed();
            Message error = new Message(query.getHeader().getID());
            error.getHeader().setFlag(Flags.QR);
            // Do NOT set RA: this is a conditional forwarder, not an open recursive resolver.
            error.getHeader().setRcode(Rcode.SERVFAIL);
            rcode = "SERVFAIL";
            return error;
        } finally {
            metrics.stopSample(sample, type, rcode);
        }
    }
    
    /**
     * Extract query record type as a bounded-cardinality tag value.
     * Supported types return their name (A, AAAA, SRV); everything else maps to OTHER.
     */
    String extractQueryType(Message query) {
        Record question = query.getQuestion();
        if (question == null) {
            return "UNKNOWN";
        }
        int type = question.getType();
        if (type == Type.A || type == Type.AAAA || type == Type.SRV) {
            return Type.string(type);
        }
        return "OTHER";
    }
    
    private Message doHandleQuery(Message query) {
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        // Do NOT set RA: this is a conditional forwarder, not an open recursive resolver.
        
        Record question = query.getQuestion();
        if (question == null) {
            response.getHeader().setRcode(Rcode.FORMERR);
            metrics.recordFailed();
            return response;
        }
        
        // Only answer IN class queries
        if (question.getDClass() != DClass.IN) {
            response.getHeader().setRcode(Rcode.REFUSED);
            metrics.recordFailed();
            return response;
        }
        
        String domain = question.getName().toString(true);
        int type = question.getType();
        
        LOGGER.debug("DNS query: domain={}, type={}", domain, Type.string(type));
        
        // Support A, AAAA, and SRV record types
        if (type != Type.A && type != Type.AAAA && type != Type.SRV) {
            response.getHeader().setRcode(Rcode.NOTIMP);
            metrics.recordFailed();
            return response;
        }
        
        // Parse domain and look up service instances
        List<Instance> instances = resolveInstances(domain);
        
        if (instances == null || instances.isEmpty()) {
            response.getHeader().setRcode(Rcode.NXDOMAIN);
            metrics.recordFailed();
            return response;
        }
        
        response.addRecord(question, Section.QUESTION);
        
        if (type == Type.SRV) {
            handleSrvQuery(response, question, instances);
        } else {
            handleAddressQuery(response, question, instances, type);
        }
        
        metrics.recordSuccess();
        return response;
    }
    
    /**
     * Handle A or AAAA address queries.
     */
    private void handleAddressQuery(Message response, Record question,
        List<Instance> instances, int type) {
        // Filter addresses by requested record type first, then shuffle and limit.
        List<InetAddress> matching = instances.stream()
            .map(inst -> parseIpAddress(inst.getIp()))
            .filter(addr -> addr != null)
            .filter(addr -> (type == Type.A && addr.getAddress().length == 4)
                || (type == Type.AAAA && addr.getAddress().length == 16))
            .collect(Collectors.toList());
        
        if (matching.isEmpty()) {
            return;
        }
        
        Collections.shuffle(matching);
        int limit = Math.min(matching.size(), MAX_ANSWER_RECORDS);
        
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
    }
    
    /**
     * Handle SRV queries. Each healthy instance becomes an SRV record with
     * priority=0, weight=instance.weight, port=instance.port, and a synthetic
     * target hostname. Corresponding A/AAAA records are added to the additional
     * section so clients can resolve targets without extra queries.
     */
    private void handleSrvQuery(Message response, Record question, List<Instance> instances) {
        Collections.shuffle(instances);
        int limit = Math.min(instances.size(), MAX_ANSWER_RECORDS);
        
        Name queryName = question.getName();
        for (int i = 0; i < limit; i++) {
            Instance inst = instances.get(i);
            InetAddress addr = parseIpAddress(inst.getIp());
            if (addr == null) {
                continue;
            }
            int port = inst.getPort();
            if (port < 0 || port > 65535) {
                LOGGER.warn("Skipping instance with invalid port: {}", port);
                continue;
            }
            double rawWeight = inst.getWeight();
            int weight;
            if (rawWeight <= 0) {
                weight = SRV_DEFAULT_WEIGHT;
            } else if (rawWeight > SRV_MAX_WEIGHT) {
                weight = SRV_MAX_WEIGHT;
                LOGGER.warn("Clamping SRV weight from {} to {} for instance {}",
                    rawWeight, SRV_MAX_WEIGHT, inst.getIp());
            } else {
                weight = (int) rawWeight;
            }
            Name target = buildSrvTargetName(addr);
            SRVRecord srv = new SRVRecord(queryName, DClass.IN, properties.getTtl(),
                SRV_DEFAULT_PRIORITY, weight, port, target);
            response.addRecord(srv, Section.ANSWER);
            
            // Add glue record in additional section
            Record glue;
            if (addr.getAddress().length == 4) {
                glue = new ARecord(target, DClass.IN, properties.getTtl(), addr);
            } else {
                glue = new AAAARecord(target, DClass.IN, properties.getTtl(), addr);
            }
            response.addRecord(glue, Section.ADDITIONAL);
        }
    }
    
    /**
     * Build a synthetic target hostname for an SRV record from an IP address.
     * IPv4: 10.0.0.1 -> 10-0-0-1.nacos.
     * IPv6: uses full expanded form with hyphens, e.g. 2001-0db8-0000-0000-0000-0000-0000-0001.nacos.
     */
    private Name buildSrvTargetName(InetAddress addr) {
        String ipPart;
        if (addr.getAddress().length == 4) {
            ipPart = addr.getHostAddress().replace('.', '-');
        } else {
            // IPv6: expand to full form to avoid leading/trailing hyphens from "::"
            byte[] b = addr.getAddress();
            StringBuilder sb = new StringBuilder(39);
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(String.format("%02x%02x", b[i * 2], b[i * 2 + 1]));
            }
            ipPart = sb.toString();
        }
        return Name.fromConstantString(ipPart + "." + properties.getDomainSuffix() + ".");
    }
    
    /**
     * Resolve a domain name to a list of healthy Nacos service instances.
     */
    private List<Instance> resolveInstances(String domain) {
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
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            LOGGER.error("Failed to query Nacos service: {}/{}", groupName, serviceName, e);
            return null;
        }
    }
    
    /**
     * Parse an IP string into InetAddress without performing a DNS lookup.
     * Returns null if the string is not a valid IP address.
     */
    private InetAddress parseIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            // Use getByAddress with parsed bytes to avoid any DNS resolution
            byte[] addr = parseIpBytes(ip);
            if (addr == null) {
                return null;
            }
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            LOGGER.warn("Invalid IP address: {}", ip);
            return null;
        }
    }
    
    /**
     * Parse an IP string to raw bytes without DNS lookup.
     * Supports IPv4 (dotted decimal) and IPv6 (colon hex).
     */
    private byte[] parseIpBytes(String ip) {
        // IPv4: four octets separated by dots
        if (ip.indexOf(':') < 0) {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return null;
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                try {
                    int val = Integer.parseInt(parts[i]);
                    if (val < 0 || val > 255) {
                        return null;
                    }
                    bytes[i] = (byte) val;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return bytes;
        }
        // IPv6: strings containing ':' are IP literals, getByName parses them without DNS
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
