#!/bin/bash

set -e

cd "$(dirname "$0")/.."

echo "等待 MySQL 容器就绪..."
until docker exec milife-mysql mysqladmin ping -h localhost -uroot -p123456 --silent >/dev/null 2>&1; do
  sleep 2
done

echo "导入初始化数据..."
docker exec -i milife-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 dingping < src/main/resources/db/life-platform.sql

echo "数据导入完成"