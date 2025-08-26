local key = KEYS[1]
local id = redis.call('get',key)
local thread_id = ARGV[1]

if(id==thread_id)then
    return redis.call('del',key)
end
return 0