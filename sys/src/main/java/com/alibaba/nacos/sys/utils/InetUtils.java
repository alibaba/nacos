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

package com.alibaba.nacos.sys.utils;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.SlowEvent;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import static com.alibaba.nacos.sys.env.Constants.IP_ADDRESS;
import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;
import static com.alibaba.nacos.sys.env.Constants.PREFER_HOSTNAME_OVER_IP;
import static com.alibaba.nacos.sys.env.Constants.SYSTEM_PREFER_HOSTNAME_OVER_IP;
import static com.alibaba.nacos.sys.env.Constants.USE_ONLY_SITE_INTERFACES;

/**
 * Network card operation tool class.
 *
 * @author Nacos
 */
public class InetUtils {
    
    private static final Logger LOG = LoggerFactory.getLogger(InetUtils.class);
    
    private static String selfIP;
    
    private static boolean useOnlySiteLocalInterface = false;
    
    private static boolean preferHostnameOverIP = false;
    
    private static final List<String> PREFERRED_NETWORKS = new ArrayList<String>();
    
    private static final List<String> IGNORED_INTERFACES = new ArrayList<String>();
    
    static {
        NotifyCenter.registerToSharePublisher(IPChangeEvent.class);
        
        useOnlySiteLocalInterface = Boolean.parseBoolean(EnvUtil.getProperty(USE_ONLY_SITE_INTERFACES));
        
        List<String> networks = EnvUtil.getPropertyList(Constants.PREFERRED_NETWORKS);
        PREFERRED_NETWORKS.addAll(networks);
        
        List<String> interfaces = EnvUtil.getPropertyList(Constants.IGNORED_INTERFACES);
        IGNORED_INTERFACES.addAll(interfaces);
        
        final long delayMs = Long.getLong("nacos.core.inet.auto-refresh", 30_000L);
        
        Runnable ipAutoRefresh = new Runnable() {
            @Override
            public void run() {
                String nacosIP = System.getProperty(NACOS_SERVER_IP);
                if (StringUtils.isBlank(nacosIP)) {
                    nacosIP = EnvUtil.getProperty(IP_ADDRESS);
                }
                if (!StringUtils.isBlank(nacosIP)) {
                    if (!(InternetAddressUtil.isIP(nacosIP) || InternetAddressUtil.isDomain(nacosIP))) {
                        throw new RuntimeException("nacos address " + nacosIP + " is not ip");
                    }
                }
                String tmpSelfIP = nacosIP;
                if (StringUtils.isBlank(tmpSelfIP)) {
                    preferHostnameOverIP = Boolean.getBoolean(SYSTEM_PREFER_HOSTNAME_OVER_IP);
                    
                    if (!preferHostnameOverIP) {
                        preferHostnameOverIP = Boolean.parseBoolean(EnvUtil.getProperty(PREFER_HOSTNAME_OVER_IP));
                    }
                    
                    if (preferHostnameOverIP) {
                        InetAddress inetAddress;
                        try {
                            inetAddress = InetAddress.getLocalHost();
                            if (inetAddress.getHostName().equals(inetAddress.getCanonicalHostName())) {
                                tmpSelfIP = inetAddress.getHostName();
                            } else {
                                tmpSelfIP = inetAddress.getCanonicalHostName();
                            }
                        } catch (UnknownHostException ignore) {
                            LOG.warn("Unable to retrieve localhost");
                        }
                    } else {
                        tmpSelfIP = Objects.requireNonNull(findFirstNonLoopbackAddress()).getHostAddress();
                    }
                }
                if (InternetAddressUtil.PREFER_IPV6_ADDRESSES && !tmpSelfIP.startsWith(InternetAddressUtil.IPV6_START_MARK) && !tmpSelfIP
                        .endsWith(InternetAddressUtil.IPV6_END_MARK)) {
                    tmpSelfIP = InternetAddressUtil.IPV6_START_MARK + tmpSelfIP + InternetAddressUtil.IPV6_END_MARK;
                    if (StringUtils.contains(tmpSelfIP, InternetAddressUtil.PERCENT_SIGN_IN_IPV6)) {
                        tmpSelfIP = tmpSelfIP.substring(0, tmpSelfIP.indexOf(InternetAddressUtil.PERCENT_SIGN_IN_IPV6))
                                + InternetAddressUtil.IPV6_END_MARK;
                    }
                }
                if (!Objects.equals(selfIP, tmpSelfIP) && Objects.nonNull(selfIP)) {
                    IPChangeEvent event = new IPChangeEvent();
                    event.setOldIP(selfIP);
                    event.setNewIP(tmpSelfIP);
                    NotifyCenter.publishEvent(event);
                }
                selfIP = tmpSelfIP;
            }
        };
        
        ipAutoRefresh.run();
    }
    
    public static String getSelfIP() {
        return selfIP;
    }
    
    /**
     * findFirstNonLoopbackAddress.
     *
     * @return {@link InetAddress}
     */
    public static InetAddress findFirstNonLoopbackAddress() {
        InetAddress result = null;
        
        try {
            int lowest = Integer.MAX_VALUE;
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                    nics.hasMoreElements(); ) {
                NetworkInterface ifc = nics.nextElement();
                if (ifc.isUp()) {
                    LOG.debug("Testing interface: " + ifc.getDisplayName());
                    if (ifc.getIndex() < lowest || result == null) {
                        lowest = ifc.getIndex();
                    } else {
                        continue;
                    }
                    
                    if (!ignoreInterface(ifc.getDisplayName())) {
                        for (Enumeration<InetAddress> addrs = ifc.getInetAddresses(); addrs.hasMoreElements(); ) {
                            InetAddress address = addrs.nextElement();
                            boolean isLegalIpVersion = InternetAddressUtil.PREFER_IPV6_ADDRESSES ? address instanceof Inet6Address
                                    : address instanceof Inet4Address;
                            if (isLegalIpVersion && !address.isLoopbackAddress() && isPreferredAddress(address)) {
                                LOG.debug("Found non-loopback interface: " + ifc.getDisplayName());
                                result = address;
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error("Cannot get first non-loopback address", ex);
        }
        
        if (result != null) {
            return result;
        }
        
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            LOG.warn("Unable to retrieve localhost");
        }
        
        return null;
    }
    
    private static boolean isPreferredAddress(InetAddress address) {
        if (useOnlySiteLocalInterface) {
            final boolean siteLocalAddress = address.isSiteLocalAddress();
            if (!siteLocalAddress) {
                LOG.debug("Ignoring address: " + address.getHostAddress());
            }
            return siteLocalAddress;
        }
        if (PREFERRED_NETWORKS.isEmpty()) {
            return true;
        }
        for (String regex : PREFERRED_NETWORKS) {
            final String hostAddress = address.getHostAddress();
            if (hostAddress.matches(regex) || hostAddress.startsWith(regex)) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean ignoreInterface(String interfaceName) {
        for (String regex : IGNORED_INTERFACES) {
            if (interfaceName.matches(regex)) {
                LOG.debug("Ignoring interface: " + interfaceName);
                return true;
            }
        }
        return false;
    }
    
    /**
     * {@link com.alibaba.nacos.core.cluster.ServerMemberManager} is listener.
     */
    @SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "checkstyle:AbbreviationAsWordInName"})
    public static class IPChangeEvent extends SlowEvent {
        
        private String oldIP;
        
        private String newIP;
        
        public String getOldIP() {
            return oldIP;
        }
        
        public void setOldIP(String oldIP) {
            this.oldIP = oldIP;
        }
        
        public String getNewIP() {
            return newIP;
        }
        
        public void setNewIP(String newIP) {
            this.newIP = newIP;
        }
        
        @Override
        public String toString() {
            return "IPChangeEvent{" + "oldIP='" + oldIP + '\'' + ", newIP='" + newIP + '\'' + '}';
        }
    }
    
}
