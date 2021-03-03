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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.IPUtil;
import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Member node tool class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberUtil {
    
    protected static final String TARGET_MEMBER_CONNECT_REFUSE_ERRMSG = "Connection refused";
    
    /**
     * Information copy.
     *
     * @param newMember {@link Member}
     * @param oldMember {@link Member}
     */
    public static void copy(Member newMember, Member oldMember) {
        oldMember.setIp(newMember.getIp());
        oldMember.setPort(newMember.getPort());
        oldMember.setState(newMember.getState());
        oldMember.setExtendInfo(newMember.getExtendInfo());
        oldMember.setAddress(newMember.getAddress());
        oldMember.setAbilities(newMember.getAbilities());
    }
    
    /**
     * parse ip:port to member.
     *
     * @param member ip:port
     * @return {@link Member}
     */
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    public static Member singleParse(String member) {
        // Nacos default port is 8848
        int defaultPort = Integer.valueOf(System.getProperty("server.port", "8848"));
        // Set the default Raft port information for securit
        
        String address = member;
        int port = defaultPort;
        String[] info = IPUtil.splitIPPortStr(address);
        if (info.length > 1) {
            address = info[0];
            port = Integer.parseInt(info[1]);
        }
        
        Member target = Member.builder().ip(address).port(port).state(NodeState.UP).build();
        
        Map<String, Object> extendInfo = new HashMap<>(4);
        // The Raft Port information needs to be set by default
        extendInfo.put(MemberMetaDataConstants.RAFT_PORT, String.valueOf(calculateRaftPort(target)));
        target.setExtendInfo(extendInfo);
        return target;
    }
    
    /**
     * check whether the member support long connection or not.
     *
     * @param member member instance of server.
     * @return support long connection or not.
     */
    public static boolean isSupportedLongCon(Member member) {
        if (member.getAbilities() == null || member.getAbilities().getRemoteAbility() == null) {
            return false;
        }
        return member.getAbilities().getRemoteAbility().isSupportRemoteConnection();
    }
    
    public static int calculateRaftPort(Member member) {
        return member.getPort() - 1000;
    }
    
    /**
     * Resolves to Member list.
     *
     * @param addresses ip list, example [127.0.0.1:8847,127.0.0.1:8848,127.0.0.1:8849]
     * @return member list
     */
    public static Collection<Member> multiParse(Collection<String> addresses) {
        List<Member> members = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            Member member = singleParse(address);
            members.add(member);
        }
        return members;
    }
    
    /**
     * Successful processing of the operation on the node.
     *
     * @param member {@link Member}
     */
    public static void onSuccess(final ServerMemberManager manager, final Member member) {
        final NodeState old = member.getState();
        manager.getMemberAddressInfos().add(member.getAddress());
        member.setState(NodeState.UP);
        member.setFailAccessCnt(0);
        if (!Objects.equals(old, member.getState())) {
            manager.notifyMemberChange();
        }
    }
    
    public static void onFail(final ServerMemberManager manager, final Member member) {
        // To avoid null pointer judgments, pass in one NONE_EXCEPTION
        onFail(manager, member, ExceptionUtil.NONE_EXCEPTION);
    }
    
    /**
     * Failure processing of the operation on the node.
     *
     * @param member {@link Member}
     * @param ex     {@link Throwable}
     */
    public static void onFail(final ServerMemberManager manager, final Member member, Throwable ex) {
        manager.getMemberAddressInfos().remove(member.getAddress());
        final NodeState old = member.getState();
        member.setState(NodeState.SUSPICIOUS);
        member.setFailAccessCnt(member.getFailAccessCnt() + 1);
        int maxFailAccessCnt = EnvUtil.getProperty("nacos.core.member.fail-access-cnt", Integer.class, 3);
        
        // If the number of consecutive failures to access the target node reaches
        // a maximum, or the link request is rejected, the state is directly down
        if (member.getFailAccessCnt() > maxFailAccessCnt || StringUtils
                .containsIgnoreCase(ex.getMessage(), TARGET_MEMBER_CONNECT_REFUSE_ERRMSG)) {
            member.setState(NodeState.DOWN);
        }
        if (!Objects.equals(old, member.getState())) {
            manager.notifyMemberChange();
        }
    }
    
    /**
     * Node list information persistence.
     *
     * @param members member list
     */
    public static void syncToFile(Collection<Member> members) {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("#").append(LocalDateTime.now()).append(StringUtils.LF);
            for (String member : simpleMembers(members)) {
                builder.append(member).append(StringUtils.LF);
            }
            EnvUtil.writeClusterConf(builder.toString());
        } catch (Throwable ex) {
            Loggers.CLUSTER.error("cluster member node persistence failed : {}", ExceptionUtil.getAllExceptionMsg(ex));
        }
    }
    
    /**
     * We randomly pick k nodes.
     *
     * @param members member list
     * @param filter  filter {@link Predicate}
     * @param k       node number
     * @return target members
     */
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    public static Collection<Member> kRandom(Collection<Member> members, Predicate<Member> filter, int k) {
        
        Set<Member> kMembers = new HashSet<>();
        
        // Here thinking similar consul gossip protocols random k node
        int totalSize = members.size();
        Member[] membersArray = members.toArray(new Member[totalSize]);
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        for (int i = 0; i < 3 * totalSize && kMembers.size() < k; i++) {
            int idx = threadLocalRandom.nextInt(totalSize);
            Member member = membersArray[idx];
            if (filter.test(member)) {
                kMembers.add(member);
            }
        }
        
        return kMembers;
    }
    
    /**
     * Default configuration format resolution, only NACos-Server IP or IP :port or hostname: Port information.
     */
    public static Collection<Member> readServerConf(Collection<String> members) {
        Set<Member> nodes = new HashSet<>();
        
        for (String member : members) {
            Member target = singleParse(member);
            nodes.add(target);
        }
        
        return nodes;
    }
    
    /**
     * Select target members with filter.
     *
     * @param members original members
     * @param filter  filter
     * @return target members
     */
    public static Set<Member> selectTargetMembers(Collection<Member> members, Predicate<Member> filter) {
        return members.stream().filter(filter).collect(Collectors.toSet());
    }
    
    /**
     * Get address list of members.
     *
     * @param members members
     * @return address list
     */
    public static List<String> simpleMembers(Collection<Member> members) {
        return members.stream().map(Member::getAddress).sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Judge whether basic info has changed.
     *
     * @param actual   actual member
     * @param expected expected member
     * @return true if all content is same, otherwise false
     */
    public static boolean isBasicInfoChanged(Member actual, Member expected) {
        if (null == expected) {
            return null == actual;
        }
        if (!expected.getIp().equals(actual.getIp())) {
            return true;
        }
        if (expected.getPort() != actual.getPort()) {
            return true;
        }
        if (!expected.getAddress().equals(actual.getAddress())) {
            return true;
        }
        if (!expected.getState().equals(actual.getState())) {
            return true;
        }
    
        if (!expected.getAbilities().equals(actual.getAbilities())) {
            return true;
        }
    
        return isBasicInfoChangedInExtendInfo(expected, actual);
    }
    
    private static boolean isBasicInfoChangedInExtendInfo(Member expected, Member actual) {
        for (String each : MemberMetaDataConstants.BASIC_META_KEYS) {
            if (expected.getExtendInfo().containsKey(each) != actual.getExtendInfo().containsKey(each)) {
                return true;
            }
            if (!Objects.equals(expected.getExtendVal(each), actual.getExtendVal(each))) {
                return true;
            }
        }
        return false;
    }
}
