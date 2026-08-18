package com.study.lifeplatform.controller;


import com.study.lifeplatform.annotation.LimitType;
import com.study.lifeplatform.annotation.RateLimit;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author mi
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    /**
     * 秒杀下单：按用户维度滑动窗口限流，单用户 1 秒内最多 5 次。
     *
     * @param voucherId 优惠券id
     * @return 含订单id的结果
     */
    @RateLimit(limitType = LimitType.USER, window = 1, maxCount = 5)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 支付订单，乐观锁保证与自动关单并发安全。
     *
     * @param orderId 订单id
     * @return 支付结果
     */
    @PostMapping("/pay/{id}")
    public Result payOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }
}
