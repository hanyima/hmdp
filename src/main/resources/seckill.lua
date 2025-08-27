
--1.判断库存是否充足
local stock = redis.call('get',KEYS[1])
if stock<=0 then
    return 1
end
--2.判断一人一单
if(redis.call('sismember',KEYS[2],ARGV[1])~=nil) then
    return 2
end

    --3.库存扣减
redis.call('increby',KEYS[1],-1)

    --4.保存下单用户信息
redis.call('sadd',KEYS[2],ARGV[1])

return 0