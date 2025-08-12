package com.hmdp.config;

import com.hmdp.inteceptors.LoginInterceptor;
import com.hmdp.inteceptors.LoginStatusRefreshIntercepor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .excludePathPatterns(
                        "/shop/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/upload/**",
                        "/voucher/**",
                        "/shop-type/**"
                );
        registry.addInterceptor(new LoginStatusRefreshIntercepor(stringRedisTemplate));
    }
}
