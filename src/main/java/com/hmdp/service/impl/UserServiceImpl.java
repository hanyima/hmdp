package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    private static final String REDIS_CODE_PREFIX = "login:code:";
    private static final String REDIS_TOKEN_PREFIX = "login:token:";
    //验证码过期时间
    private static final long TIMEOUT_SECONDS = 90L;
    //用户登录持续时间
    private static final long USER_TIME_TO_LIVE = 3600L;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("手机号格式错误");
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(4);
        //session.setAttribute(phone,code);
        String key = REDIS_CODE_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(key,code,TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info("验证码已成功发送:{}",code);

        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if(loginForm == null){
            return Result.fail("表单为空");
        }
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            log.info("手机格式错误");
            return Result.fail("手机格式错误");
        }
        //Object code = session.getAttribute(phone);
        String code = stringRedisTemplate.opsForValue().get(REDIS_CODE_PREFIX + phone);
        String formCode = loginForm.getCode();
        if(formCode==null || code ==null || !formCode.equals(code.toString())){
            log.info("验证码错误");
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone",phone).one();
        if(user==null){
            log.info("用户不存在");
            user = createUserWithPhone(phone);
        }
        //session.setAttribute("user",user);
        String token = UUID.randomUUID().toString();
        String key = REDIS_TOKEN_PREFIX + token;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(key,userDTOMap);
        //设置过期时间
        stringRedisTemplate.expire(key,USER_TIME_TO_LIVE,TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        this.save(user);
        log.info("新建用户:{}",user);
        return user;
    }
}
