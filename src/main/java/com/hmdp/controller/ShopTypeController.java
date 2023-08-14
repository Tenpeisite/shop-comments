package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
@Slf4j
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        //查缓存
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if (!CollectionUtils.isEmpty(shopTypeJson)) {
            //命中
            log.info("shopTypeJson:{}", shopTypeJson);
            List<ShopType> shopTypes = shopTypeJson.stream().map(item -> JSONUtil.toBean(item, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        //未命中，查数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        List<String> res = typeList.stream().map(item -> JSONUtil.toJsonStr(item)).collect(Collectors.toList());
        //添加缓存
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, res);
        return Result.ok(typeList);
    }
}
