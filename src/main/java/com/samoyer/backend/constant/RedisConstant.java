package com.samoyer.backend.constant;

/**
 * 刷题签到记录的Redis Key
 *
 * @author Samoyer
 * @since 2024-10-10
 */
public interface RedisConstant {
    /**
     * 用户签到记录的Redis Key前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX="user:signins";

    /**
     * 获取用户签到记录的Redis Key
     * @param year 年份
     * @param userId 用户id
     * @return 拼接好的Redis Key
     */
    static String getUserSignInRedisKey(int year,long userId){
        // user:signins:2024:1
        return String.format("%s:%s:%s",USER_SIGN_IN_REDIS_KEY_PREFIX,year,userId);
    }
}
