package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {

        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<ShopType> shopTypeList;
        if(!CollectionUtil.isEmpty(shopTypeJsonList)){
           shopTypeList = shopTypeJsonList.stream().map(j-> (ShopType)JSONUtil.toBean(j,ShopType.class,true)
           ).collect(Collectors.toList());
           return shopTypeList;
        }

        //缓存未命中
        shopTypeList = query().orderByAsc("sort").list();
        if(CollectionUtil.isEmpty(shopTypeList)){
            return null;
        }
        //更新缓存
        stringRedisTemplate.opsForList().rightPushAll(key,
                shopTypeList.stream()
                        .map(s-> JSONUtil.toJsonStr(s))
                        .collect(Collectors.toList()));

        return shopTypeList;
    }
}
