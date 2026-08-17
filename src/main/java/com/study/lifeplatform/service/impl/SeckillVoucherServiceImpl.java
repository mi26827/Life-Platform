package com.study.lifeplatform.service.impl;

import com.study.lifeplatform.entity.SeckillVoucher;
import com.study.lifeplatform.mapper.SeckillVoucherMapper;
import com.study.lifeplatform.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author mi
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
