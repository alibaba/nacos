package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.users.User;
import java.util.List;

/**
 * User CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface UserPersistService {
    
    /**
     * query user by username.
     *
     * @param username username
     * @return user
     */
    User findUserByUsername(String username);
    
    
    /**
     * fuzzy query user by username.
     *
     * @param username username
     * @return usernames
     */
    List<String> findUserLikeUsername(String username);
    
    /**
     * get users by page.
     *
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return user page info
     */
    Page<User> getUsers(int pageNo, int pageSize);
    
}

