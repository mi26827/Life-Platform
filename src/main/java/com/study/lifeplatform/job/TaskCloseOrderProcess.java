package com.study.lifeplatform.job;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.VoucherOrder;
import com.study.lifeplatform.service.ISeckillVoucherService;
import com.study.lifeplatform.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static com.study.lifeplatform.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.study.lifeplatform.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 未支付订单自动关闭定时任务：Spring Task 每分钟扫描超时未支付订单，
 * 以状态乐观锁关闭订单并回补库存（数据库 + Redis）与秒杀资格，
 * 与订单支付接口并发时通过 CAS 保证二者仅一方成功。
 *
 * @author mi
 */
@Slf4j
@Component
public class TaskCloseOrderProcess {

    /**
     * 订单超时时间（分钟），下单后超过该时长未支付则自动关闭
     */
    private static final int ORDER_TIMEOUT_MINUTES = 15;

    /**
     * 订单状态：未支付
     */
    private static final int ORDER_STATUS_UNPAID = 1;

    /**
     * 单次扫描的最大处理条数
     */
    private static final int BATCH_SIZE = 100;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每分钟执行一次：扫描超时未支付订单并关闭、回补库存与秒杀资格。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void closeExpiredOrders() {
        log.info("未支付订单自动关闭任务开始");
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES);
        Page<VoucherOrder> page = voucherOrderService.query()
                .eq("status", ORDER_STATUS_UNPAID)
                .lt("create_time", deadline)
                .page(new Page<>(1, BATCH_SIZE));
        List<VoucherOrder> orders = page.getRecords();
        if (orders.isEmpty()) {
            log.info("未支付订单自动关闭任务结束，无超时订单");
            return;
        }
        int closedCount = 0;
        for (VoucherOrder order : orders) {
            if (closeAndRestore(order)) {
                closedCount++;
            }
        }
        log.info("未支付订单自动关闭任务结束，本次处理={}，成功关闭={}", orders.size(), closedCount);
    }

    /**
     * 关闭单笔订单并回补库存：乐观锁关单成功后才回补数据库库存、
     * Redis 库存与秒杀资格集合，保证不重复回补。
     *
     * @param order 超时未支付订单
     * @return 是否成功关闭
     */
    private boolean closeAndRestore(VoucherOrder order) {
        Result closeResult = voucherOrderService.closeOrder(order.getId());
        if (!closeResult.getSuccess()) {
            log.info("订单状态已变更，跳过关单 订单ID={}", order.getId());
            return false;
        }
        Long voucherId = order.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", voucherId)
                .update();
        stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId, 1);
        stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY + voucherId, order.getUserId().toString());
        log.info("超时订单已关闭并回补库存 订单ID={} 券ID={}", order.getId(), voucherId);
        return true;
    }
}