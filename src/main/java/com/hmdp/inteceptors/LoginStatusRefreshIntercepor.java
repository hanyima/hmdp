package com.hmdp.inteceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginStatusRefreshIntercepor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public LoginStatusRefreshIntercepor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader("authorization");

        String key = RedisConstants.LOGIN_CODE_KEY + token ;
        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(key);

        if(MapUtil.isEmpty(userDTOMap))
            return true;

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDTOMap, new UserDTO(), true);

        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);


        return true ;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        UserHolder.removeUser();
    }
}
