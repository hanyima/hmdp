package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private BlockingQueue bq =new LinkedBlockingQueue();
    ExecutorService createOrderThreadPool = Executors.newFixedThreadPool(1);

    private static DefaultRedisScript<Long> redisScript;
    static {
        redisScript = new DefaultRedisScript();
        redisScript.setLocation(new PathResource("seckill.lua"));
        redisScript.setResultType(Long.class);
    }

    @PostConstruct
    private void init(){
        IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
        createOrderThreadPool.submit(()->{
            while(true){
                proxy.createSeckillOrder();
            }
        });
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        String key1 = RedisConstants.CACHE_SECKILL_VOUCHER_KEY + voucherId;
        String key2 = RedisConstants.CACHE_SECKILL_ORDER_KEY + voucherId;
        // 1.执行lua脚本，进行秒杀资格判断
        Long res = stringRedisTemplate.execute(redisScript, List.of(key1, key2), UserHolder.getUser().getId());

        //2.判断是否下单成功
        if(res.intValue()!=0){
            return Result.fail("下单失败");
        }
        //3.下单成功 创建订单(阻塞队列)
        long orderId = redisWorker.nextId("seckill_order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        bq.add(voucherOrder);
        return Result.ok(orderId);
    }

    @Transactional
    public VoucherOrder createSeckillOrder() {
        VoucherOrder order = null;
        try {
            order = (VoucherOrder) bq.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        //4.判断该用户是否购买过
        List list = query().eq("voucher_id", order.getVoucherId())
                .eq("user_id", order.getUserId())
                .list();
        if (!CollectionUtil.isEmpty(list)){
            log.info("无法重复购买");
            return null;
        }
        //5.扣减库存（如果足够）
        boolean flag = iSeckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!flag){
            log.info("库存不足");
            return null;
        }

        //6.创建订单
        save(order);
        return order ;
    }

//   ##旧版本实现
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷信息
//        SeckillVoucher seckillVoucher = iSeckillVoucherService.query().eq("voucher_id", voucherId).list().get(0);
//
//        //2.判断是否在活动期间
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        LocalDateTime now = LocalDateTime.now();
//        if(now.isAfter(endTime)||now.isBefore(beginTime)){
//            log.info("秒杀券不在活动时间");
//            return Result.fail("秒杀券不在活动时间");
//        }
//
//        //3.判断库存是否充足
//        if (seckillVoucher.getStock()<=0){
//            log.info("优惠券{}库存不足",seckillVoucher);
//            return Result.fail("优惠券库存不足");
//        }
//        VoucherOrder voucherOrder = null;
//        Long userId = UserHolder.getUser().getId();
//
//        IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//
//        synchronized (userId.toString().intern()){
//            voucherOrder = proxy.createSeckillOrder(voucherId,userId);
//        }
//        if(voucherOrder==null){
//            return Result.fail("订单创建失败");
//        }
//        log.info("订单创建成功{}",seckillVoucher);
//        return Result.ok();
//
//    }
}
