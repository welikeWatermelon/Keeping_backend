CREATE TABLE `transactions` (
`transaction_id`	BIGINT	NOT NULL,
`wallet_id`	BIGINT	NOT NULL,
`related_wallet_id`	BIGINT	NULL,
`customer_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`transaction_type`	ENUM	NOT NULL,
`amount`	DECIMAL	NOT NULL,
`created_at`	DATETIME	NOT NULL
);

CREATE TABLE `settlement_tasks` (
`task_id`	BIGINT	NOT NULL,
`transaction_id`	BIGINT	NOT NULL,
`Status`	ENUM	NOT NULL,
`processed_at`	DATETIME	NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `menus` (
`menu_id`	BIGINT	NOT NULL,
`category_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`menu_name`	VARCHAR(150)	NOT NULL,
`price`	INT	NOT NULL,
`description`	VARCHAR(500)	NULL,
`image_url`	VARCHAR(500)	NULL,
`sold_out`	TINYINT(1)	NOT NULL,
`active`	TINYINT(1)	NOT NULL,
`display_order`	INT	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL,
`deleted_at`	DATETIME	NULL
);

CREATE TABLE `qr_token` (
`qr_token_id`	BINARY(16)	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`mode_id`	ENUM	NOT NULL,
`bind_store_id`	BIGINT	NOT NULL,
`expires_at`	DATETIME(3)	NOT NULL,
`state`	ENUM	NOT NULL,
`created_at`	TIMESTAMP	NOT NULL,
`consumed_at`	DATETIME(3)	NULL
);

CREATE TABLE `categories` (
`category_id`	BIGINT	NOT NULL,
`parent_id`	BIGINT	NULL,
`store_id`	BIGINT	NOT NULL,
`name`	VARCHAR(100)	NOT NULL,
`display_order`	INT	NOT NULL,
`createed_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `transaction_items` (
`item_id`	BIGINT	NOT NULL,
`transaction_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`menu_id`	BIGINT	NULL,
`menu_name_snapshot`	VARCHAR(150)	NOT NULL,
`menu_price_snapshot`	INT	NOT NULL,
`quantity`	INT	NOT NULL,
`line_total`	INT	NOT NULL,
`created_at`	DATETIME	NOT NULL
);

CREATE TABLE `group_add_requests` (
`group_add_requests_id`	BIGINT	NOT NULL,
`group_id`	BIGINT	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`status`	ENUM	NOT NULL,
`create_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallets` (
`wallet_id`	BIGINT	NOT NULL,
`customer_id`	BIGINT	NULL,
`group_id`	BIGINT	NULL,
`wallet_type`	ENUM	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `stores` (
`store_id`	BIGINT	NOT NULL,
`owner_id`	BIGINT	NOT NULL,
`tax_id_number`	VARCHAR(50)	NOT NULL,
`adress`	VARCHAR(100)	NOT NULL,
`store_name`	VARCHAR(100)	NOT NULL,
`phone_number`	VARCHAR(100)	NULL,
`business_sector`	VARCHAR(100)	NOT NULL,
`business_type`	VARCHAR(100)	NOT NULL,
`merchant_id`	BIGINT	NOT NULL,
`img_url`	VARCHAR(200)	NULL,
`category`	VARCHAR(50)	NOT NULL,
`bank_account`	VARCHAR(100)	NOT NULL,
`updated_at`	DATETIME	NOT NULL,
`created_at`	DATETIME	NOT NULL
);

CREATE TABLE `wallet_store_lot` (
`lot_id`	BIGINT	NOT NULL,
`wallet_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`amount_total`	DECIMAL	NOT NULL,
`amount_remaining`	DECIMAL	NOT NULL,
`acquierd_at`	DATETIME	NOT NULL,
`expired_at`	DATETIME	NOT NULL,
`source_type`	ENUM	NOT NULL,
`contributor_wallet_id`	BIGINT	NULL,
`origin_charge_tx_id`	BIGINT	NOT NULL
);

CREATE TABLE `payment_intent` (
`intent_id`	BIGINT	NOT NULL,
`public_id`	VARCHAR(64)	NOT NULL,
`qr_token_id`	BINARY(16)	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`amount`	DECIMAL(18, 2)	NOT NULL,
`status`	ENUM	NOT NULL,
`create_idempotency_key`	VARCHAR(64)	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL,
`expires_at`	DATETIME	NOT NULL,
`approved_at`	DATETIME	NULL,
`rejected_at`	DATETIME	NULL,
`canceled_at`	DATETIME	NULL,
`succeeded_at`	DATETIME	NULL
);

CREATE TABLE `wallet_store_balance` (
`balance_id`	BIGINT	NOT NULL,
`wallet_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`balance`	DECIMAL	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `Notifications` (
`notification_id`	BIGINT	NOT NULL,
`TYPE`	ENUM	NULL,
`Field`	VARCHAR(255)	NULL
);

CREATE TABLE `group_members` (
`group_member_id`	BIGINT	NOT NULL,
`group_id`	BIGINT	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`isLeader`	Boolean	NOT NULL,
`created_at`	VARCHAR(255)	NOT NULL,
`updated_at`	VARCHAR(255)	NOT NULL
);

CREATE TABLE `group` (
`group_id`	BIGINT	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`group_name`	VARCHAR(100)	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL
);

CREATE TABLE `owners` (
`owner_id`	BIGINT	NOT NULL,
`provider_id`	VARCHAR(100)	NOT NULL,
`provider_type`	ENUM	NOT NULL,
`email`	VARCHAR(250)	NOT NULL,
`phone_number`	VARCHAR(50)	NOT NULL,
`birth`	DATE	NOT NULL,
`name`	VARCHAR(50)	NOT NULL,
`gender`	ENUM	NOT NULL,
`img_url`	VARCHAR(200)	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL,
`phone_verified_at`	DATETIME	NULL,
`deleted_at`	DATETIME	NULL
);

CREATE TABLE `store_favorite` (
`favorite_id`	BIGINT	NOT NULL,
`customer_id`	BIGINT	NOT NULL,
`store_id`	BIGINT	NOT NULL,
`active`	TINYINT(1)	NOT NULL,
`favorited_at`	DATETIME	NOT NULL,
`unfavorited_at`	DATETIME	NULL
);

CREATE TABLE `idempotency_keys` (
`id`	BIGINT	NOT NULL,
`key_uuid`	BINARY(16)	NOT NULL,
`actor_type`	ENUM('MERCHANT','CUSTOMER','SYSTEM')	NOT NULL,
`actor_id`	BIGINT	NOT NULL,
`method`	VARCHAR(10)	NOT NULL,
`path`	VARCHAR(255)	NOT NULL,
`body_hash`	VARBINARY(32)	NOT NULL,
`status`	ENUM('IN_PROGRESS','DONE')	NOT NULL,
`response_json`	JSON	NULL,
`created_at`	TIMESTAMP	NOT NULL
);

CREATE TABLE `customers` (
`customer_id`	BIGINT	NOT NULL,
`provider_id`	VARCHAR(100)	NOT NULL,
`provider_type`	ENUM	NOT NULL,
`email`	VARCHAR(250)	NOT NULL,
`phone_number`	VARCHAR(50)	NOT NULL,
`birth`	DATE	NOT NULL,
`name`	VARCHAR(50)	NOT NULL,
`gender`	ENUM	NOT NULL,
`img_url`	VARCHAR(200)	NOT NULL,
`created_at`	DATETIME	NOT NULL,
`updated_at`	DATETIME	NOT NULL,
`phone_verified_at`	DATETIME	NULL,
`deleted_at`	DATETIME	NULL
);

ALTER TABLE `transactions` ADD CONSTRAINT `PK_TRANSACTIONS` PRIMARY KEY (
`transaction_id`
);

ALTER TABLE `settlement_tasks` ADD CONSTRAINT `PK_SETTLEMENT_TASKS` PRIMARY KEY (
`task_id`
);

ALTER TABLE `menus` ADD CONSTRAINT `PK_MENUS` PRIMARY KEY (
`menu_id`
);

ALTER TABLE `qr_token` ADD CONSTRAINT `PK_QR_TOKEN` PRIMARY KEY (
`qr_token_id`
);

ALTER TABLE `categories` ADD CONSTRAINT `PK_CATEGORIES` PRIMARY KEY (
`category_id`
);

ALTER TABLE `transaction_items` ADD CONSTRAINT `PK_TRANSACTION_ITEMS` PRIMARY KEY (
`item_id`
);

ALTER TABLE `group_add_requests` ADD CONSTRAINT `PK_GROUP_ADD_REQUESTS` PRIMARY KEY (
`group_add_requests_id`
);

ALTER TABLE `wallets` ADD CONSTRAINT `PK_WALLETS` PRIMARY KEY (
`wallet_id`
);

ALTER TABLE `stores` ADD CONSTRAINT `PK_STORES` PRIMARY KEY (
`store_id`
);

ALTER TABLE `wallet_store_lot` ADD CONSTRAINT `PK_WALLET_STORE_LOT` PRIMARY KEY (
`lot_id`
);

ALTER TABLE `payment_intent` ADD CONSTRAINT `PK_PAYMENT_INTENT` PRIMARY KEY (
`intent_id`
);

ALTER TABLE `wallet_store_balance` ADD CONSTRAINT `PK_WALLET_STORE_BALANCE` PRIMARY KEY (
`balance_id`
);

ALTER TABLE `group_members` ADD CONSTRAINT `PK_GROUP_MEMBERS` PRIMARY KEY (
`group_member_id`
);

ALTER TABLE `group` ADD CONSTRAINT `PK_GROUP` PRIMARY KEY (
`group_id`
);

ALTER TABLE `owners` ADD CONSTRAINT `PK_OWNERS` PRIMARY KEY (
`owner_id`
);

ALTER TABLE `store_favorite` ADD CONSTRAINT `PK_STORE_FAVORITE` PRIMARY KEY (
`favorite_id`
);

ALTER TABLE `idempotency_keys` ADD CONSTRAINT `PK_IDEMPOTENCY_KEYS` PRIMARY KEY (
`id`
);

ALTER TABLE `customers` ADD CONSTRAINT `PK_CUSTOMERS` PRIMARY KEY (
`customer_id`
);

