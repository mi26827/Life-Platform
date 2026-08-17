package com.study.lifeplatform.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.dto.UserDTO;
import com.study.lifeplatform.entity.Shop;
import com.study.lifeplatform.mapper.ShopMapper;
import com.study.lifeplatform.service.IShopService;
import com.study.lifeplatform.utils.CacheClient;
import com.study.lifeplatform.utils.RedisData;
import com.study.lifeplatform.utils.SystemConstants;
import com.study.lifeplatform.utils.UserHolder;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.study.lifeplatform.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.study.lifeplatform.utils.RedisConstants.SHOP_GEO_KEY;
import static com.study.lifeplatform.utils.RedisConstants.SHOP_LIKED_KEY;
import static com.study.lifeplatform.utils.RedisConstants.SHOP_RANK_KEY;
import static com.study.lifeplatform.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * 店铺服务实现类，包含店铺缓存查询与基于 GEO 的附近店铺分页查询。
 *
 * @author mi
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /**
     * 热度排行最大返回数量
     */
    private static final int MAX_TOP_N = 50;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient clientClient;

    /**
     * 根据 id 查询店铺，基于缓存穿透策略优先读缓存。
     *
     * @param id 店铺 id
     * @return 店铺数据
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = clientClient.queryWithPassThrough(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        fillLikeInfo(shop);
        return Result.ok(shop);
    }

    /**
     * 填充商铺的点赞数与当前用户点赞状态。
     *
     * @param shop 商铺数据
     */
    private void fillLikeInfo(Shop shop) {
        Double score = stringRedisTemplate.opsForZSet().score(SHOP_RANK_KEY, shop.getId().toString());
        shop.setLikes(score == null ? 0L : score.longValue());
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            shop.setIsLiked(false);
            return;
        }
        Boolean isMember = stringRedisTemplate.opsForSet()
                .isMember(SHOP_LIKED_KEY + shop.getId(), user.getId().toString());
        shop.setIsLiked(BooleanUtil.isTrue(isMember));
    }

    /**
     * 点赞或取消点赞商铺：Set 记录用户保证一人一赞，ZSet 维护热度分。
     * 已点赞时再次调用为取消点赞，热度分相应扣减。
     *
     * @param id 商铺 id
     * @return 操作结果
     */
    @Override
    public Result likeShop(Long id) {
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        Long userId = UserHolder.getUser().getId();
        String likedKey = SHOP_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(likedKey, userId.toString());
        if (BooleanUtil.isTrue(isMember)) {
            stringRedisTemplate.opsForSet().remove(likedKey, userId.toString());
            stringRedisTemplate.opsForZSet().incrementScore(SHOP_RANK_KEY, id.toString(), -1);
        } else {
            stringRedisTemplate.opsForSet().add(likedKey, userId.toString());
            stringRedisTemplate.opsForZSet().incrementScore(SHOP_RANK_KEY, id.toString(), 1);
        }
        return Result.ok();
    }

    /**
     * 查询商铺热度点赞排行 Top N：ZSet 按分数倒序取前 N，查库组装并附带点赞数与当前用户点赞状态。
     *
     * @param topN 排行数量，超过上限时按上限处理
     * @return 按点赞数降序的商铺列表
     */
    @Override
    public Result queryTopShops(Integer topN) {
        int size = topN == null || topN < 1 ? 10 : Math.min(topN, MAX_TOP_N);
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(SHOP_RANK_KEY, 0, size - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = tuples.stream()
                .filter(tuple -> tuple.getScore() != null && tuple.getScore() > 0)
                .map(tuple -> Long.valueOf(tuple.getValue()))
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Map<Long, Long> likesMap = new HashMap<>(ids.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            likesMap.put(Long.valueOf(tuple.getValue()), tuple.getScore().longValue());
        }
        List<Shop> shops = listByIds(ids);
        Map<Long, Shop> shopMap = shops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        List<Shop> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Shop shop = shopMap.get(id);
            if (shop == null) {
                continue;
            }
            shop.setLikes(likesMap.get(id));
            UserDTO user = UserHolder.getUser();
            if (user == null) {
                shop.setIsLiked(false);
            } else {
                Boolean isMember = stringRedisTemplate.opsForSet()
                        .isMember(SHOP_LIKED_KEY + id, user.getId().toString());
                shop.setIsLiked(BooleanUtil.isTrue(isMember));
            }
            result.add(shop);
        }
        return Result.ok(result);
    }

    /**
     * 将店铺数据以逻辑过期形式预热写入 Redis。
     *
     * @param id 店铺 id
     * @param expireSeconds 逻辑过期时间（秒）
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新店铺：先修改数据库再删除缓存。
     *
     * @param shop 店铺数据
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * 按店铺类型分页查询，携带坐标时基于 Redis GEO 按距离排序。
     *
     * @param typeId 类型 id
     * @param current 当前页码
     * @param x 经度
     * @param y 纬度
     * @return 店铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results == null || results.getContent().isEmpty()) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query()
                .in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}