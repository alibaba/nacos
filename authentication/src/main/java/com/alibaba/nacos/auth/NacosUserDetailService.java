package authentication.src.main.java.com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.UserDetailService;
import com.alibaba.nacos.auth.model.UserFromService;

public class NacosUserDetailService implements UserDetailService {
    
    /**
     * 从Nacos service端获取用户信息，我看到Nacos的用户信息放在下面几个类中：
     * 1. com.alibaba.nacos.api.common.Constants;
     * 2. Nacos通过EmbeddedUserPersistServiceImpl、ExternalUserPersistServiceImpl类来查询数据库获取用户信息，
     *    它也定义了一个UserPersistService接口
     * @param username
     * @return
     */
    @Override
    public UserFromService loadUserDetailByUsername(String username){
        return null;
    }
    
}
