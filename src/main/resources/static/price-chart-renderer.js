(function (window) {
    'use strict';

    function getYahooStyleVolumeColors(data) {
        return data.map((item, index) => {
            const previousClose = index > 0 ? Number(data[index - 1].c) : Number(item.o);
            const currentClose = Number(item.c);

            if (!Number.isFinite(currentClose) || !Number.isFinite(previousClose) || currentClose === previousClose) {
                return {
                    background: 'rgba(148, 163, 184, 0.48)',
                    border: 'rgba(100, 116, 139, 0.90)'
                };
            }

            return currentClose > previousClose
                ? {
                    background: 'rgba(220, 53, 69, 0.58)',
                    border: 'rgba(220, 53, 69, 0.98)'
                }
                : {
                    background: 'rgba(25, 135, 84, 0.58)',
                    border: 'rgba(25, 135, 84, 0.98)'
                };
        });
    }

    function renderPriceVolumeCharts(options) {
        const {
            Chart,
            priceCanvas,
            volumeCanvas,
            rawData,
            buildStandardHoverOptions,
            buildZoomPluginOptions,
            getActiveXFromChart,
            syncHoverBetweenCharts,
            clearSyncedHover,
            extraVolumeDatasetOptions = {}
        } = options;

        const normalizedData = window.StockChartUtils.normalizeTradingData(rawData);
        if (normalizedData.length === 0) {
            return { priceChart: null, volumeChart: null, normalizedData };
        }

        const priceData = normalizedData.map(item => ({ x: item.x, y: item.c }));
        const volumeData = normalizedData.map(item => ({ x: item.x, y: Number((item.volume / 1000).toFixed(2)) }));
        const volumeColorSet = getYahooStyleVolumeColors(normalizedData);
        const volumeColors = volumeColorSet.map(item => item.background);
        const volumeBorders = volumeColorSet.map(item => item.border);
        const minX = priceData[0].x;
        const maxX = priceData[priceData.length - 1].x;
        const priceVolumeByX = new Map(normalizedData.map(item => [item.x, item]));

        function getPriceVolumePoint(context) {
            const rawX = context?.raw?.x ?? context?.parsed?.x;
            return priceVolumeByX.get(Number(rawX));
        }

        function buildPriceVolumeTooltipLabel(context) {
            const item = getPriceVolumePoint(context);
            if (!item) {
                return `${context.dataset.label}: ${window.StockChartUtils.formatNumber(context.parsed?.y)}`;
            }
            return [
                `收盤價 ${window.StockChartUtils.formatNumber(item.c)}`,
                `成交量 ${window.StockChartUtils.formatVolume(item.volume / 1000)} 千股`
            ];
        }

        const priceVolumeTooltipCallbacks = {
            title(items) {
                const item = getPriceVolumePoint(items?.[0]);
                return item ? item.date : '';
            },
            label: buildPriceVolumeTooltipLabel
        };

        let priceChart;
        let volumeChart;

        function handleLinkedHover(event, activeElements, chart) {
            const xValue = getActiveXFromChart(chart, activeElements);
            if (xValue != null) {
                syncHoverBetweenCharts(chart, xValue);
            } else if (event?.native?.type === 'mouseout') {
                clearSyncedHover();
            }
        }

        const priceCtx = priceCanvas.getContext('2d');
        const volumeCtx = volumeCanvas.getContext('2d');

        priceChart = new Chart(priceCtx, {
            data: {
                datasets: [
                    {
                        type: 'line',
                        label: '收盤價',
                        data: priceData,
                        backgroundColor: 'rgba(37, 99, 235, 0.10)',
                        borderColor: '#2563eb',
                        borderWidth: 2.4,
                        fill: true,
                        pointRadius: 0,
                        pointHitRadius: 14,
                        tension: 0.18
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: buildStandardHoverOptions(),
                onHover: handleLinkedHover,
                plugins: {
                    legend: {
                        position: 'top',
                        align: 'start',
                        labels: { usePointStyle: true, boxWidth: 8 }
                    },
                    tooltip: { callbacks: priceVolumeTooltipCallbacks },
                    zoom: buildZoomPluginOptions(() => priceChart)
                },
                scales: {
                    x: {
                        type: 'timeseries',
                        min: minX,
                        max: maxX,
                        time: { unit: 'day' },
                        ticks: { display: false },
                        grid: { display: false }
                    },
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        title: { display: true, text: '股價' }
                    }
                }
            }
        });

        volumeChart = new Chart(volumeCtx, {
            data: {
                datasets: [
                    {
                        type: 'bar',
                        label: '成交量(千股)',
                        data: volumeData,
                        backgroundColor: volumeColors,
                        borderColor: volumeBorders,
                        borderWidth: 1,
                        barPercentage: 0.9,
                        categoryPercentage: 0.98,
                        ...extraVolumeDatasetOptions
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: buildStandardHoverOptions(),
                onHover: handleLinkedHover,
                plugins: {
                    legend: {
                        position: 'top',
                        align: 'start',
                        labels: { usePointStyle: true, boxWidth: 8 }
                    },
                    tooltip: { callbacks: priceVolumeTooltipCallbacks },
                    zoom: buildZoomPluginOptions(() => volumeChart)
                },
                scales: {
                    x: {
                        type: 'timeseries',
                        min: minX,
                        max: maxX,
                        time: { unit: 'day' }
                    },
                    y: {
                        type: 'linear',
                        position: 'left',
                        title: { display: true, text: '成交量(千股)' }
                    }
                }
            }
        });

        priceCanvas.onmouseleave = clearSyncedHover;
        volumeCanvas.onmouseleave = clearSyncedHover;

        return { priceChart, volumeChart, normalizedData };
    }

    window.PriceChartRenderer = {
        getYahooStyleVolumeColors,
        renderPriceVolumeCharts
    };
})(window);
