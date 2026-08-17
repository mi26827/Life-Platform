package com.study.lifeplatform.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.Voucher;
import com.study.lifeplatform.mapper.VoucherMapper;
import com.study.lifeplatform.entity.SeckillVoucher;
import com.study.lifeplatform.service.ISeckillVoucherService;
import com.study.lifeplatform.service.IVoucherService;
import com.study.lifeplatform.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.study.lifeplatform.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 优惠券服务实现类，提供店铺优惠券列表查询（带缓存）与优惠券新增能力。
 *
 * @author mi
 */
@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    /**
     * 防雪崩随机过期时间的最大附加值（分钟）
     */
    private static final long CACHE_TTL_RANDOM_BOUND = 5L;

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺的优惠券列表，采用 Cache Aside 模式：
     * 先查 Redis 缓存，未命中再查数据库并回写缓存；
     * 店铺无券时写入空值缓存防止缓存穿透，命中缓存时间叠加随机值防止缓存雪崩。
     *
     * @param shopId 店铺 id
     * @return 优惠券列表
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        String key = RedisConstants.CACHE_VOUCHER_KEY + shopId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            List<Voucher> vouchers = JSONUtil.toList(json, Voucher.class);
            return Result.ok(vouchers);
        }
        if (json != null) {
            return Result.ok(Collections.emptyList());
        }
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        if (vouchers == null || vouchers.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, "",
                    RedisConstants.CACHE_VOUCHER_NULL_TTL, TimeUnit.MINUTES);
            return Result.ok(Collections.emptyList());
        }
        long ttl = RedisConstants.CACHE_VOUCHER_TTL + RandomUtil.randomLong(0, CACHE_TTL_RANDOM_BOUND);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vouchers), ttl, TimeUnit.MINUTES);
        return Result.ok(vouchers);
    }

    /**
     * 新增普通优惠券：仅保存优惠券本身，并删除该店铺的优惠券列表缓存。
     *
     * @param voucher 优惠券信息
     */
    @Override
    @Transactional
    public void saveVoucher(Voucher voucher) {
        save(voucher);
        deleteShopVoucherCache(voucher.getShopId());
    }

    /**
     * 新增秒杀券：保存优惠券与秒杀信息，预热库存到 Redis，并删除该店铺的优惠券列表缓存。
     *
     * @param voucher 优惠券信息，包含秒杀信息
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        save(voucher);
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        deleteShopVoucherCache(voucher.getShopId());
    }

    /**
     * 删除店铺优惠券列表缓存，新增券后保证下次查询拿到最新数据。
     *
     * @param shopId 店铺 id
     */
    private void deleteShopVoucherCache(Long shopId) {
        if (shopId == null) {
            return;
        }
        stringRedisTemplate.delete(RedisConstants.CACHE_VOUCHER_KEY + shopId);
    }
}
