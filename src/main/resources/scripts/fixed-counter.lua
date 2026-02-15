local current = redis.call("INCR", KEYS[1])

if tonumber(current) == 1 then
	redis.call("expire", KEYS[1], ARGV[2])
end

if tonumber(current) > tonumber(ARGV[1]) then
	return 0
else
	return 1
end
