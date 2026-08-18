# MI Life Platform（本地生活服务平台）

> 面向高并发场景设计的生活服务平台，提供商家信息查询、秒杀优惠券及智能客服功能，并支持商家优惠信息发布与推广，提升系统性能与用户体验。

## 技术栈

`SpringBoot` + `MySQL` + `Redis` + `Lua` + `Kafka` + `Caffeine` + `LangChain4j` + `MyBatis-Plus`

---

## 一、高并发系统设计

### 1.1 秒杀防超卖与一人一单（Redis + Lua）

库存与已购用户集合前置到 Redis，将「判断库存 → 判断一人一单 → 扣减库存 → 记录用户」四步操作封装为 Lua 脚本，由 Redis 单线程原子执行，彻底避免并发下的超卖与重复下单。

**实现路径：** [VoucherOrderServiceImpl.seckillVoucher()](src/main/java/com/study/lifeplatform/service/impl/VoucherOrderServiceImpl.java:81) 加载 [seckill.lua](src/main/resources/seckill.lua) 并通过 `DefaultRedisScript` 执行：

```lua
-- seckill.lua 核心逻辑（完整见 src/main/resources/seckill.lua）
local stock = tonumber(redis.call('get', stockKey))
if (stock <= 0) then return 1 end                      -- 库存不足
if (redis.call('sismember', orderKey, userId) == 1)
    then return 2 end                                  -- 重复下单
redis.call('incrby', stockKey, -1)                     -- 扣库存
redis.call('sadd', orderKey, userId)                   -- 记录购买资格
```

**方案对比：**

| 方案 | 问题 |
| --- | --- |
| 数据库行锁（`select ... for update`） | 热点行锁竞争，DB 成为瓶颈，QPS 低 |
| JVM 锁（synchronized） | 集群多实例下失效，无法跨 JVM 互斥 |
| 分布式锁（Redisson） | 多次 Redis 网络往返，锁粒度大、吞吐受限 |
| **Redis + Lua（本项目）** | 多步判断与扣减合并为一次原子执行，单次网络往返，吞吐最高 |

全局唯一订单 ID 采用 Redis 自增 + 时间戳拼接（[RedisIdWorker.nextId()](src/main/java/com/study/lifeplatform/utils/RedisIdWorker.java)），趋势递增且避免分库分表主键冲突。

### 1.2 秒杀流程优化（Kafka 异步下单）

Lua 判定通过后不直接落库，而是将订单消息发送到 Kafka，由消费者异步完成订单落库与库存扣减，将下单接口响应时间从「DB 写入耗时」降低为「MQ 发送耗时」，显著提高系统吞吐能力。

```
用户请求 → 限流(AOP) → Lua 原子判定 → Kafka(seckill.order) → 消费者幂等落库 + 扣减库存
```

**实现路径：**

- 生产侧：[VoucherOrderServiceImpl.seckillVoucher()](src/main/java/com/study/lifeplatform/service/impl/VoucherOrderServiceImpl.java:103) 以订单 ID 为 key 发送消息，保证同订单进入同一分区有序
- 消费侧：[SeckillVoucherListener.onMessage()](src/main/java/com/study/lifeplatform/listener/SeckillVoucherListener.java:38) 按订单 ID 查库判重 + 捕获 `DuplicateKeyException` 双重幂等
- 落库扣减库存使用乐观锁兜底：`stock = stock - 1 ... where stock > 0`，防止 MQ 重复消费导致的超卖

**为什么选 Kafka 而不是 RabbitMQ：**

| 维度 | RabbitMQ | Kafka（本项目） |
| --- | --- | --- |
| 吞吐 | 万级，消息路由灵活 | 百万级，顺序写磁盘 + 零拷贝 |
| 消息语义 | AMQP，手动 ack | 分区有序 + 消费位移，天然契合按订单 ID 分区 |
| 堆积能力 | 堆积影响性能 | 磁盘持久化，可长期回溯重放 |
| 生态 | 需要额外组件 | 与大数据/流处理生态无缝衔接 |

秒杀场景消息量极大且要求高吞吐、可回溯，Kafka 更契合；若业务以复杂路由、低延迟小流量为主则 RabbitMQ 更合适。

**接口：** `POST /voucher-order/seckill/{id}`，返回下单成功的订单 ID，异步落库可稍后通过订单接口查询。

### 1.3 支付与关单并发处理（乐观锁）

订单支付与定时关单存在并发冲突：用户正在支付的订单可能恰好被关单任务关闭。采用以 `status = 1`（未支付）为条件的 CAS 更新，谁先执行成功谁生效，另一方更新行数为 0 即失败。

**实现路径：** [VoucherOrderServiceImpl.payOrder()](src/main/java/com/study/lifeplatform/service/impl/VoucherOrderServiceImpl.java:145)

```java
boolean success = update()
        .eq("id", orderId)
        .eq("status", ORDER_STATUS_UNPAID)   // CAS 条件：仅未支付可支付
        .set("status", ORDER_STATUS_PAID)
        .set("pay_time", LocalDateTime.now())
        .update();
```

[closeOrder()](src/main/java/com/study/lifeplatform/service/impl/VoucherOrderServiceImpl.java:168) 同样以 `status = 1` 为条件，支付与关单并发时仅一方更新成功。

**方案对比：** 悲观锁（`for update`）持有 DB 连接时间长、并发下易锁等待；分布式锁引入额外组件与网络开销；乐观锁无锁等待、冲突概率低（支付/关单窗口期短），仅一条 UPDATE 语句代价最小。

**接口：** `POST /voucher-order/pay/{id}`

---

## 二、缓存与性能优化

### 2.1 Caffeine + Redis 多级缓存架构

热点商铺详情查询链路为 **Caffeine 本地缓存 → Redis → MySQL**：

- **L1 Caffeine**：进程内缓存，纳秒级访问，扛住热点 Key 的大部分读流量，避免所有实例同时击穿 Redis；容量 10000、写后 5 分钟过期（短 TTL 降低脏数据窗口）
- **L2 Redis**：分布式共享缓存，含空值防穿透，毫秒级访问
- **DB**：最终兜底，查询结果同时回写两级缓存

**实现路径：** [CacheClient.queryWithMultiLevel()](src/main/java/com/study/lifeplatform/utils/CacheClient.java:52)

```java
Object local = localCache.getIfPresent(key);
if (local != null) return type.cast(local);            // L1 命中，直接返回
R r = queryWithPassThrough(keyPrefix, id, type, ...);  // L2 Redis（含防穿透）
if (r != null) localCache.put(key, r);                 // 回写 L1
```

**为什么不用纯 Redis：** 纯 Redis 下每个请求仍有网络往返，极端热点 Key 会把 Redis 单分片打满；Caffeine 在应用层消化读流量，Redis 只承接本地未命中的部分，且节点崩溃重启后可依赖 Redis 快速预热。

### 2.2 缓存击穿：逻辑过期 + 互斥锁

热点 Key 过期瞬间大量请求打到 DB 造成击穿。采用**逻辑过期**方案：缓存永不真正过期，数据体中携带逻辑过期时间；发现逻辑过期后，抢到互斥锁的线程异步重建缓存，其余请求直接返回旧数据，保证可用性。

**实现路径：** [CacheClient.queryWithLogicalExpire()](src/main/java/com/study/lifeplatform/utils/CacheClient.java:123)，互斥锁通过 `setIfAbsent` 实现，独立线程池 `CACHE_REBUILD_EXECUTOR` 异步重建。

**方案对比：** 互斥锁方案实现简单但重建期间其余线程阻塞等待；逻辑过期牺牲短暂的数据一致性换取全程无阻塞，适合秒杀商品等高价值热点数据。

### 2.3 缓存穿透：空值缓存

查询不存在的商铺时，将空字符串写入 Redis 并设置短 TTL（2 分钟），后续相同请求在缓存层直接拦截，避免恶意 ID 反复穿透到数据库。

**实现路径：** [CacheClient.queryWithPassThrough()](src/main/java/com/study/lifeplatform/utils/CacheClient.java:91) 中 `set(key, "", CACHE_NULL_TTL, MINUTES)`。相比布隆过滤器，空值缓存实现简单、无误判，且可容忍 Key 集合动态变化。

### 2.4 缓存一致性：TTL + 补偿机制

- **写路径：** 更新商铺先改 DB，再删除 Redis 缓存（Cache Aside），同时调用 [evictLocal()](src/main/java/com/study/lifeplatform/utils/CacheClient.java:73) 失效 Caffeine 本地缓存，见 [ShopServiceImpl.update()](src/main/java/com/study/lifeplatform/service/impl/ShopServiceImpl.java)
- **TTL 兜底：** 两级缓存均设置过期时间，即使删除操作失败，脏数据也会在 TTL 内自动收敛
- **秒杀链路补偿：** 定时关单回补 Redis 库存与购买资格（见第四节），保证「DB 库存 / Redis 库存 / 购买资格集合」三方最终一致

---

## 三、系统稳定性与限流

针对秒杀等瞬时高流量场景，基于 Redis + AOP 实现统一限流组件，防止系统过载、恶意刷单与无效请求穿透到核心链路。

### 3.1 滑动窗口限流

以「当前时间戳」为 score、唯一请求 ID 为 member 写入 Redis ZSet。每次请求先清理窗口外的过期成员，再统计窗口内请求数，超过阈值则拒绝。

**实现路径：** [RateLimitAspect.tryAcquire()](src/main/java/com/study/lifeplatform/aspect/RateLimitAspect.java:75)

```java
stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, now - windowMillis); // 清理窗口外
Long count = stringRedisTemplate.opsForZSet().zCard(key);                        // 窗口内计数
if (count != null && count >= maxCount) return false;                            // 超限拒绝
stringRedisTemplate.opsForZSet().add(key, memberId, now);                        // 记录本次请求
```

### 3.2 多维度限流

通过自定义注解 [RateLimit](src/main/java/com/study/lifeplatform/annotation/RateLimit.java) + [LimitType](src/main/java/com/study/lifeplatform/annotation/LimitType.java) 声明维度（`GLOBAL` / `IP` / `USER`），切面按维度拼接限流 Key，实现接口级统一接入、零侵入业务代码：

```java
@RateLimit(limitType = LimitType.USER, window = 1, maxCount = 5)  // 用户维度：1 秒最多 5 次
@PostMapping("/seckill/{voucherId}")
public Result seckillVoucher(@PathVariable("voucherId") Long voucherId, ...) { ... }
```

**限流算法对比：**

| 算法 | 特点 | 问题 |
| --- | --- | --- |
| 固定窗口计数 | 实现最简单 | 窗口边界突刺：两窗口交界处可通过 2 倍流量 |
| 漏桶 | 恒定速率流出，削峰 | 无法应对突发流量，需维护队列 |
| 令牌桶 | 允许一定突发 | 需要定时生成令牌，实现较重 |
| **滑动窗口（本项目）** | 以任意时刻向前看一个完整窗口 | 内存/成员随窗口增长，用短窗口 + 定期清理规避 |

秒杀防刷关注「任意连续时间窗口内的行为频率」，滑动窗口精确刻画该语义，且基于 Redis ZSet 实现简单、天然支持分布式多实例共享计数。

---

## 四、业务流程控制

### 4.1 Spring Task 自动关单

用户抢购成功后 15 分钟未支付，订单自动关闭并回补库存，避免库存被无效订单长期占用。

**实现路径：** [TaskCloseOrderProcess.closeExpiredOrders()](src/main/java/com/study/lifeplatform/job/TaskCloseOrderProcess.java:59)，`@Scheduled(cron = "0 * * * * ?")` 每分钟扫描超时未支付订单，分批（每批 100 条）处理：

```java
// closeAndRestore()：CAS 关单成功才回补，与支付并发时仅一方生效
Result closeResult = voucherOrderService.closeOrder(order.getId());
if (closeResult.getSuccess()) {
    voucherOrderService.restoreSeckillStock(order.getVoucherId(), order.getUserId()); // 回补 DB 库存 + Redis 库存 + 释放购买资格
}
```

**为什么选 Spring Task 而不是 XXL-Job：** 单体应用、单实例部署场景下 Spring Task 零依赖、注解即用；若未来多实例部署需防止重复扫描，可平滑迁移至分布式调度或加分布式锁。

### 4.2 MQ + 补偿机制保障订单可靠性

Kafka 异步下单链路通过「重试 + 死信 + 幂等 + 回补」四层机制保障订单消息不丢、不重、不超卖：

| 机制 | 实现 | 解决的问题 |
| --- | --- | --- |
| 消费重试 | [KafkaConsumerConfig](src/main/java/com/study/lifeplatform/config/KafkaConsumerConfig.java:53) `SeekToCurrentErrorHandler` + `FixedBackOff(1s, 3)` | 瞬时异常（DB 抖动、网络超时）自动重试 |
| 死信队列 | `DeadLetterPublishingRecoverer` 重试耗尽后投递 `seckill.order.dlt` | 毒消息隔离，避免阻塞分区，便于人工介入 |
| 消费幂等 | [SeckillVoucherListener](src/main/java/com/study/lifeplatform/listener/SeckillVoucherListener.java:38) 订单 ID 查库判重 + 唯一索引兜底 | 重试导致的重复消息不产生重复订单 |
| 关单回补 | 定时任务 CAS 关单后回补库存/资格 | 超时未支付订单的库存最终一致 |

---

## 五、智能客服

基于 LangChain4j 集成大模型，为用户提供商家信息查询、到店预约等智能问答服务。

### 5.1 多轮对话与上下文记忆

以登录用户 ID 为 `memoryId`，会话历史持久化到 Redis（TTL 24 小时），支持跨请求的多轮对话上下文。

**实现路径：** [AiConfig](src/main/java/com/study/lifeplatform/config/AiConfig.java:59) + [RedisChatMemoryStore](src/main/java/com/study/lifeplatform/ai/RedisChatMemoryStore.java)

```java
AiServices.builder(AiAssistant.class)
    .chatLanguageModel(model)
    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
        .id(memoryId).maxMessages(10)          // 滑动窗口保留最近 10 条，控制 Token 成本
        .chatMemoryStore(redisChatMemoryStore) // 历史消息序列化存 Redis
        .build())
    .tools(shopQueryTool, shopReservationTool) // 注册 Function Calling 工具
    .build();
```

### 5.2 Function Calling 业务工具

大模型通过 Function Calling 自主决定何时调用业务工具，实现「对话即操作」：

- [ShopQueryTool](src/main/java/com/study/lifeplatform/ai/ShopQueryTool.java)：`@Tool` 按名称模糊查询商家，格式化返回给模型
- [ShopReservationTool](src/main/java/com/study/lifeplatform/ai/ShopReservationTool.java)：`@Tool` 将预约信息写入 Redis List（TTL 48 小时）

### 5.3 Prompt 约束输出

[AiAssistant](src/main/java/com/study/lifeplatform/ai/AiAssistant.java) 通过 `@SystemMessage` 注入中文系统提示词，约束模型角色定位、回复语言与范围，避免超纲回答；`AiConfig` 在未配置 API Key 时降级为兜底实现，保证应用可正常启动。

**接口：** `POST /ai/chat`

```json
// 请求
{ "message": "帮我查一下有哪些咖啡店，顺便预约一家" }
// 响应：模型自动调用商家查询工具 → 生成推荐 → 调用预约工具 → 返回确认话术
{ "success": true, "data": "为您找到 3 家咖啡店：... 已为您预约 xxx 咖啡店，预约信息保留 48 小时。" }
```

**为什么选 LangChain4j 而不是裸 HTTP 调用：** 裸调用需自行处理消息编排、上下文拼接、工具调用协议（JSON Schema 往返）；LangChain4j 提供声明式 `AiServices` 代理、开箱即用的 Chat Memory 抽象与 `@Tool` 注解式 Function Calling，大幅降低集成与维护成本。

---

## 核心接口一览

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/shop/{id}` | GET | 商铺详情（Caffeine + Redis 多级缓存） |
| `/voucher/list/type/of/{typeId}` | GET | 按类型查询优惠券 |
| `/voucher-order/seckill/{voucherId}` | POST | 秒杀下单（Lua 判定 + Kafka 异步，用户维度限流 1s/5次） |
| `/voucher-order/pay/{orderId}` | POST | 订单支付（乐观锁 CAS） |
| `/ai/chat` | POST | 智能客服对话 |
| `/user/code` / `/user/login` | POST | 发送验证码 / 登录 |

---

## 快速开始

### 1. 环境准备

```bash
# 启动 MySQL / Redis / Kafka（含 zookeeper 与 kafka-ui）
docker compose up -d

# 初始化数据库（建库建表 + 种子数据）
docker exec -i mysql mysql -uroot -p123456 < src/main/resources/db/life-platform.sql

# 初始化 Kafka 主题（seckill.order 3 分区、seckill.order.dlt 死信）
bash scripts/init-kafka.sh
```

### 2. 配置环境变量

智能客服需要 OpenAI 兼容接口（未配置时自动降级，不影响启动）：

```bash
export AI_BASE_URL=https://api.openai.com/v1
export AI_API_KEY=sk-xxx
export AI_MODEL_NAME=gpt-4o-mini
```

### 3. 启动应用

```bash
mvn spring-boot:run
# 或
mvn clean package -DskipTests && java -jar target/life-platform-*.jar
```

默认地址：http://localhost:8081 。更多运行细节参见 [RUNNING_BACKEND.md](RUNNING_BACKEND.md)。
