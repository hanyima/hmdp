package com.hmdp;

import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisWorker redisWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    public void IcrIdTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(30);
        Runnable task = ()->{
            for(int i = 0;i < 100;i++){
                long id = redisWorker.nextId("test");
                System.out.println(id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for(int i = 0;i < 30;i++){
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();

        System.out.println(end - begin);


    }

}
