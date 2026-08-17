package com.study.lifeplatform.service;

import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author mi
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

    /**
     * 新增普通优惠券，仅保存优惠券本身，不写秒杀表也不预热库存。
     *
     * @param voucher 优惠券信息
     */
    void saveVoucher(Voucher voucher);
}
