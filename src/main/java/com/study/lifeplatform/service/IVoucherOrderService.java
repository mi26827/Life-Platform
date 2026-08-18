package com.study.lifeplatform.service;

import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author mi
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 支付订单，基于状态乐观锁（仅未支付可支付），与自动关单并发时保证二者只有一个成功。
     *
     * @param orderId 订单 id
     * @return 支付结果
     */
    Result payOrder(Long orderId);

    /**
     * 关闭订单，基于状态乐观锁（仅未支付可关闭），与支付并发冲突时返回失败。
     *
     * @param orderId 订单 id
     * @return 关单结果
     */
    Result closeOrder(Long orderId);
}
