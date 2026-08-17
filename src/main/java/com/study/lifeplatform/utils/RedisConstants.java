package com.study.lifeplatform.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String SHOP_TYPE_KEY = "shop_type:";
    public static final Long SHOP_TYPE_LONG=10L;

    /**
     * 店铺优惠券列表缓存 key 前缀，完整 key 为 cache:voucher:shop:{shopId}
     */
    public static final String CACHE_VOUCHER_KEY = "cache:voucher:shop:";

    /**
     * 店铺优惠券列表缓存基础过期时间（分钟），实际过期时在此基础上叠加随机值防雪崩
     */
    public static final Long CACHE_VOUCHER_TTL = 30L;

    /**
     * 店铺无券时空值缓存的过期时间（分钟），防止缓存穿透
     */
    public static final Long CACHE_VOUCHER_NULL_TTL = 2L;

    /**
     * 商铺点赞用户集合 key 前缀，完整 key 为 shop:liked:{shopId}，成员为用户 id，实现一人一赞
     */
    public static final String SHOP_LIKED_KEY = "shop:liked:";

    /**
     * 商铺热度排行 ZSet key，member 为商铺 id，score 为点赞数
     */
    public static final String SHOP_RANK_KEY = "shop:rank";
}
