package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_TOKEN_PREFIX = "login:token:";

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 登录校验
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //HttpSession session = request.getSession();
        //User user = (User)session.getAttribute("user");
        String token = request.getHeader("authorization");

        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(REDIS_TOKEN_PREFIX + token);
        UserDTO userDTO = new UserDTO();
        if(userDTOMap==null){
            log.info("用户未登录");
            response.setStatus(401);
            return false;
        }
        BeanUtil.fillBeanWithMap(userDTOMap, userDTO, true);

        UserHolder.saveUser(userDTO);

        //刷新登录有效期
        stringRedisTemplate.expire(REDIS_TOKEN_PREFIX + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //防止内存泄漏
        UserHolder.removeUser();
    }
}
