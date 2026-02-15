-- KEYS[1] = bucket key

-- ARGV[1] = capacity
-- ARGV[2] = refill_rate (tokens per second)
-- ARGV[3] = current time (ms)
-- ARGV[4] = requested tokens
-- ARGV[5] = ttl (ms)

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- fetch existing bucket state
local bucket = redis.call("HMGET", KEYS[1], "tokens", "last_refill")

local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- initialize bucket if first request
if tokens == nil then
	tokens = capacity
	last_refill = now
end

-- calculate time elapsed
local delta = now - last_refill
if delta < 0 then
	delta = 0
end

-- calculate refill
local refill_tokens = (delta / 1000.0) * refill_rate
tokens = math.min(capacity, tokens + refill_tokens)

local allowed = 0

if tokens >= requested then
	allowed = 1
	tokens = tokens - requested
end

redis.call("HMSET", KEYS[1], "tokens", tokens, "last_refill", now)

-- set TTL so inactive buckets disappear
redis.call("PEXPIRE", KEYS[1], ttl)

-- return { allowed, tokens }
return allowed
