package com.hmdp;

import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;


    @Test
    public void addShopCache() throws InterruptedException {
        Shop shop = shopService.getById(1);
        shopService.setShopCache(shop,10L);
    }

}
