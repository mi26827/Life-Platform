package com.study.lifeplatform.service;

import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author mi
 */
public interface IShopService extends IService<Shop> {
    Result queryById(Long id) throws InterruptedException;

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 点赞或取消点赞商铺，同一用户对同一商铺仅可点赞一次，再次调用视为取消。
     *
     * @param id 商铺 id
     * @return 操作结果
     */
    Result likeShop(Long id);

    /**
     * 查询商铺热度点赞排行 Top N。
     *
     * @param topN 排行数量
     * @return 按点赞数降序的商铺列表，携带点赞数与当前用户点赞状态
     */
    Result queryTopShops(Integer topN);
}
