package com.study.lifeplatform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.Voucher;
import com.study.lifeplatform.mapper.VoucherMapper;
import com.study.lifeplatform.entity.SeckillVoucher;
import com.study.lifeplatform.service.ISeckillVoucherService;
import com.study.lifeplatform.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.study.lifeplatform.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 优惠券服务实现类，提供店铺优惠券列表查询与秒杀券新增（预热库存）能力。
 *
 * @author mi
 */
@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺的优惠券列表。
     *
     * @param shopId 店铺 id
     * @return 优惠券列表
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀券：保存优惠券与秒杀信息，并预热库存到 Redis。
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
    }
}