#!/usr/bin/env sh
set -eu

DATA_DIR="${STOCKPREDICTOR_DATA_DIR:-/data}"
DB_FILE="$DATA_DIR/stockdb.mv.db"
SEED_FILE="/opt/stockpredictor-seed/stockdb.mv.db"

mkdir -p "$DATA_DIR"

JDBC_URL="${STOCKPREDICTOR_JDBC_URL:-jdbc:h2:file:$DATA_DIR/stockdb}"

if [ "${JDBC_URL#jdbc:h2:}" != "$JDBC_URL" ] && [ ! -s "$DB_FILE" ] && [ -s "$SEED_FILE" ]; then
    echo "Initializing StockPredictor database from bundled seed..."
    cp "$SEED_FILE" "$DB_FILE"
fi

exec java -jar /app/app.jar
