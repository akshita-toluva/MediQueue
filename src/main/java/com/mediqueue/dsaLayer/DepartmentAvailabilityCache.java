package com.mediqueue.dsaLayer;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DepartmentAvailabilityCache {

    public static final String KEY_PREFIX="doctors:available:";
    public static final String ALL_KEY=KEY_PREFIX+"ALL";

    private final StringRedisTemplate redisTemplate;

    public DepartmentAvailabilityCache(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate=redisTemplate;
    }

    public  void markAvailable(String department,Long doctorId)
    {
        String member=doctorId.toString();
        redisTemplate.opsForSet().add(key(department), member);
        redisTemplate.opsForSet().add(ALL_KEY,member);
    }

    public void markUnavailable(String department, Long doctorId) {
        String member = doctorId.toString();
        redisTemplate.opsForSet().remove(key(department), member);
        redisTemplate.opsForSet().remove(ALL_KEY, member);
    }

    public List<Long> getAvailableDoctorIds(String department) {
        return idsFromKey(key(department));
    }

    public List<Long> getAllAvailableDoctorIds() {
        return idsFromKey(ALL_KEY);
    }

    private List<Long> idsFromKey(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    private String key(String department) {
        return KEY_PREFIX + department;
    }


}
