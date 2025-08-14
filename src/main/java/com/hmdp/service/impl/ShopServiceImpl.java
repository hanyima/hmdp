package com.hmdp.service.impl;

import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        //1.查询缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id  ;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(!StringUtils.isEmpty(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class, true);
            return shop;
        }

        //2.缓存未命中则查询数据库
        Shop shop = getById(id);
        if(shop != null){
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }

        //3.数据库未命中，缓存空值
        stringRedisTemplate.opsForValue().set(key,null,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);

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
}
