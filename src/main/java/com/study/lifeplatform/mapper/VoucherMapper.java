package com.study.lifeplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.lifeplatform.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author mi
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
