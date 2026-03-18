/**
 * K6 Load Test - Index Performance Benchmark
 *
 * idx_recovery 인덱스 성능 측정 테스트
 *
 * 테스트 시나리오:
 * 1. 인덱스 있을 때 쓰기 (writeWithIndex)
 * 2. 인덱스 있을 때 읽기 (readWithIndex)
 * 3. 인덱스 없을 때 쓰기 (writeWithoutIndex) - 인덱스 DROP 후 실행
 * 4. 인덱스 없을 때 읽기 (readWithoutIndex) - 인덱스 DROP 후 실행
 *
 * 사용법:
 *   # 1단계: 인덱스 있는 상태에서 테스트
 *   k6 run --env SCENARIO=write_with_index index-benchmark.js
 *   k6 run --env SCENARIO=read_with_index index-benchmark.js
 *
 *   # 2단계: MySQL에서 인덱스 삭제
 *   # mysql> DROP INDEX idx_recovery ON payment_intent;
 *
 *   # 3단계: 인덱스 없는 상태에서 테스트
 *   k6 run --env SCENARIO=write_without_index index-benchmark.js
 *   k6 run --env SCENARIO=read_without_index index-benchmark.js
 *
 *   # 4단계: 인덱스 복구
 *   # mysql> CREATE INDEX idx_recovery ON payment_intent(status, expires_at, created_at);
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// ===== 환경 설정 =====
const QR_SERVICE_URL = __ENV.QR_SERVICE_URL || 'http://localhost:8082';
const SCENARIO = __ENV.SCENARIO || 'write_with_index';

// ===== 테스트 데이터 (test-data-config.js 기준) =====
const CUSTOMER_ID_START = 10001;
const CUSTOMER_COUNT = 1000;
const WALLET_ID_START = 40001;
const WALLET_COUNT = 1000;
const STORE_ID_START = 30001;
const STORE_COUNT = 200;

function getRandomCustomerId() {
  return CUSTOMER_ID_START + Math.floor(Math.random() * CUSTOMER_COUNT);
}

function getRandomWalletId() {
  return WALLET_ID_START + Math.floor(Math.random() * WALLET_COUNT);
}

function getRandomStoreId() {
  return STORE_ID_START + Math.floor(Math.random() * STORE_COUNT);
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// ===== 커스텀 메트릭 =====
const writeLatency = new Trend('write_latency', true);
const readLatency = new Trend('read_latency', true);
const writeSuccess = new Rate('write_success');
const readSuccess = new Rate('read_success');
const totalWrites = new Counter('total_writes');
const totalReads = new Counter('total_reads');

// ===== 시나리오 설정 =====
const scenarios = {
  // 인덱스 있을 때 쓰기 테스트
  write_with_index: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },   // 워밍업
      { duration: '30s', target: 50 },   // 부하 증가
      { duration: '1m', target: 100 },   // 최대 부하 유지
      { duration: '10s', target: 0 },    // 쿨다운
    ],
    exec: 'writeScenario',
  },

  // 인덱스 있을 때 읽기 테스트
  read_with_index: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '10s', target: 0 },
    ],
    exec: 'readScenario',
  },

  // 인덱스 없을 때 쓰기 테스트
  write_without_index: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '10s', target: 0 },
    ],
    exec: 'writeScenario',
  },

  // 인덱스 없을 때 읽기 테스트
  read_without_index: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '10s', target: 0 },
    ],
    exec: 'readScenario',
  },

  // 혼합 테스트 (쓰기 + 읽기)
  mixed: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '10s', target: 0 },
    ],
    exec: 'mixedScenario',
  },
};

export let options = {
  scenarios: {
    [SCENARIO]: scenarios[SCENARIO] || scenarios['write_with_index'],
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    write_latency: ['p(95)<300'],
    read_latency: ['p(95)<200'],
    write_success: ['rate>0.99'],
    read_success: ['rate>0.99'],
  },
};

// ===== Setup =====
export function setup() {
  console.log(`[Index Benchmark] Starting scenario: ${SCENARIO}`);
  console.log(`[Index Benchmark] QR Service URL: ${QR_SERVICE_URL}`);

  // 헬스체크
  const healthRes = http.get(`${QR_SERVICE_URL}/loadtest/benchmark/health`);
  const healthOk = check(healthRes, {
    'benchmark health check passed': (r) => r.status === 200,
  });

  if (!healthOk) {
    throw new Error('Benchmark health check failed. Make sure loadtest.backdoor.enabled=true');
  }

  console.log('[Index Benchmark] Health check passed');

  return {
    scenario: SCENARIO,
    startTime: new Date().toISOString(),
  };
}

// ===== 쓰기 시나리오 =====
export function writeScenario() {
  const headers = { 'Content-Type': 'application/json' };

  // 랜덤 상태 선택 (PENDING, UNCERTAIN 혼합)
  const statuses = ['PENDING', 'UNCERTAIN', 'PENDING', 'PENDING'];
  const status = statuses[Math.floor(Math.random() * statuses.length)];

  const payload = JSON.stringify({
    customerId: getRandomCustomerId(),
    walletId: getRandomWalletId(),
    storeId: getRandomStoreId(),
    amount: randomInt(1000, 100000),
    status: status,
  });

  const startTime = Date.now();
  const res = http.post(`${QR_SERVICE_URL}/loadtest/benchmark/write`, payload, { headers });
  const duration = Date.now() - startTime;

  writeLatency.add(duration);
  totalWrites.add(1);

  const success = check(res, {
    'write status is 200': (r) => r.status === 200,
    'write response has success': (r) => {
      try {
        return JSON.parse(r.body).success === true;
      } catch {
        return false;
      }
    },
  });

  writeSuccess.add(success ? 1 : 0);

  sleep(0.1); // 100ms 대기
}

// ===== 읽기 시나리오 =====
export function readScenario() {
  // 다양한 조회 범위 테스트
  const days = [1, 3, 7, 14][Math.floor(Math.random() * 4)];

  const startTime = Date.now();
  const res = http.get(`${QR_SERVICE_URL}/loadtest/benchmark/read?days=${days}`);
  const duration = Date.now() - startTime;

  readLatency.add(duration);
  totalReads.add(1);

  const success = check(res, {
    'read status is 200': (r) => r.status === 200,
    'read response has count': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.success === true && typeof body.count === 'number';
      } catch {
        return false;
      }
    },
  });

  readSuccess.add(success ? 1 : 0);

  sleep(0.05); // 50ms 대기
}

// ===== 혼합 시나리오 =====
export function mixedScenario() {
  // 70% 읽기, 30% 쓰기 비율
  if (Math.random() < 0.3) {
    writeScenario();
  } else {
    readScenario();
  }
}

// ===== Teardown =====
export function teardown(data) {
  console.log(`[Index Benchmark] Completed scenario: ${data.scenario}`);
  console.log(`[Index Benchmark] Started at: ${data.startTime}`);
  console.log(`[Index Benchmark] Ended at: ${new Date().toISOString()}`);
}

export default function () {
  if (SCENARIO.includes('write')) {
    writeScenario();
  } else if (SCENARIO.includes('read')) {
    readScenario();
  } else {
    mixedScenario();
  }
}
