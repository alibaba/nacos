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

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.SlowEvent;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    
    private static final List<String> PREFERRED_NETWORKS = new ArrayList<>();
    
    private static final List<String> IGNORED_INTERFACES = new ArrayList<>();
    
    private static final ScheduledExecutorService INET_AUTO_REFRESH_EXECUTOR = ExecutorFactory.Managed.newSingleScheduledExecutorService(
            InetUtils.class.getCanonicalName(), new NameThreadFactory("com.alibaba.inet.ip.auto-refresh"));
    
    private static volatile String selfIP;
    
    private static boolean useOnlySiteLocalInterface = false;
    
    private static boolean preferHostnameOverIP = false;
    
    static {
        NotifyCenter.registerToSharePublisher(IPChangeEvent.class);
        
        useOnlySiteLocalInterface = Boolean.parseBoolean(EnvUtil.getProperty(USE_ONLY_SITE_INTERFACES));
        
        List<String> networks = EnvUtil.getPropertyList(Constants.PREFERRED_NETWORKS);
        PREFERRED_NETWORKS.addAll(networks);
        
        List<String> interfaces = EnvUtil.getPropertyList(Constants.IGNORED_INTERFACES);
        IGNORED_INTERFACES.addAll(interfaces);
        
        refreshIp();
        
        final long delayMs = Long.getLong(Constants.AUTO_REFRESH_TIME, 30_000L);
        
        INET_AUTO_REFRESH_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                InetUtils.refreshIp();
            } catch (Exception e) {
                LOG.error("refresh ip error", e);
            }
        }, delayMs, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * refresh ip address.
     */
    private static void refreshIp() {
        
        String tmpSelfIp = getNacosIp();
        
        if (StringUtils.isBlank(tmpSelfIp)) {
            tmpSelfIp = getPreferHostnameOverIP();
        }
        
        if (StringUtils.isBlank(tmpSelfIp)) {
            tmpSelfIp = Objects.requireNonNull(findFirstNonLoopbackAddress()).getHostAddress();
        }
        
        if (InternetAddressUtil.PREFER_IPV6_ADDRESSES && !tmpSelfIp.startsWith(InternetAddressUtil.IPV6_START_MARK)
                && !tmpSelfIp.endsWith(InternetAddressUtil.IPV6_END_MARK)) {
            tmpSelfIp = InternetAddressUtil.IPV6_START_MARK + tmpSelfIp + InternetAddressUtil.IPV6_END_MARK;
            if (StringUtils.contains(tmpSelfIp, InternetAddressUtil.PERCENT_SIGN_IN_IPV6)) {
                tmpSelfIp = tmpSelfIp.substring(0, tmpSelfIp.indexOf(InternetAddressUtil.PERCENT_SIGN_IN_IPV6))
                        + InternetAddressUtil.IPV6_END_MARK;
            }
        }
        if (!Objects.equals(selfIP, tmpSelfIp) && Objects.nonNull(selfIP)) {
            IPChangeEvent event = new IPChangeEvent();
            event.setOldIP(selfIP);
            event.setNewIP(tmpSelfIp);
            NotifyCenter.publishEvent(event);
        }
        selfIP = tmpSelfIp;
    }
    
    /**
     * Get ip address from environment
     * System property nacos.server.ip
     * Spring property nacos.inetutils.ip-address.
     *
     * @return ip address
     */
    public static String getNacosIp() {
        String nacosIp = System.getProperty(NACOS_SERVER_IP);
        if (StringUtils.isBlank(nacosIp)) {
            nacosIp = EnvUtil.getProperty(IP_ADDRESS);
        }
        if (!StringUtils.isBlank(nacosIp)) {
            if (!(InternetAddressUtil.isIP(nacosIp) || InternetAddressUtil.isDomain(nacosIp))) {
                throw new RuntimeException("nacos address " + nacosIp + " is not ip");
            }
        }
        
        return nacosIp;
    }
    
    /**
     * Get ip address.
     *
     * @return ip address
     */
    private static String getPreferHostnameOverIP() {
        preferHostnameOverIP = Boolean.getBoolean(SYSTEM_PREFER_HOSTNAME_OVER_IP);
        
        if (!preferHostnameOverIP) {
            preferHostnameOverIP = Boolean.parseBoolean(EnvUtil.getProperty(PREFER_HOSTNAME_OVER_IP));
        }
        
        if (!preferHostnameOverIP) {
            return null;
        }
        String preferHostnameOverIp = null;
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getLocalHost();
            if (inetAddress.getHostName().equals(inetAddress.getCanonicalHostName())) {
                preferHostnameOverIp = inetAddress.getHostName();
            } else {
                preferHostnameOverIp = inetAddress.getCanonicalHostName();
            }
        } catch (UnknownHostException ignore) {
            LOG.warn("Unable to retrieve localhost");
        }
        return preferHostnameOverIp;
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
                if (isUp(ifc)) {
                    LOG.debug("Testing interface: " + ifc.getDisplayName());
                    if (ifc.getIndex() >= lowest && result != null) {
                        continue;
                    } else {
                        lowest = ifc.getIndex();
                    }
                    
                    if (!ignoreInterface(ifc.getDisplayName())) {
                        for (Enumeration<InetAddress> addrs = ifc.getInetAddresses(); addrs.hasMoreElements(); ) {
                            InetAddress address = addrs.nextElement();
                            boolean isLegalIpVersion =
                                    InternetAddressUtil.PREFER_IPV6_ADDRESSES ? address instanceof Inet6Address
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
            LOG.error("Unable to retrieve localhost", e);
        }
        
        return null;
    }
    
    /**
     * check network intreface isUp, not throw SocketException.
     * @param ifc network interface
     * @return true or false;
     */
    public static boolean isUp(NetworkInterface ifc)  {
        try {
            return ifc.isUp();
        } catch (SocketException e) {
            LOG.debug("Network interface can not get isUp, exception: ", e);
        }
        return false;
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
