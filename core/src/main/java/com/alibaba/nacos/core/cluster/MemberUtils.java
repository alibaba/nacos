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

import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberUtils {

    public static void copy(Member newMember, Member oldMember) {
        oldMember.extendInfo().putAll(newMember.extendInfo());
    }

    public static Member parse(String host, int port) {
        Member member = new Member();
        member.setIp(host);
        member.setPort(port);
        return member;
    }

    public static Member parse(String address) {
        Member member = new Member();
        if (address.contains(":")) {
            String[] s = address.split(":");
            member.setIp(s[0].trim());
            member.setPort(Integer.parseInt(s[1].trim()));
        } else {
            member.setIp(address);
            member.setPort(8848);
        }
        return member;
    }

    public static void onSuccess(Member member, ServerMemberManager manager) {
        manager.getMemberAddressInfos().add(member.address());
        member.setState(NodeState.UP);
        member.setFailAccessCnt(0);
    }

    public static void onFail(Member member, ServerMemberManager manager) {
        manager.getMemberAddressInfos().remove(member.address());
        member.setState(NodeState.SUSPICIOUS);
        member.setFailAccessCnt(member.getFailAccessCnt() + 1);
        if (member.getFailAccessCnt() > 3) {
            member.setState(NodeState.DOWN);
        }
    }

    public static Collection<Member> stringToMembers(Collection<String> addresses) {
        List<Member> members = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            Member member = parse(address);
            members.add(member);
        }
        return members;
    }

    public static List<Member> kRandom(ServerMemberManager memberManager, Predicate<Member> filter) {
        int k = SpringUtils.getProperty("nacos.core.member.report.random-num", Integer.class, 3);

        List<Member> members = new ArrayList<>();
        Collection<Member> have = memberManager.allMembers();

        // Here thinking similar consul gossip protocols random k node

        int totalSize = have.size();
        for (int i = 0; i < 3 * totalSize && members.size() <= k; i++) {
            for (Member member : have) {

                if (filter.test(member)) {
                    members.add(member);
                }

            }
        }

        return members;
    }

}
