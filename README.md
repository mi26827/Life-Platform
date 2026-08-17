# MI Life Platform（本地生活服务点评平台）

一个基于 Spring Boot + Redis + MySQL 的本地生活服务点评平台，涵盖商铺查询、优惠券秒杀、社交互动（探店笔记、点赞、关注）、附近商铺、签到统计等核心业务场景。项目重点实践了 Redis 在高并发场景下的典型应用：缓存策略、分布式锁、消息队列异步下单、GEO 位置服务与 BitMap 统计。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 后端框架 | Spring Boot 2.3.12、Spring MVC、Spring AMQP |
| 数据存储 | MySQL 8.x、MyBatis-Plus 3.4.3 |
| 缓存 | Redis（Lettuce + Redisson 3.13.6） |
| 消息队列 | RabbitMQ（异步秒杀下单） |
| 工具库 | Hutool、Lombok |
| 前端部署 | Nginx（静态页面 + 反向代理） |

## 功能模块

### 短信登录与会话管理
- 基于 Redis 替代 Session，解决集群环境下的会话共享问题
- 双层拦截器设计：第一层刷新 Token 有效期并构建 ThreadLocal 用户上下文，第二层做登录校验

### 商铺查询缓存
- Cache Aside 模式保证数据库与缓存一致性
- 缓存穿透：空值缓存；缓存雪崩：随机过期时间；缓存击穿：互斥锁与逻辑过期两种方案
- 缓存预热工具与封装的通用缓存客户端（`CacheClient`）

### 优惠券秒杀
- Redis + Lua 脚本原子完成库存判断、一人一单预检与扣减
- 全局唯一订单 ID 生成（Redis 自增 + 时间戳）
- RabbitMQ 异步下单，正常队列 + 死信队列兜底
- 乐观锁（stock > 0 条件更新）防止超卖

### 社交互动
- 探店笔记发布与图片上传
- 点赞排行榜：ZSet 按时间戳排序
- 关注与共同关注：Set 交集运算
- 好友动态 Feed 流：推模式（收件箱）

### 位置与统计
- 附近商铺：Redis GEO 按坐标范围检索并按距离排序
- 用户签到与连续签到统计：BitMap 位图
- UV 统计：HyperLogLog

## 目录结构

```text
src/main/java/com/study/lifeplatform
├── config          # Redis、Redisson、RabbitMQ、MVC 等配置
├── controller      # REST 接口层
├── dto             # 数据传输对象与统一返回 Result
├── entity          # 数据库实体
├── interceptor     # 登录拦截器（Token 刷新 + 登录校验）
├── listener        # MQ 消费者（异步秒杀下单）
├── mapper          # MyBatis-Plus Mapper
├── service         # 业务接口与实现
└── utils           # 缓存客户端、分布式锁、ID 生成器、登录上下文等
```

## 快速开始

### 1. 环境准备

- JDK 8+（Java 17 亦可运行）、Maven 3.6+
- Docker 与 Docker Compose（推荐）；或本机手动安装 MySQL 8.x、Redis、RabbitMQ
- Windows 环境可参考 [RUNNING_BACKEND.md](RUNNING_BACKEND.md) 的完整步骤

### 2. 一键启动中间件（Docker Compose）

仓库根目录已提供 `docker-compose.yml`，包含 MySQL 8、Redis 7、RabbitMQ 3（含管理台 http://localhost:15672，账号 guest/guest）：

```bash
docker compose up -d

# 首次启动需导入初始化数据（macOS/Linux）
./scripts/init-data.sh

# Windows PowerShell 手动导入
docker exec -i milife-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 dingping < src/main/resources/db/life-platform.sql
```

若使用本机自建的中间件，可跳过此步，自行执行 `src/main/resources/db/life-platform.sql`。

### 3. 配置说明

`application.yaml` 所有连接信息均为本地默认值（MySQL `root/123456`、Redis 本机、RabbitMQ `guest/guest`），与 Docker Compose 服务一致，**克隆后无需修改即可启动**。

如需连接其他环境，通过环境变量覆盖：

| 环境变量 | 默认值 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `127.0.0.1` / `3306` / `dingping` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `root` / `123456` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` |

生产环境务必通过环境变量注入密码，不要提交真实凭证。

### 4. 启动后端

```bash
mvn spring-boot:run
```

后端默认监听 `http://127.0.0.1:8081`。

### 5. 启动前端

```bash
cd frontend/nginx-1.18.0
./nginx.exe   # Windows；macOS/Linux 使用 ./nginx
```

前端页面默认由 Nginx 托管，配置见 `frontend/nginx-1.18.0/conf/nginx.conf`。

### 6. 缓存预热（可选）

商铺详情查询采用逻辑过期策略，首次使用前需预热缓存：

```bash
mvn '-Dtest=MiLifePlatformApplicationTests#testSaveShop' test
```

## 说明

- 本项目为个人学习与实践项目，用于深入理解 Redis 在高并发业务中的落地方式。