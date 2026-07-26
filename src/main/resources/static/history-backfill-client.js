(function (global) {
    'use strict';

    const DEFAULT_TIMEOUT_MS = 30000;
    const POLL_INTERVAL_MS = 750;
    let statusElement;

    function ensureStatusElement() {
        if (statusElement && document.body.contains(statusElement)) {
            return statusElement;
        }

        statusElement = document.createElement('div');
        statusElement.id = 'history-backfill-status';
        statusElement.setAttribute('role', 'status');
        statusElement.setAttribute('aria-live', 'polite');
        Object.assign(statusElement.style, {
            position: 'fixed',
            right: '18px',
            bottom: '18px',
            zIndex: '10050',
            maxWidth: '340px',
            padding: '10px 14px',
            borderRadius: '10px',
            background: 'rgba(15, 23, 42, .94)',
            color: '#e2e8f0',
            boxShadow: '0 10px 30px rgba(0, 0, 0, .28)',
            font: '13px/1.5 "Microsoft JhengHei", sans-serif',
            opacity: '0',
            transform: 'translateY(8px)',
            transition: 'opacity .2s ease, transform .2s ease',
            pointerEvents: 'none'
        });
        document.body.appendChild(statusElement);
        return statusElement;
    }

    function showDefaultProgress(snapshot) {
        const element = ensureStatusElement();
        const progress = Math.max(0, Math.min(100, Number(snapshot.progressPct || 0)));
        const month = snapshot.currentMonth ? `｜${snapshot.currentMonth}` : '';
        element.textContent = `${snapshot.symbol} 歷史行情回補 ${progress}%${month}｜${snapshot.message || '處理中'}`;
        element.style.opacity = '1';
        element.style.transform = 'translateY(0)';

        if (snapshot.status === 'COMPLETED' || snapshot.status === 'FAILED' || snapshot.status === 'TIMEOUT') {
            setTimeout(() => {
                element.style.opacity = '0';
                element.style.transform = 'translateY(8px)';
            }, 3500);
        }
    }

    function parseHistoryRequest(input) {
        const url = new URL(typeof input === 'string' ? input : input.url, global.location.href);
        const match = url.pathname.match(/^(.*\/api\/stocks\/)([^/]+)\/(history|full-history)$/);
        if (!match) {
            return null;
        }
        return {
            url,
            apiPrefix: match[1],
            symbol: decodeURIComponent(match[2]),
            fullHistory: match[3] === 'full-history',
            limit: Math.max(1, Number(url.searchParams.get('limit')) || 100)
        };
    }

    async function readJsonArray(response) {
        try {
            const value = await response.clone().json();
            return Array.isArray(value) ? value : [];
        } catch (_) {
            return [];
        }
    }

    function wait(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function fetchWithBackfill(input, init, options) {
        const request = parseHistoryRequest(input);
        const response = await global.fetch(input, init);
        if (!request || !response.ok || (init && init.method && init.method !== 'GET')) {
            return response;
        }

        const initialData = await readJsonArray(response);
        if (initialData.length >= request.limit) {
            return response;
        }

        const settings = options || {};
        const reportProgress = typeof settings.onProgress === 'function'
            ? settings.onProgress
            : showDefaultProgress;
        const deadline = Date.now() + Math.max(1000, settings.timeoutMs || DEFAULT_TIMEOUT_MS);
        const statusUrl = new URL(
            `${request.apiPrefix}${encodeURIComponent(request.symbol)}/history/backfill`,
            request.url
        );

        while (Date.now() < deadline) {
            await wait(POLL_INTERVAL_MS);
            if (init && init.signal && init.signal.aborted) {
                return response;
            }
            let statusResponse;
            try {
                statusResponse = await global.fetch(statusUrl, { cache: 'no-store' });
            } catch (_) {
                continue;
            }
            if (statusResponse.status === 404) {
                continue;
            }
            if (!statusResponse.ok) {
                return response;
            }

            const snapshot = await statusResponse.json();
            reportProgress(snapshot);
            if (snapshot.status === 'FAILED') {
                return response;
            }
            if (snapshot.status === 'COMPLETED') {
                if (init && init.signal && init.signal.aborted) {
                    return response;
                }
                return snapshot.dataReady
                    ? global.fetch(input, init)
                    : response;
            }
        }

        reportProgress({
            symbol: request.symbol,
            status: 'TIMEOUT',
            progressPct: 0,
            message: '背景回補仍在進行，可稍後重新查詢'
        });
        return global.fetch(input, init);
    }

    global.StockHistoryBackfill = Object.freeze({
        fetch: fetchWithBackfill
    });
})(window);
