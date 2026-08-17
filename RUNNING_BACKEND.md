# Backend Local Running Guide

This repository contains only the Spring Boot backend. The backend has been
reproduced successfully on Windows with Java 17.

## Verified Environment

- Java 17
- Maven 3.9.12
- MySQL 8.0.34
- Docker Desktop 29.5.2
- Redis on `127.0.0.1:6379`
- Kafka on `127.0.0.1:9092`
- Backend on `http://127.0.0.1:8081`

Java 17 compiled and ran the Java 8-targeted project successfully. No JDK
switch or dependency upgrade was required.

## 1. Clone

```powershell
git clone https://github.com/mi26827/Life-Platform.git D:\codexcode\life-platform
Set-Location D:\codexcode\life-platform
```

## 2. Start MySQL

Run PowerShell as Administrator when starting the registered Windows service:

```powershell
Start-Service MySQL
```

If service control is unavailable, the installed server can be launched as a
normal background process:

```powershell
Start-Process `
  -FilePath 'D:\develop\mysql-8.0.34-winx64\bin\mysqld.exe' `
  -ArgumentList '--console' `
  -WindowStyle Hidden
```

Confirm that MySQL is listening:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 3306
```

## 3. Create and Import the Database

Set the password only for the current PowerShell session:

```powershell
$env:MYSQL_PWD = '<your-mysql-root-password>'
```

The SQL dump was created for MySQL 5.6 and contains zero timestamp defaults.
MySQL 8 strict mode rejects those defaults, so use a session-only compatibility
mode during import:

```powershell
mysql -uroot -e "DROP DATABASE IF EXISTS dingping; CREATE DATABASE dingping DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysql -uroot --default-character-set=utf8mb4 dingping -e "SET SESSION sql_mode='NO_ENGINE_SUBSTITUTION'; source D:/codexcode/life-platform/src/main/resources/db/life-platform.sql;"

Remove-Item Env:MYSQL_PWD
```

Verification:

```powershell
$env:MYSQL_PWD = '<your-mysql-root-password>'
mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dingping'; SELECT COUNT(*) FROM dingping.tb_shop;"
Remove-Item Env:MYSQL_PWD
```

Expected results are 11 tables and 14 shops.

## 4. Start Redis and Kafka

Open Docker Desktop first. If the containers already exist, start them:

```powershell
docker start redis kafka
```

For a clean machine without existing containers:

```powershell
docker run -d --name dianping-redis -p 6379:6379 redis:6.2
docker run -d --name dianping-kafka -p 9092:9092 `
  -e KAFKA_CFG_NODE_ID=0 `
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker `
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 `
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 `
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT `
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@dianping-kafka:9093 `
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER `
  bitnami/kafka:3.7
```

Check service health:

```powershell
docker ps
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Redis database 15 is used to isolate this backend from unrelated cached data:

```powershell
$env:SPRING_REDIS_DATABASE = '15'
```

## 5. Configure Runtime Secrets

Do not commit real passwords. Set all credentials in the shell that launches
Maven:

```powershell
$env:SPRING_DATASOURCE_PASSWORD = '<your-mysql-root-password>'
$env:SPRING_REDIS_PASSWORD = '<your-redis-password>'
$env:SPRING_REDIS_DATABASE = '15'
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = '<your-kafka-bootstrap-servers>'
```

Spring Boot 2.3 supports these relaxed-binding environment variable overrides.

`RedissonConfig.java` was minimally adjusted to use the same
`spring.redis.host`, `spring.redis.port`, `spring.redis.password`, and
`spring.redis.database` properties as Spring Data Redis.

## 6. Compile and Run

```powershell
mvn -DskipTests compile
mvn spring-boot:run
```

Successful startup includes:

```text
Tomcat started on port(s): 8081
Created new connection ... 127.0.0.1:5672
Started MiLifePlatformApplication
```

## 7. Preheat the Shop Cache

The current `/shop/{id}` implementation uses logical expiration and does not
query MySQL when the cache is absent. Run the existing test once to preload
shop 1 into Redis:

```powershell
mvn '-Dtest=MiLifePlatformApplicationTests#testSaveShop' test
```

Run this command with the same database, Redis, and Kafka environment
variables used for the backend.

## 8. Verify

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8081

curl.exe http://127.0.0.1:8081/shop-type/list
curl.exe http://127.0.0.1:8081/shop/1
```

Verified results:

- `/shop-type/list`: HTTP 200, `success=true`, 10 shop types
- `/shop/1`: HTTP 200, `success=true`, shop ID 1

## 9. Stop

Stop the backend process listening on port 8081:

```powershell
$listener = Get-NetTCPConnection -State Listen -LocalPort 8081
Stop-Process -Id $listener.OwningProcess
```

Optionally stop Docker services:

```powershell
docker stop redis kafka
```

If MySQL was launched manually, stop its process only when no other local
project is using it:

```powershell
Get-Process mysqld
```
