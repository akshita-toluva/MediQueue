package com.mediqueue.dsaLayer;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HashMapDoctorCache {

    private static final String KEY_PREFIX="doctor:availability:7";

    private final Map<Long,Boolean> localCache=new ConcurrentHashMap<>();
    private final RedisTemplate<String,Boolean> redisTemplate;
    private final Duration ttl;

    public HashMapDoctorCache(RedisTemplate<String,Boolean> doctorAvailabilityRedisTemplate,
    @Value("${mediqueue.cache.doctor-availability-ttl-seconds}") long ttlSeconds)
    {
        this.redisTemplate=doctorAvailabilityRedisTemplate;
        this.ttl=Duration.ofSeconds(ttlSeconds);
    }

    public Boolean get(Long doctorId)
    {
        Boolean local=localCache.get(doctorId);
        if(local!=null)
        {
            return local;
        }
        Boolean fromRedis=redisTemplate.opsForValue().get(redisKey(doctorId));
        if(fromRedis!=null)
        {
            localCache.put(doctorId,fromRedis);
        }
        return fromRedis;
    }

    public void put(Long doctorId,boolean available)
    {
        localCache.put(doctorId,available);
        redisTemplate.opsForValue().set(redisKey(doctorId),available,ttl);
    }

    public void evict(Long doctorId)
    {
        localCache.remove(doctorId);
        redisTemplate.delete(redisKey(doctorId));
    }

    private String redisKey(Long doctorId)
    {
        return KEY_PREFIX + doctorId;
    }
}
