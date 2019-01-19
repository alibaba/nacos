package com.alibaba.nacos.naming.cluster.members;

import java.util.List;

/**
 * Nacos cluster member change event listener
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @since 1.0.0
 */
public interface MemberChangeListener {

    /**
     * If member list changed, this method is invoked.
     *
     * @param latestMembers members after chang
     */
    void onChangeMemberList(List<Member> latestMembers);

    /**
     * If reachable member list changed, this method is invoked.
     *
     * @param latestReachableMembers reachable members after change
     */
    void onChangeReachableMemberList(List<Member> latestReachableMembers);
}
