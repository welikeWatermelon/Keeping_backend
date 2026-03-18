-- K6 Load Test Data Initialization Script
-- 테이블 생성 + 테스트 데이터 삽입
-- Target: 1000 Customers, 100 Owners, 200 Stores, 1000 Wallets, 400 Categories, 1600 Menus, 200000 WalletStoreBalances
-- 1000 VU 부하 테스트를 위한 대용량 테스트 데이터

-- =============================================
-- 데이터베이스 생성 및 선택
-- =============================================
CREATE DATABASE IF NOT EXISTS keeping
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE keeping;

-- =============================================
-- 1. CUSTOMERS 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id VARCHAR(100) NOT NULL,
    provider_type VARCHAR(20) NOT NULL,
    email VARCHAR(250) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    birth DATE NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    img_url VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uq_customers_provider (provider_type, provider_id),
    UNIQUE KEY uq_customers_email (email),
    UNIQUE KEY uq_customers_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 2. OWNERS 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS owners (
    owner_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id VARCHAR(100) NOT NULL,
    provider_type VARCHAR(20) NOT NULL,
    email VARCHAR(250) NULL,
    phone_number VARCHAR(50) NULL,
    birth DATE NULL,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NULL,
    img_url VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    phone_verified_at DATETIME NULL,
    deleted_at DATETIME NULL,
    UNIQUE KEY uq_owners_provider (provider_type, provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 3. STORES 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS stores (
    store_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    tax_id_number VARCHAR(50) NOT NULL,
    store_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NULL,
    category VARCHAR(50) NOT NULL,
    img_url VARCHAR(255) NOT NULL,
    description VARCHAR(250) NULL,
    stores_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uq_stores_tax_address (tax_id_number, address),
    KEY idx_stores_owner (owner_id),
    CONSTRAINT fk_stores_owner FOREIGN KEY (owner_id) REFERENCES owners(owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 4. WALLETS 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NULL,
    group_id BIGINT NULL,
    wallet_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_wallets_customer (customer_id),
    KEY idx_wallets_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 5. CATEGORIES 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS categories (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    category_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_categories_store (store_id),
    KEY idx_categories_parent (parent_id),
    KEY idx_cat_store_parent_order (store_id, parent_id, display_order),
    CONSTRAINT fk_categories_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 6. MENUS 테이블 생성
-- =============================================
CREATE TABLE IF NOT EXISTS menus (
    menu_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    menu_name VARCHAR(150) NOT NULL,
    price INT NOT NULL,
    description VARCHAR(500) NULL,
    sold_out TINYINT(1) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0,
    image_url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    KEY idx_menus_store (store_id),
    KEY idx_menus_category (category_id),
    KEY idx_menus_active (active),
    KEY idx_menus_soldout (sold_out),
    CONSTRAINT fk_menus_store FOREIGN KEY (store_id) REFERENCES stores(store_id),
    CONSTRAINT fk_menus_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 7. WALLET_STORE_BALANCES 테이블 생성 (결제용 잔액)
-- =============================================
CREATE TABLE IF NOT EXISTS wallet_store_balances (
    balance_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_store (wallet_id, store_id),
    KEY idx_wsb_wallet (wallet_id),
    KEY idx_wsb_store (store_id),
    CONSTRAINT fk_wsb_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id),
    CONSTRAINT fk_wsb_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 데이터 삽입 시작
-- =============================================
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. CUSTOMERS 데이터 (1000명: 10001 ~ 11000)
-- =============================================
INSERT INTO customers (customer_id, provider_id, provider_type, email, phone_number, birth, name, gender, img_url, created_at, updated_at)
SELECT
    10000 + seq AS customer_id,
    CONCAT('loadtest_customer_', 10000 + seq) AS provider_id,
    'KAKAO' AS provider_type,
    CONCAT('loadtest_customer_', 10000 + seq, '@test.com') AS email,
    CONCAT('010-1', LPAD(seq, 4, '0'), '-', LPAD(seq, 4, '0')) AS phone_number,
    DATE_ADD('1980-01-01', INTERVAL (seq MOD 30) YEAR) AS birth,
    CONCAT('테스트고객', seq) AS name,
    IF(seq MOD 2 = 0, 'MALE', 'FEMALE') AS gender,
    CONCAT('https://example.com/customer/', 10000 + seq, '.png') AS img_url,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row := @row + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t3,
         (SELECT @row := 0) t4
    LIMIT 1000
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 2. OWNERS 데이터 (100명: 20001 ~ 20100)
-- =============================================
INSERT INTO owners (owner_id, provider_id, provider_type, email, phone_number, birth, name, gender, img_url, created_at, updated_at)
SELECT
    20000 + seq AS owner_id,
    CONCAT('loadtest_owner_', 20000 + seq) AS provider_id,
    'KAKAO' AS provider_type,
    CONCAT('loadtest_owner_', 20000 + seq, '@test.com') AS email,
    CONCAT('010-2', LPAD(seq, 4, '0'), '-', LPAD(seq, 4, '0')) AS phone_number,
    DATE_ADD('1970-01-01', INTERVAL (seq MOD 25) YEAR) AS birth,
    CONCAT('테스트점주', seq) AS name,
    IF(seq MOD 2 = 0, 'MALE', 'FEMALE') AS gender,
    CONCAT('https://example.com/owner/', 20000 + seq, '.png') AS img_url,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row2 := @row2 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT @row2 := 0) t3
    LIMIT 100
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 3. STORES 데이터 (200개: 30001 ~ 30200, Owner당 2개)
-- =============================================
INSERT INTO stores (store_id, owner_id, tax_id_number, store_name, address, phone_number, category, img_url, description, stores_status, created_at, updated_at)
SELECT
    30000 + seq AS store_id,
    20001 + ((seq - 1) DIV 2) AS owner_id,
    CONCAT('100-00-', LPAD(30000 + seq, 5, '0')) AS tax_id_number,
    CONCAT('테스트매장', 30000 + seq) AS store_name,
    CONCAT('서울시 강남구 테스트로 ', seq, '길') AS address,
    CONCAT('02-3', LPAD(seq, 4, '0'), '-0001') AS phone_number,
    ELT((seq MOD 10) + 1, 'KOREAN', 'JAPANESE', 'CHINESE', 'WESTERN', 'CAFE', 'CHICKEN', 'PIZZA', 'DESSERT', 'SNACK', 'OTHER') AS category,
    CONCAT('https://example.com/store/', 30000 + seq, '.png') AS img_url,
    CONCAT('부하테스트용 매장 ', seq) AS description,
    'ACTIVE' AS stores_status,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row3 := @row3 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2) t3,
         (SELECT @row3 := 0) t4
    LIMIT 200
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 4. WALLETS 데이터 (1000개: 40001 ~ 41000, Customer당 1개)
-- =============================================
INSERT INTO wallets (wallet_id, customer_id, wallet_type, created_at, updated_at)
SELECT
    40000 + seq AS wallet_id,
    10000 + seq AS customer_id,
    'INDIVIDUAL' AS wallet_type,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row4 := @row4 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t3,
         (SELECT @row4 := 0) t4
    LIMIT 1000
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 5. CATEGORIES 데이터 (400개: 50001 ~ 50400, Store당 2개)
-- =============================================
INSERT INTO categories (category_id, store_id, parent_id, category_name, display_order, created_at, updated_at)
SELECT
    50000 + seq AS category_id,
    30001 + ((seq - 1) DIV 2) AS store_id,
    NULL AS parent_id,
    IF((seq MOD 2) = 1, '메인메뉴', '사이드메뉴') AS category_name,
    ((seq - 1) MOD 2) + 1 AS display_order,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row5 := @row5 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) t3,
         (SELECT @row5 := 0) t4
    LIMIT 400
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 6. MENUS 데이터 (1600개: 60001 ~ 61600, Category당 4개)
-- =============================================
INSERT INTO menus (menu_id, store_id, category_id, menu_name, price, description, sold_out, active, display_order, image_url, created_at, updated_at)
SELECT
    60000 + seq AS menu_id,
    30001 + ((seq - 1) DIV 8) AS store_id,
    50001 + ((seq - 1) DIV 4) AS category_id,
    CONCAT('테스트메뉴_', ((seq - 1) MOD 4) + 1) AS menu_name,
    5000 + (((seq - 1) MOD 4) + 1) * 1000 + ((seq MOD 10) * 500) AS price,
    CONCAT('맛있는 테스트메뉴 ', seq, ' 입니다.') AS description,
    0 AS sold_out,
    1 AS active,
    ((seq - 1) MOD 4) + 1 AS display_order,
    CONCAT('https://example.com/menu/', 60000 + seq, '.png') AS image_url,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row6 := @row6 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t3,
         (SELECT 1 UNION SELECT 2) t4,
         (SELECT @row6 := 0) t5
    LIMIT 1600
) numbers
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- =============================================
-- 7. WALLET_STORE_BALANCES 데이터 (200,000개: Wallet 1000개 x Store 200개)
-- 각 지갑에 모든 매장에서 사용 가능한 잔액 충전 (100만원씩)
-- =============================================
INSERT INTO wallet_store_balances (wallet_id, store_id, balance, updated_at)
SELECT
    40001 + ((seq - 1) DIV 200) AS wallet_id,
    30001 + ((seq - 1) MOD 200) AS store_id,
    1000000 AS balance,
    NOW() AS updated_at
FROM (
    SELECT @row7 := @row7 + 1 AS seq
    FROM (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t1,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t2,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t3,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t4,
         (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) t5,
         (SELECT 1 UNION SELECT 2) t6,
         (SELECT @row7 := 0) t7
    LIMIT 200000
) numbers
ON DUPLICATE KEY UPDATE balance = 1000000, updated_at = NOW();

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 검증 쿼리
-- =============================================
SELECT '========== 데이터 삽입 완료 ==========' AS message;

SELECT 'Customers' AS entity, COUNT(*) AS count FROM customers WHERE customer_id BETWEEN 10001 AND 11000
UNION ALL
SELECT 'Owners', COUNT(*) FROM owners WHERE owner_id BETWEEN 20001 AND 20100
UNION ALL
SELECT 'Stores', COUNT(*) FROM stores WHERE store_id BETWEEN 30001 AND 30200
UNION ALL
SELECT 'Wallets', COUNT(*) FROM wallets WHERE wallet_id BETWEEN 40001 AND 41000
UNION ALL
SELECT 'Categories', COUNT(*) FROM categories WHERE category_id BETWEEN 50001 AND 50400
UNION ALL
SELECT 'Menus', COUNT(*) FROM menus WHERE menu_id BETWEEN 60001 AND 61600
UNION ALL
SELECT 'WalletStoreBalances', COUNT(*) FROM wallet_store_balances WHERE wallet_id BETWEEN 40001 AND 41000;
