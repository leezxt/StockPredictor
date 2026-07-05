CREATE TABLE IF NOT EXISTS SCAN_HISTORY (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    scan_date DATE NOT NULL,
    score INT NOT NULL,
    price_at_scan DOUBLE NOT NULL,
    rsi_at_scan DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS INSTITUTIONAL_DATA (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    foreign_buy BIGINT DEFAULT 0,
    trust_buy BIGINT DEFAULT 0,
    dealer_buy BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS MARKET_BREADTH (
    trade_date DATE PRIMARY KEY,
    breadth DECIMAL(5, 2) NOT NULL,
    eligible_count INT NOT NULL,
    bullish_count INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS MONTHLY_REVENUE (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    revenue DOUBLE NOT NULL,
    mom DOUBLE DEFAULT 0,
    yoy DOUBLE DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS FINMIND_SHAREHOLDING (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    holding_factor INT NOT NULL,
    shareholder_count INT NOT NULL,
    shares BIGINT NOT NULL,
    percentage DOUBLE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS IDX_INST_SYMBOL_DATE
    ON INSTITUTIONAL_DATA(symbol, trade_date);

CREATE UNIQUE INDEX IF NOT EXISTS IDX_MONTHLY_REVENUE_SYMBOL_MONTH
    ON MONTHLY_REVENUE(symbol, year_month);

CREATE UNIQUE INDEX IF NOT EXISTS IDX_SHAREHOLDING_SYMBOL_DATE_FACTOR
    ON FINMIND_SHAREHOLDING(symbol, trade_date, holding_factor);

ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS foreign_buy BIGINT;
ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS trust_buy BIGINT;
ALTER TABLE STOCK_DATA ADD COLUMN IF NOT EXISTS dealer_buy BIGINT;

-- ── 季度財報快取表 ──────────────────────────────────────────────────────────────
-- 資料來源：FinMind TaiwanStockFinancialStatements（損益表三率）
--           + TaiwanStockBalanceSheet（存貨週轉天數、合約負債）
-- FinMind 損益表三率直接欄位：GrossProfitMargin / OperatingProfitMargin / NetProfitMargin
--   → value 已為百分比（如 58.56 代表 58.56%），無需自行 ÷ Revenue 推算
CREATE TABLE IF NOT EXISTS FINANCIAL_QUARTER_DATA (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol                   VARCHAR(20)     NOT NULL,
    quarter_date             DATE            NOT NULL,  -- 季度結束日，如 2024-03-31
    gross_profit_margin      DOUBLE          DEFAULT 0, -- GrossProfitMargin (%)
    operating_profit_margin  DOUBLE          DEFAULT 0, -- OperatingProfitMargin (%)
    net_profit_margin        DOUBLE          DEFAULT 0, -- NetProfitMargin (%)
    inventory_turnover_days  INT             DEFAULT 0, -- 存貨週轉天數（由 Inventories/Revenue×91.25 推算）
    contract_liabilities     BIGINT          DEFAULT 0, -- 合約負債（元）
    created_at               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS IDX_FINANCIAL_QUARTER_SYMBOL_DATE
    ON FINANCIAL_QUARTER_DATA(symbol, quarter_date);

