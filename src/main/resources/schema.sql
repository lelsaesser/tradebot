-- finnhub_price_quotes: Historical Finnhub price quotes
CREATE TABLE IF NOT EXISTS finnhub_price_quotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    current_price REAL NOT NULL,
    daily_open REAL,
    daily_high REAL,
    daily_low REAL,
    change_amount REAL,
    change_percent REAL,
    previous_close REAL,
    UNIQUE(symbol, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_finnhub_price_quotes_symbol
    ON finnhub_price_quotes(symbol);

CREATE INDEX IF NOT EXISTS idx_finnhub_price_quotes_timestamp
    ON finnhub_price_quotes(timestamp);

CREATE INDEX IF NOT EXISTS idx_finnhub_price_quotes_symbol_timestamp
    ON finnhub_price_quotes(symbol, timestamp);

-- twelvedata_daily_ohlcv: Twelve Data daily OHLCV data
CREATE TABLE IF NOT EXISTS twelvedata_daily_ohlcv (
    symbol TEXT NOT NULL,
    date TEXT NOT NULL,
    open REAL NOT NULL,
    high REAL NOT NULL,
    low REAL NOT NULL,
    close REAL NOT NULL,
    volume INTEGER NOT NULL,
    UNIQUE(symbol, date)
);

CREATE INDEX IF NOT EXISTS idx_twelvedata_daily_ohlcv_symbol
    ON twelvedata_daily_ohlcv(symbol);

CREATE INDEX IF NOT EXISTS idx_twelvedata_daily_ohlcv_symbol_date
    ON twelvedata_daily_ohlcv(symbol, date);

-- momentum_roc_state: Momentum ROC state for crossover detection
CREATE TABLE IF NOT EXISTS momentum_roc_state (
    symbol TEXT PRIMARY KEY,
    previous_roc10 REAL NOT NULL,
    previous_roc20 REAL NOT NULL,
    initialized INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);

-- ignored_symbols: Per-symbol alert suppression with reason and TTL
CREATE TABLE IF NOT EXISTS ignored_symbols (
    symbol          TEXT    NOT NULL,
    reason          TEXT    NOT NULL,
    ignored_at      INTEGER NOT NULL,
    alert_threshold INTEGER,
    PRIMARY KEY (symbol, reason)
);
