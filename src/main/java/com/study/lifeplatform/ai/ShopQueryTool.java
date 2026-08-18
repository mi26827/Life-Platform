package com.study.lifeplatform.ai;

import com.study.lifeplatform.entity.Shop;
import com.study.lifeplatform.service.IShopService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商铺查询工具：智能客服 Function Calling 能力，
 * 大模型根据用户意图自动调用，按名称或类型查询平台商铺信息。
 *
 * @author mi
 */
@Slf4j
@Component
public class ShopQueryTool {

    /**
     * 单次最多返回的商铺数量
     */
    private static final int MAX_SHOP_COUNT = 5;

    @Resource
    private IShopService shopService;

    /**
     * 按名称或类型分页查询商铺，返回简要信息文本供大模型组织回答。
     *
     * @param keyword 商铺名称或类型关键词
     * @return 商铺信息列表文本
     */
    @Tool("根据商铺名称或类型关键词查询平台商铺信息，返回商铺名称、地址、人均价格")
    public String queryShop(String keyword) {
        log.info("智能客服查询商铺 关键词={}", keyword);
        List<Shop> shops = shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .last("LIMIT " + MAX_SHOP_COUNT)
                .list();
        if (shops.isEmpty()) {
            return "未查询到相关商铺";
        }
        return shops.stream()
                .map(shop -> String.format("商铺：%s，地址：%s，人均价格：%s元",
                        shop.getName(), shop.getAddress(), shop.getAvgPrice()))
                .collect(Collectors.joining("\n"));
    }
}