local capacity = tonumber(ARGV[1])
local time_now = tonumber(ARGV[2])
local flow_rate = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local bucket = redis.call("HMGET", KEYS[1], "last_time", "queue_size")
local last_time = tonumber(bucket[1])
local queue_size = tonumber(bucket[2])

if last_time == nil then
	last_time = time_now
	queue_size = 0
end

local elapsed_time = math.max(0, (time_now - last_time))
local leaked = (elapsed_time / 1000.0) * flow_rate

queue_size = math.max(0, queue_size - leaked)

local allowed = 0
if queue_size + 1 <= capacity then
	allowed = 1
	queue_size = queue_size + 1
end

redis.call("HMSET", KEYS[1], "last_time", time_now, "queue_size", queue_size)
redis.call("PEXPIRE", KEYS[1], ttl)

-- return { allowed, queue_size }
return allowed
