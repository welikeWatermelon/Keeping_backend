# KEEPING — QR 기반 디지털 장부 선결제 서비스

> 단골 가게에서 미리 금액을 충전하고, QR 코드로 간편하게 결제하는 선결제 플랫폼입니다.

**팀 구성** : 총 6명 (백엔드 3명 / 프론트엔드 3명)

<br>

## ✨ 주요 기능

- **QR 결제** — 매장 QR 스캔 후 충전 잔액으로 즉시 결제
- **선결제 장부** — 가게별 충전 잔액 및 거래 내역 관리
- **자동 장애 복구** — 네트워크 불안정 시 데이터 정합성 자동 복구
- **실시간 알림** — SSE(Server-Sent Events) + FCM 기반 결제 완료 알림

<br>

## 🏗 아키텍처

```
                          ┌─────────────────┐
                          │     Client      │
                          └────────┬────────┘
                                   │ HTTPS
                          ┌────────▼────────┐
                          │   Free Domain   │  도메인 기반 엔드포인트 라우팅
                          └────────┬────────┘
                   ┌───────────────┴───────────────┐
                   │ /qr/**                         │ 그 외
          ┌────────▼─────────┐           ┌──────────▼────────┐
          │    QR Service    │           │    Main Server    │
          │  (결제 전용 서버)  │           │  (장부·유저·가게)  │
          └────────┬─────────┘           └──────────┬────────┘
                   │                                │
          ┌────────▼────────┐           ┌───────────▼───────┐
          │  MySQL  │ Redis │           │  MySQL  │  Redis  │
          │  (결제DB)│(캐시) │           │  (메인DB)│ (캐시) │
          └─────────┴───────┘           └──────────┴────────┘
```

**라우팅 전략**
- 공짜 도메인을 구매하여 엔드포인트 경로 기반으로 서비스를 분리
- `/qr/**` 경로 → QR Service (결제 전용)
- 그 외 경로 → Main Server (장부, 유저, 가게 관리)
- 각 서비스는 독립된 MySQL / Redis 인스턴스를 사용하여 DB 자원 경쟁 원천 차단
- **ACL(부패방지계층) 패턴** 적용으로 서비스 간 도메인 모델 오염 방지 및 배포 독립성 확보

<br>

## ⚡ 기술적 도전 & 성과

### 결제 응답 속도 86% 단축
- **문제** : 단일 DB에서 발생하는 자원 경쟁으로 인한 조회 병목 (1,923ms)
- **해결** : Webhook 기반 Push 캐싱(Event-Driven)으로 전환, Redis에 결제 상태 선캐싱
- **결과** : **1,923ms → 262ms**

### 분산 환경 데이터 정합성 보장
- **문제** : 타임아웃 발생 시 QR Service ↔ Main Server 간 데이터 불일치
- **해결** : 멱등성 키 + 상태 플래그 기반 **3단계 자동 복구 전략** 설계 (추가 인프라 비용 없음)
  1. 요청 수신 시 멱등성 키로 중복 처리 차단
  2. 타임아웃 감지 시 상태 플래그로 미완료 트랜잭션 마킹
  3. 스케줄러가 주기적으로 미완료 건을 탐색하여 자동 재처리
- **복구 탐색 최적화** : B-Tree 복합 인덱스 튜닝 → 1만 건 기준 탐색 성능 **98% 개선** (2,075ms → 24ms)

### 분산 추적 환경 구축
- **문제** : 서버 분리 이후 파편화된 로그로 서비스 간 흐름 추적 불가
- **해결** : **Micrometer Tracing** 기반 단일 TraceId로 서비스 간 End-to-End 흐름 가시화
- **결과** : 장애 원인 분석 시간 **83% 단축** (30분 → 5분 이내)

### 실시간 알림 시스템
- **SSE** : 결제 완료 즉시 점주에게 실시간 Push (앱 포그라운드)
- **FCM** : 앱 백그라운드 / 종료 상태에서도 결제 알림 수신

<br>

## 🛠 Tech Stack

| 분류 | 기술 |
|---|---|
| Backend | Java, Spring Boot |
| Database | MySQL, Redis |
| Infra | Docker, Docker Compose, GitHub Actions |
| Monitoring | Prometheus, Grafana, Micrometer Tracing |
| Load Test | k6 |
| Test | WireMock |
| Notification | SSE, FCM |

<br>

## 📁 프로젝트 구조

```
Keeping_backend/
├── gateway/              # 도메인 라우팅 설정
├── services/
│   └── qr-service/       # QR 결제 독립 서버
├── src/                  # 메인 서버 (장부·유저·가게)
├── monitoring/           # Prometheus / Grafana 설정
├── k6/                   # 부하 테스트 스크립트
├── wiremock/             # 외부 API 목킹
├── deploy/               # 배포 스크립트
├── docker-compose.yml        # 모놀리스 구성
└── docker-compose.msa.yml    # 서비스 분리 구성
```

<br>

## 🚀 실행 방법

```bash
# 환경 변수 설정
cp .env.example .env

# 모놀리스 모드
docker-compose up -d

# 서비스 분리 모드 (QR Service + Main Server)
docker-compose -f docker-compose.msa.yml up -d
```
