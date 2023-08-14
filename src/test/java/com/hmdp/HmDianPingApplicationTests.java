package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.Person;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Var;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IUserService userService;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    public void loadAllVoucher() {
        List<SeckillVoucher> list = seckillVoucherService.list();
        list.forEach(item -> {
            stringRedisTemplate.opsForValue().set("seckill:stock:" + item.getVoucherId(), item.getStock().toString());
        });
    }

    @Test
    public void loadAllLikes() {

    }

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - begin));
    }

    @Test
    void test1() {
        String uuid = UUID.randomUUID().toString();
        System.out.println(uuid);
    }

    @Test
    void method() {
        //创建锁对象
        RLock lock = redissonClient.getLock("lock");
        method1(lock);
    }

    @Test
    void method1(RLock lock) {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败，1 ");
            return;
        }
        try {
            log.info("获取锁成功，1");
            method2(lock);
        } finally {
            log.info("释放锁，1");
            lock.unlock();
        }
    }

    @Test
    void method2(RLock lock) {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败，2 ");
            return;
        }
        try {
            log.info("获取锁成功，2");
        } finally {
            log.info("释放锁，2");
            lock.unlock();
        }
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void addAllShop2Redis() {
        shopService.list().forEach(shop -> {
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
            //添加到redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shop.getId(), JSONUtil.toJsonStr(redisData));
        });
    }

    @Test
    void loadShopData() {
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按照typeId分组，id一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1.获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            //3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.3.写入reids GEOADD key 经度 维度 member
            for (Shop shop : value) {
                //stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        // 准备数组，装用户数据
        String[] users = new String[1000];
        // 数组角标
        int index = 0;
        for (int i = 1; i <= 1000000; i++) {
            // 赋值
            users[index++] = "user_" + i;
            // 每1000条发送一次
            if (i % 1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }

    //模拟多个用户并发请求
    @Test
    public void loadAllUser() throws IOException {
        String filePath = "d:\\user.txt";
        //加true表示在文本末尾续写
        //不加表示覆盖原文
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filePath, true));

        List<User> list = userService.lambdaQuery().lt(User::getId, 105).list();
        list.forEach(user -> {
            //7.1.随机生成token，作为登录令牌（不带中划线的token）
            String token = cn.hutool.core.lang.UUID.randomUUID().toString(true);
            //7.2.将User转为hash存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)//忽略空值
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())//字段编辑器，将Long型转成String型
            );
            //7.3存储
            String tokenKey = LOGIN_USER_KEY + token;

            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            //7.4设置token有效期
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
            try {
                bufferedWriter.write(token);
                bufferedWriter.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        bufferedWriter.close();
    }


    @Test
    public void saveJson2Redis(){
        Person person = new Person("吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱", "男的的的", 18);
        stringRedisTemplate.opsForValue().set("person1",JSONUtil.toJsonStr(person));
    }

    @Test
    public void saveHash2Redis(){
        Person person = new Person("吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱吱", "男的的的", 18);
        Map<String, Object> map = BeanUtil.beanToMap(person, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)//忽略空值
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())//将所有字段类型都转成string
        );
        stringRedisTemplate.opsForHash().putAll("person2",map);
    }
}

