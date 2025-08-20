package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    ExecutorService cachedThreadPool = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("查询的店铺不存在");
        }
        log.info("查询成功:{}",shop);
        return Result.ok(shop);
    }

    //采用逻辑过期解决缓存击穿
    private Shop queryWithLogicalExpire(Long id){

        String key = RedisConstants.CACHE_SHOP_KEY + id ;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id ;
        //1.查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.缓存未命中直接返回空值
        if(StringUtils.isEmpty(shopJson)){
            return null;
        }
        //3.判断缓存是否过期，未过期直接返回
        RedisData redisData = (RedisData)JSONUtil.toBean(shopJson, RedisData.class, true);
        if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
            JSONObject jsonObject = (JSONObject)redisData.getData();
            return (Shop)JSONUtil.toBean(jsonObject,Shop.class);
        }
        //4.缓存过期，进入缓存重构

        cachedThreadPool.execute(() -> {
            //4.1 获取互斥锁
            try {
                boolean flag = tryLock(lockKey);
                //4.2 成功，查询数据库，更新缓存
                if(flag){
                    Shop shop = getById(id);
                    setShopCache(shop,30L);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                releaseLock(lockKey);
            }
        });
        //4.3获取失败，说明有线程重构，直接返回旧数据
        JSONObject jsonObject = (JSONObject)redisData.getData();
        return (Shop)JSONUtil.toBean(jsonObject,Shop.class);
    }


    //采用互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id) {

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id ;
        try {
            while(true) {
                //1.查询缓存
                String key = RedisConstants.CACHE_SHOP_KEY + id  ;
                String shopJson = stringRedisTemplate.opsForValue().get(key);
                if(!StringUtils.isEmpty(shopJson)){
                    Shop shop = JSONUtil.toBean(shopJson, Shop.class, true);
                    return shop;
                }

                if(shopJson!=null){
                    return null;
                }

                //2.缓存未命中，重构缓存
                //2.1 获取互斥锁
                if(!tryLock(lockKey)){
                    Thread.sleep(100);
                    continue;
                }

                //2.2 获取成功，查询数据库
                if(stringRedisTemplate.opsForValue().get(key)!=null){
                    return JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), Shop.class, true);
                }
                Shop shop = getById(id);
                //2.2.1 数据库命中，写入缓存
                if(shop != null){
                    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
                    return shop;
                }

                //2.2.2 数据库未命中，缓存空值
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

                return shop;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //3. 释放互斥锁
            releaseLock(lockKey);
        }
    }

    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        //1.查询缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id  ;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(!StringUtils.isEmpty(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class, true);
            return shop;
        }

        if(shopJson!=null){
            return null;
        }

        //2.缓存未命中则查询数据库
        Shop shop = getById(id);
        if(shop != null){
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }

        //3.数据库未命中，缓存空值
        stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

        return null;
    }


    /**
     * 更新方法店铺同时删除缓存
     * @param shop
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        updateById(shop);
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    private boolean tryLock(String key){

        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(key,Thread.currentThread().getName(),RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);

        return BooleanUtil.isTrue(res);
    }

    private boolean releaseLock(String key){
        Boolean res = false;
        //检查是否是此线程持有锁
        if(Thread.currentThread().getName().equals(redisTemplate.opsForValue().get(key))){
            res = stringRedisTemplate.delete(key);
        }
        return BooleanUtil.isTrue(res);

    }

    /**
     * 手动添加店铺缓存
     * @param shop
     * @param ttl
     */
    public void setShopCache(Shop shop,Long ttl) throws InterruptedException {
        Thread.sleep(200);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

}
