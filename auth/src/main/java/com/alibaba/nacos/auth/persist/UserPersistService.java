package com.alibaba.nacos.auth.persist;

import com.alibaba.nacos.auth.model.Page;
import com.alibaba.nacos.auth.users.User;

/**
 * User CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface UserPersistService {
    
    /**
     * create user.
     *
     * @param username username
     * @param password password
     */
    void createUser(String username, String password);
    
    /**
     * query user by username.
     *
     * @param username username
     * @return user
     */
    User findUserByUsername(String username);
    
    /**
     * get users by page.
     *
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return user page info
     */
    Page<User> getUsers(int pageNo, int pageSize);
    
}

