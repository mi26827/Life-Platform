#!/bin/bash
# Kafka 初始化脚本
# 用途：
#   1. 手动创建 __consumer_offsets 内部主题（apache/kafka:3.7.0 KRaft 模式下自动创建可能卡住，
#      导致消费组无法完成入组协调，需手动创建解除阻塞）
#   2. 预创建业务主题 seckill.order 与死信主题 seckill.order.dlt
# 用法：bash scripts/init-kafka.sh
# 说明：脚本幂等，已存在的主题会创建失败并忽略，不影响后续执行

set -e

CONTAINER=${KAFKA_CONTAINER:-milife-kafka}

echo "==> 等待 Kafka broker 就绪..."
until docker exec "$CONTAINER" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; do
  sleep 2
done
echo "==> Kafka broker 已就绪"

echo "==> 创建 __consumer_offsets 内部主题（50 分区，compact 策略）..."
docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic __consumer_offsets \
  --partitions 50 \
  --replication-factor 1 \
  --config cleanup.policy=compact \
  --config segment.bytes=104857600 || echo "==> __consumer_offsets 已存在，跳过"

echo "==> 创建业务主题 seckill.order（3 分区）..."
docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic seckill.order \
  --partitions 3 \
  --replication-factor 1 || true

echo "==> 创建死信主题 seckill.order.dlt（3 分区）..."
docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic seckill.order.dlt \
  --partitions 3 \
  --replication-factor 1 || true

echo "==> 当前主题列表："
docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

echo "==> Kafka 初始化完成"