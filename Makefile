# 이음 로컬 인프라 명령어 모음
# 사용법: make [명령어]

.PHONY: up build rebuild down restart logs logs-app ps clean reset refresh-seoul-area build-seoul-collection-rectangles

## 전체 서비스 시작 (앱 + 인프라, 백그라운드)
up:
	docker compose up -d --remove-orphans

## 앱 이미지 빌드
build:
	docker compose build app

## 앱 이미지 재빌드 후 전체 서비스 시작
rebuild:
	docker compose up -d --build

## 전체 서비스 종료
down:
	docker compose down --remove-orphans

## 전체 재시작
restart:
	docker compose down --remove-orphans && docker compose up -d --remove-orphans

## 상태 확인
ps:
	docker compose ps

## 전체 로그 스트리밍
logs:
	docker compose logs -f

logs-app:
	docker compose logs -f app

## 서비스별 로그
logs-db:
	docker compose logs -f postgres

logs-redis:
	docker compose logs -f redis

logs-kafka:
	docker compose logs -f kafka

## 볼륨까지 완전 삭제 (DB 초기화할 때)
clean:
	docker compose down -v --remove-orphans

## Flyway migration을 갈아엎은 개발 초기 상태에서 DB까지 초기화 후 재시작
reset:
	docker compose down -v --remove-orphans && docker compose up -d --build --remove-orphans

## PostgreSQL 직접 접속
db:
	docker compose exec postgres psql -U course -d course

## seoul-service-areas.csv의 이름을 매핑 파일과 지도에 반영
refresh-seoul-area:
	node tools/seed/refresh-seoul-service-area-names.mjs
	node tools/seed/build-seoul-area-collection-rectangles.mjs

## 서울 Area별 초기 Kakao rect 수집 범위 생성
build-seoul-collection-rectangles:
	node tools/seed/build-seoul-area-collection-rectangles.mjs

## Redis CLI 접속
redis-cli:
	docker compose exec redis redis-cli

## Kafka 토픽 목록 확인
kafka-topics:
	docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

## Kafka 토픽 메시지 실시간 확인 (TOPIC= 필수)
# 사용 예: make kafka-consume TOPIC=challenge.verified
kafka-consume:
	docker compose exec kafka kafka-console-consumer \
		--bootstrap-server localhost:9092 \
		--topic $(TOPIC) \
		--from-beginning
