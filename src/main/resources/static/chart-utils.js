(function (window) {
    'use strict';

    function formatNumber(value, digits = 2) {
        const numeric = Number(value);
        return Number.isFinite(numeric) ? numeric.toFixed(digits) : '—';
    }

    function formatSignedNumber(value, digits = 2) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return '—';
        }
        return `${numeric > 0 ? '+' : ''}${numeric.toFixed(digits)}`;
    }

    function formatSignedPercent(value, digits = 2) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return '—';
        }
        return `${numeric > 0 ? '+' : ''}${numeric.toFixed(digits)}%`;
    }

    function formatVolume(value) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return '—';
        }
        return new Intl.NumberFormat('zh-TW', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2
        }).format(Number(numeric.toFixed(2)));
    }

    function formatCompactNumber(value) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return '--';
        }
        if (Math.abs(numeric) >= 100000000) {
            return `${(numeric / 100000000).toFixed(1)}億`;
        }
        if (Math.abs(numeric) >= 10000) {
            return `${(numeric / 10000).toFixed(1)}萬`;
        }
        return Math.round(numeric).toLocaleString('zh-TW');
    }

    function parseTradingDate(value) {
        if (window.luxon?.DateTime) {
            return window.luxon.DateTime.fromISO(value, { zone: 'local' });
        }

        const date = new Date(value);
        return {
            isValid: !Number.isNaN(date.getTime()),
            valueOf: () => date.getTime(),
            toISODate: () => date.toISOString().slice(0, 10),
            startOf: unit => {
                const copy = new Date(date.getTime());
                if (unit === 'month') {
                    copy.setDate(1);
                } else if (unit === 'week') {
                    const day = copy.getDay() || 7;
                    copy.setDate(copy.getDate() - day + 1);
                }
                return {
                    toISODate: () => copy.toISOString().slice(0, 10)
                };
            }
        };
    }

    function isValidPrice(value) {
        const numeric = Number(value);
        return Number.isFinite(numeric) && numeric > 0;
    }

    function toPriceNumber(row) {
        const value = Number(row?.close ?? row?.Close ?? row?.price ?? row?.c);
        return Number.isFinite(value) ? value : NaN;
    }

    function toVolumeNumber(row) {
        const value = Number(row?.volume ?? row?.Volume ?? row?.tradingVolume ?? row?.v ?? 0);
        return Number.isFinite(value) ? Math.max(0, value) : 0;
    }

    function toDateLabel(row) {
        const raw = String(row?.date ?? row?.tradeDate ?? row?.tradingDate ?? row?.t ?? row?.x ?? '');
        return raw.includes('-') ? raw.slice(5) : raw;
    }

    function normalizeTradingData(rawData) {
        return (Array.isArray(rawData) ? rawData : [])
            .map(item => {
                const isoDate = item.t || item.date || item.tradeDate || item.tradingDate;
                const dt = parseTradingDate(isoDate);
                if (!dt.isValid) {
                    return null;
                }

                const closeCandidate = item.c ?? item.close ?? item.price;
                if (!isValidPrice(closeCandidate)) {
                    return null;
                }

                const close = Number(closeCandidate);
                const open = isValidPrice(item.o ?? item.open) ? Number(item.o ?? item.open) : close;
                let high = isValidPrice(item.h ?? item.high) ? Number(item.h ?? item.high) : Math.max(open, close);
                let low = isValidPrice(item.l ?? item.low) ? Number(item.l ?? item.low) : Math.min(open, close);

                high = Math.max(high, open, close);
                low = Math.min(low, open, close);

                return {
                    dt,
                    startDt: dt,
                    endDt: dt,
                    x: dt.valueOf(),
                    t: dt.toISODate(),
                    date: dt.toISODate(),
                    o: open,
                    h: high,
                    l: low,
                    c: close,
                    price: close,
                    volume: toVolumeNumber(item)
                };
            })
            // 排除無成交量的日期，避免停牌、補值或非交易日形成空白價量柱。
            .filter(item => item && Number(item.volume) > 0)
            .sort((a, b) => a.x - b.x);
    }

    function aggregateTradingData(rawData, timeframe) {
        const data = normalizeTradingData(rawData);
        if (timeframe === 'day') {
            return data;
        }

        const bucketUnit = timeframe === 'week' ? 'week' : 'month';
        const aggregated = [];
        let currentBucket = null;

        for (const item of data) {
            const bucketStart = item.dt.startOf(bucketUnit);
            const bucketKey = bucketStart.toISODate();

            if (!currentBucket || currentBucket.bucketKey !== bucketKey) {
                currentBucket = {
                    bucketKey,
                    dt: item.dt,
                    startDt: item.dt,
                    endDt: item.dt,
                    x: item.x,
                    t: item.date,
                    date: item.date,
                    o: item.o,
                    h: item.h,
                    l: item.l,
                    c: item.c,
                    price: item.c,
                    volume: Number(item.volume ?? 0)
                };
                aggregated.push(currentBucket);
                continue;
            }

            currentBucket.dt = item.dt;
            currentBucket.endDt = item.dt;
            currentBucket.x = item.x;
            currentBucket.t = item.date;
            currentBucket.date = item.date;
            currentBucket.h = Math.max(currentBucket.h, item.h);
            currentBucket.l = Math.min(currentBucket.l, item.l);
            currentBucket.c = item.c;
            currentBucket.price = item.c;
            currentBucket.volume += Number(item.volume ?? 0);
        }

        return aggregated;
    }

    function buildAverageSeries(rawData, period, selector) {
        const result = [];
        const values = [];
        let sum = 0;

        for (const item of rawData) {
            const value = Number(selector(item));
            const x = Number(item.x ?? parseTradingDate(item.t || item.date).valueOf());
            values.push(value);
            sum += value;

            if (values.length > period) {
                sum -= values.shift();
            }

            result.push({
                x,
                y: values.length === period ? Number((sum / period).toFixed(2)) : null
            });
        }

        return result;
    }

    function buildMovingAverageSeries(rawData, period) {
        return buildAverageSeries(rawData, period, item => Number(item.c ?? item.price ?? 0));
    }

    function buildSeriesLookup(series) {
        const lookup = new Map();
        for (const point of series) {
            lookup.set(Number(point.x), point.y);
        }
        return lookup;
    }

    function buildPriceLookup(series) {
        const lookup = new Map();
        for (const item of series) {
            lookup.set(Number(item.x), item);
        }
        return lookup;
    }

    window.StockChartUtils = {
        formatNumber,
        formatSignedNumber,
        formatSignedPercent,
        formatVolume,
        formatCompactNumber,
        parseTradingDate,
        isValidPrice,
        toPriceNumber,
        toVolumeNumber,
        toDateLabel,
        normalizeTradingData,
        aggregateTradingData,
        buildAverageSeries,
        buildMovingAverageSeries,
        buildSeriesLookup,
        buildPriceLookup
    };
})(window);
