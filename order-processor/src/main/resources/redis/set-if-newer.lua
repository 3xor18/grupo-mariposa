local kind = redis.call('TYPE', KEYS[1])['ok']
if kind ~= 'hash' and kind ~= 'none' then
  redis.call('DEL', KEYS[1])
end
local incoming = tonumber(ARGV[1])
local current = tonumber(redis.call('HGET', KEYS[1], 'v'))
if current then
  if current > incoming then
    return 0
  end
  if current == incoming and redis.call('HEXISTS', KEYS[1], 'd') == 1 then
    return 0
  end
end
if ARGV[2] == '' then
  redis.call('HSET', KEYS[1], 'v', ARGV[1])
  redis.call('HDEL', KEYS[1], 'd')
else
  redis.call('HSET', KEYS[1], 'v', ARGV[1], 'd', ARGV[2])
end
redis.call('PEXPIRE', KEYS[1], ARGV[3])
return 1
