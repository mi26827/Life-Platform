package com.study.lifeplatform.service;

import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author mi
 */
public interface IShopTypeService extends IService<ShopType> {

    Result querySort();
}
