package org.gtalent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.gtalent.dto.FinMindNavData;
import org.springframework.cache.annotation.Cacheable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * FinMind API 客戶端服務
 * 用於獲取台灣股票的籌碼資料（法人買賣）
 *
 * @author StockPredictor Team
 * @version 1.0
 */
@Service
public class FinMindClient {
    private static final Logger logger = Logger.getLogger(FinMindClient.class.getName());
    private static final int LARGE_HOLDER_FACTOR = 15;
    private static final String DATASET_INSTITUTIONAL = "TaiwanStockInstitutionalInvestorsBuySell";
    private static final String LOGIN_TOKEN_FIELD = "token";

    @Value("${finmind.api.url}")
    private String apiUrl;

    @Value("${finmind.api.token}")
    private String apiToken;

    @Value("${finmind.api.login.url:https://api.finmindtrade.com/api/v4/login}")
    private String loginUrl;

    @Value("${finmind.api.user-id:}")
    private String userId;

    @Value("${finmind.api.password:}")
    private String password;

    @Value("${finmind.api.auto-login:true}")
    private boolean autoLogin;

    @Value("${twse.etf.nav.urls:https://www.twse.com.tw/rwd/zh/ETF/etfQuote?response=json&stockNo=%s,https://www.twse.com.tw/rwd/zh/ETF/etfDiv?response=json&stockNo=%s,https://www.tpex.org.tw/openapi/v1/tpex_etf_nav}")
    private String twseEtfNavUrlTemplates;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String cachedToken;

    public static class EtfPremiumResult {
        private final Double ratio;
        private final boolean navAvailable;
        private final String source;

        public EtfPremiumResult(Double ratio, boolean navAvailable, String source) {
            this.ratio = ratio;
            this.navAvailable = navAvailable;
            this.source = source;
        }

        public Double getRatio() { return ratio; }
        public boolean isNavAvailable() { return navAvailable; }
        public String getSource() { return source; }
    }

    public static class EtfBeneficiaryFlowResult {
        private final Double netUnits;
        private final Double flowRatio;
        private final boolean available;
        private final String source;

        public EtfBeneficiaryFlowResult(Double netUnits, Double flowRatio, boolean available, String source) {
            this.netUnits = netUnits;
            this.flowRatio = flowRatio;
            this.available = available;
            this.source = source;
        }

        public Double getNetUnits() { return netUnits; }
        public Double getFlowRatio() { return flowRatio; }
        public boolean isAvailable() { return available; }
        public String getSource() { return source; }
    }

    /**
     * 從 FinMind API 獲取籌碼資料
     * 數據範圍：法人買賣資料（TaiwanStockInstitutionalInvestorsBuySell）
     *
     * @param symbol    股票代號（例如："2330"）
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 籌碼資料列表，如果失敗返回空列表
     */
    @Cacheable(value = "finmind.chip", key = "#symbol + ':' + #startDate")
    public List<FinMindChipData> fetchChipData(String symbol, String startDate) {
        FinMindResponse body = requestDatasetAsResponse(DATASET_INSTITUTIONAL, symbol, startDate, true);
        if (body == null) {
            return new ArrayList<>();
        }
        logger.info("✅ 成功獲取 FinMind 籌碼資料: 狀態碼=" + body.getStatus() + ", 資料筆數=" +
                (body.getData() != null ? body.getData().size() : 0));
        return body.getData() != null ? body.getData() : new ArrayList<>();
    }

    /**
     * 帶備份重試的籌碼資料獲取
     * 如果首次嘗試失敗，會進行備份重試
     *
     * @param symbol    股票代號
     * @param startDate 開始日期
     * @param retries   重試次數（默認 1 次）
     * @return 籌碼資料列表
     */
    public List<FinMindChipData> fetchChipDataWithRetry(String symbol, String startDate, int retries) {
        List<FinMindChipData> result = fetchChipData(symbol, startDate);

        int attempt = 0;
        while (result.isEmpty() && attempt < retries) {
            attempt++;
            logger.info("🔄 重試第 " + attempt + " 次...");
            try {
                Thread.sleep(1000); // 等待 1 秒後重試
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("重試等待被中斷: " + e.getMessage());
                break;
            }
            result = fetchChipData(symbol, startDate);
        }

        return result;
    }

    /**
     * 獲取指定日期範圍內的籌碼資料
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @param endDate   結束日期（格式："2024-12-31"）
     * @return 籌碼資料列表
     */
    public List<FinMindChipData> fetchChipDataByDateRange(String symbol, String startDate, String endDate) {
        List<FinMindChipData> allData = new ArrayList<>();

        try {
            // 首先獲取從 startDate 開始的所有資料
            List<FinMindChipData> data = fetchChipData(symbol, startDate);

            // 過濾出在 endDate 之前的資料
            if (data != null && !data.isEmpty()) {
                for (FinMindChipData chip : data) {
                    if (chip.getDate() != null && chip.getDate().compareTo(endDate) <= 0) {
                        allData.add(chip);
                    }
                }
            }

            logger.info("📊 獲取 " + symbol + " 從 " + startDate + " 到 " + endDate + " 的資料，共 " + allData.size() + " 筆");
        } catch (Exception e) {
            logger.severe("❌ 獲取日期範圍資料失敗: " + e.getMessage());
        }

        return allData;
    }

    /**
     * 獲取最新的籌碼資料
     * 使用當天日期作為起始點
     *
     * @param symbol 股票代號
     * @return 籌碼資料列表
     */
    public List<FinMindChipData> fetchLatestChipData(String symbol) {
        String today = java.time.LocalDate.now().toString();
        return fetchChipData(symbol, today);
    }

    /**
     * 備援機制：獲取籌碼資料（用於 TWSE 失敗後的備援）
     * 當主要資料源失敗時調用此方法
     *
     * @param symbol    股票代號
     * @param date      日期（格式："2024-01-01"）
     * @return 籌碼資料列表
     */
    public List<FinMindChipData> fetchChipDataBackup(String symbol, String date) {
        logger.info("🔄 [FinMind 備援] 嘗試從 FinMind 備援通道獲取籌碼資料: " + symbol + " @ " + date);
        return fetchChipDataWithRetry(symbol, date, 2);
    }

    /**
     * 取得股權分散原始資料（TaiwanStockShareholding）。
     */
    public List<FinMindShareholdingData> fetchShareholdingData(String symbol, String startDate) {
        List<FinMindShareholdingData> results = new ArrayList<>();
        try {
            String body = requestDatasetRaw(
                    FinMindDataset.SHAREHOLDING.getDatasetName(),
                    symbol,
                    startDate,
                    true
            );
            if (body == null) {
                return results;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return results;
            }

            for (JsonNode row : dataNode) {
                FinMindShareholdingData item = objectMapper.treeToValue(row, FinMindShareholdingData.class);
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] 取得股權分散資料失敗: " + e.getMessage());
        }
        return results;
    }

    /**
     * 只保留 HoldingFactor = 15（1000 張以上）資料。
     */
    public List<FinMindShareholdingData> fetchLargeHolderShareholding(String symbol, String startDate) {
        List<FinMindShareholdingData> allData = fetchShareholdingData(symbol, startDate);
        List<FinMindShareholdingData> filtered = new ArrayList<>();
        for (FinMindShareholdingData item : allData) {
            if (item.getHoldingFactor() == LARGE_HOLDER_FACTOR) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    // ── 股權分散表相關 ────────────────────────────────

    /**
     * 獲取股權分散表數據（千張大戶控盤度）
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 股權分散數據列表
     */
    public <T> List<T> fetchStockHolding(String symbol, String startDate, Class<T> responseType) {
        return fetchGenericData(symbol, startDate, FinMindDataset.SHAREHOLDING, responseType);
    }

    // ── 融資融券相關 ──────────────────────────────────

    /**
     * 獲取融資融券與借券賣出數據
     * 用於識別散戶槓桿和空頭壓力
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 融資融券數據列表
     */
    public <T> List<T> fetchMarginPurchaseShortSale(String symbol, String startDate, Class<T> responseType) {
        return fetchGenericData(symbol, startDate, FinMindDataset.MARGIN, responseType);
    }

    // ── 股利政策相關 ──────────────────────────────────

    /**
     * 獲取股利政策與除權息數據
     * 用於殖利率計算和季底作帳估預測
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 股利數據列表
     */
    public <T> List<T> fetchStockDividend(String symbol, String startDate, Class<T> responseType) {
        return fetchGenericData(symbol, startDate, FinMindDataset.DIVIDEND, responseType);
    }

    // ── 當沖交易統計相關 ──────────────────────────────

    /**
     * 獲取當沖交易統計數據
     * 用於識別當沖過熱和技術指標失真風險
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 當沖統計數據列表
     */
    public <T> List<T> fetchStockDayTrading(String symbol, String startDate, Class<T> responseType) {
        return fetchGenericData(symbol, startDate, FinMindDataset.DAY_TRADE, responseType);
    }

    // ── 泛用數據獲取方法 ──────────────────────────────

    /**
     * 泛用 FinMind API 數據獲取方法
     * 支持所有 FinMind Dataset
     *
     * @param symbol       股票代號
     * @param startDate    開始日期
     * @param dataset      FinMind Dataset 列舉
     * @param responseType 返回數據類型
     * @return 數據列表
     */
    private <T> List<T> fetchGenericData(String symbol, String startDate, FinMindDataset dataset, Class<T> responseType) {
        FinMindResponse body = requestDatasetAsResponse(dataset.getDatasetName(), symbol, startDate, true);
        if (body == null) {
            return new ArrayList<>();
        }

        logger.info("✅ [FinMind] 成功獲取 " + dataset.getDescription() + ": 狀態碼=" + body.getStatus() +
                ", 資料筆數=" + (body.getData() != null ? body.getData().size() : 0));
        if (body.getData() == null) {
            return new ArrayList<>();
        }

        List<T> converted = new ArrayList<>();
        for (Object item : body.getData()) {
            converted.add(objectMapper.convertValue(item, responseType));
        }
        return converted;
    }

    /**
     * 獲取股權分散表數據（簡化版）
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 股權分散數據列表
     */
    public List<StockHoldingData> fetchStockHoldingData(String symbol, String startDate) {
        return fetchStockHolding(symbol, startDate, StockHoldingData.class);
    }

    /**
     * 獲取融資融券數據（簡化版）
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 融資融券數據列表
     */
    public List<MarginPurchaseShortSaleData> fetchMarginData(String symbol, String startDate) {
        return fetchMarginPurchaseShortSale(symbol, startDate, MarginPurchaseShortSaleData.class);
    }

    /**
     * 獲取股利數據（簡化版）
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 股利數據列表
     */
    public List<StockDividendData> fetchDividendData(String symbol, String startDate) {
        return fetchStockDividend(symbol, startDate, StockDividendData.class);
    }

    /**
     * 獲取當沖統計數據（簡化版）
     *
     * @param symbol    股票代號
     * @param startDate 開始日期（格式："2024-01-01"）
     * @return 當沖統計數據列表
     */
    public List<StockDayTradingData> fetchDayTradingData(String symbol, String startDate) {
        return fetchStockDayTrading(symbol, startDate, StockDayTradingData.class);
    }

    /**
     * 取得台股新聞資料（TaiwanStockNews）。
     */
    public List<NewsData> fetchStockNews(String symbol, String startDate) {
        List<NewsData> results = new ArrayList<>();
        try {
            String body = requestDatasetRaw(
                    FinMindDataset.NEWS.getDatasetName(),
                    symbol,
                    startDate,
                    true
            );
            if (body == null) {
                return results;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return results;
            }

            for (JsonNode row : dataNode) {
                NewsData item = parseNewsItem(row);
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Exception e) {
            logger.severe("[FinMind] 取得台股新聞資料失敗: " + e.getMessage());
        }
        return results;
    }

    // ── 財務報表相關 ─────────────────────────────────────

    /**
     * 取得綜合損益表原始列資料（TaiwanStockFinancialStatements）。
     * 每一列代表某季度某一損益科目的金額。
     *
     * @param symbol    股票代號
     * @param startDate 查詢起始日期（格式："2024-01-01"）
     * @return 損益表明細列表（每列含 date / type / value）
     */
    public List<FinMindRawFinancialRow> fetchFinancialStatements(String symbol, String startDate) {
        return fetchRawFinancialRows(FinMindDataset.FINANCIAL_STATEMENTS, symbol, startDate);
    }

    /**
     * 取得資產負債表原始列資料（TaiwanStockBalanceSheet）。
     * 每一列代表某季度某一資產／負債科目的金額。
     *
     * @param symbol    股票代號
     * @param startDate 查詢起始日期（格式："2024-01-01"）
     * @return 資產負債表明細列表（每列含 date / type / value）
     */
    public List<FinMindRawFinancialRow> fetchBalanceSheet(String symbol, String startDate) {
        return fetchRawFinancialRows(FinMindDataset.BALANCE_SHEET, symbol, startDate);
    }

    /**
     * 泛用財務報表原始資料獲取（內部使用）。
     */
    private List<FinMindRawFinancialRow> fetchRawFinancialRows(FinMindDataset dataset, String symbol, String startDate) {
        List<FinMindRawFinancialRow> results = new ArrayList<>();
        try {
            String body = requestDatasetRaw(dataset.getDatasetName(), symbol, startDate, true);
            if (body == null) {
                return results;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return results;
            }
            for (JsonNode row : dataNode) {
                FinMindRawFinancialRow item = objectMapper.treeToValue(row, FinMindRawFinancialRow.class);
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] 取得財務報表資料失敗(" + dataset.getDatasetName() + "/" + symbol + "): " + e.getMessage());
        }
        return results;
    }

    /**
     * 取得當沖統計原始資料（TaiwanStockDayTrading）。
     */
    public List<FinMindDayTradingData> fetchFinMindDayTradingData(String symbol, String startDate) {
        List<FinMindDayTradingData> results = new ArrayList<>();
        try {
            String body = requestDatasetRaw(
                    FinMindDataset.DAY_TRADE.getDatasetName(),
                    symbol,
                    startDate,
                    true
            );
            if (body == null) {
                return results;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return results;
            }

            for (JsonNode row : dataNode) {
                FinMindDayTradingData item = objectMapper.treeToValue(row, FinMindDayTradingData.class);
                if (item != null) {
                    results.add(item);
                }
            }
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] 取得當沖統計資料失敗: " + e.getMessage());
        }
        return results;
    }

    private NewsData parseNewsItem(JsonNode row) {
        if (row == null || !row.isObject()) {
            return null;
        }

        String title = readFirstText(row, "title", "news_title", "headline", "標題");
        if (!hasText(title)) {
            return null;
        }

        NewsData item = new NewsData();
        item.setDate(readFirstText(row, "date", "published_at", "publish_date", "datetime"));
        item.setStockId(readFirstText(row, "stock_id", "stockId", "symbol", "ticker"));
        item.setTitle(title);
        item.setContent(readFirstText(row, "content", "summary", "description", "news", "article"));
        item.setSource(readFirstText(row, "source", "provider", "media", "新聞來源"));
        item.setSentimentPolarity(0.0);
        return item;
    }

    private FinMindResponse requestDatasetAsResponse(String dataset, String symbol, String startDate, boolean allowRelogin) {
        try {
            logger.info("🔗 [FinMind] 發送資料請求: dataset=" + dataset + ", symbol=" + symbol + ", startDate=" + startDate);
            String token = resolveToken(false);
            String url = buildDataUrl(dataset, symbol, startDate, token);
            ResponseEntity<FinMindResponse> response = restTemplate.getForEntity(url, FinMindResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warning("⚠️  [FinMind] 回傳狀態異常: " + response.getStatusCode());
                return null;
            }

            FinMindResponse body = response.getBody();
            if (allowRelogin && canRelogin() && isAuthFailureBody(body)) {
                logger.warning("🔒 [FinMind] Token 可能失效，嘗試重新登入後重試");
                forceRefreshToken();
                return requestDatasetAsResponse(dataset, symbol, startDate, false);
            }
            if (isPermissionRestrictedBody(body)) {
                logger.warning("⚠️ [FinMind] 資料權限受限（dataset=" + dataset + ", symbol=" + symbol + "）: "
                        + body.getMsg());
                return null;
            }
            return body;
        } catch (HttpStatusCodeException e) {
            if (allowRelogin && canRelogin() && isAuthStatus(e.getStatusCode())) {
                logger.warning("🔒 [FinMind] 收到認證錯誤 " + e.getStatusCode() + "，嘗試重新登入後重試");
                forceRefreshToken();
                return requestDatasetAsResponse(dataset, symbol, startDate, false);
            }
            logger.severe("🚨 [FinMind] API 呼叫失敗: " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] API 呼叫失敗: " + e.getMessage());
            return null;
        }
    }

    private String requestDatasetRaw(String dataset, String symbol, String startDate, boolean allowRelogin) {
        try {
            String token = resolveToken(false);
            String url = buildDataUrl(dataset, symbol, startDate, token);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warning("⚠️  [FinMind] 原始資料查詢失敗: " + response.getStatusCode());
                return null;
            }

            if (allowRelogin && canRelogin() && isAuthFailurePayload(response.getBody())) {
                logger.warning("🔒 [FinMind] Token 可能失效（原始回傳），嘗試重新登入後重試");
                forceRefreshToken();
                return requestDatasetRaw(dataset, symbol, startDate, false);
            }
            if (isPermissionRestrictedPayload(response.getBody())) {
                logger.warning("⚠️ [FinMind] 原始資料權限受限（dataset=" + dataset + ", symbol=" + symbol + "）");
                return null;
            }
            String responseBody = response.getBody();
            if (isHtmlResponse(responseBody)) {
                logger.warning("⚠️ [FinMind] API 返回 HTML 而非 JSON（dataset=" + dataset + ", symbol=" + symbol + "）");
                return null;
            }
            return responseBody;
        } catch (HttpStatusCodeException e) {
            if (allowRelogin && canRelogin() && isAuthStatus(e.getStatusCode())) {
                logger.warning("🔒 [FinMind] 收到認證錯誤 " + e.getStatusCode() + "，嘗試重新登入後重試");
                forceRefreshToken();
                return requestDatasetRaw(dataset, symbol, startDate, false);
            }
            logger.severe("🚨 [FinMind] 原始資料查詢失敗: " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] 原始資料查詢失敗: " + e.getMessage());
            return null;
        }
    }

    private String buildDataUrl(String dataset, String symbol, String startDate, String token) {
        return UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("dataset", dataset)
                .queryParam("data_id", symbol)
                .queryParam("start_date", startDate)
                .queryParam("token", token)
                .build(true)
                .toUriString();
    }

    private synchronized String resolveToken(boolean forceRefresh) {
        if (!forceRefresh && hasText(cachedToken)) {
            return cachedToken;
        }

        if (!forceRefresh && hasText(apiToken)) {
            cachedToken = apiToken.trim();
            return cachedToken;
        }

        if (!autoLogin || !hasLoginCredentials()) {
            return hasText(cachedToken) ? cachedToken : apiToken;
        }

        String newToken = loginAndExtractToken();
        if (hasText(newToken)) {
            cachedToken = newToken;
            return cachedToken;
        }

        return hasText(cachedToken) ? cachedToken : apiToken;
    }

    private void forceRefreshToken() {
        resolveToken(true);
    }

    private String loginAndExtractToken() {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("password", password);

            ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, payload, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warning("⚠️  [FinMind] 登入失敗: " + response.getStatusCode());
                return null;
            }
            String loginBody = response.getBody();
            if (isHtmlResponse(loginBody)) {
                logger.warning("⚠️  [FinMind] 登入端點返回 HTML，可能為維護頁面");
                return null;
            }

            JsonNode root = objectMapper.readTree(loginBody);
            String token = extractToken(root);
            if (!hasText(token)) {
                logger.warning("⚠️  [FinMind] 登入成功但未取得 token 欄位");
                return null;
            }

            logger.info("✅ [FinMind] 已完成登入並更新 token 快取");
            return token.trim();
        } catch (Exception e) {
            logger.severe("🚨 [FinMind] 登入流程失敗: " + e.getMessage());
            return null;
        }
    }

    private String extractToken(JsonNode root) {
        String directToken = root.path(LOGIN_TOKEN_FIELD).asText(null);
        if (hasText(directToken)) {
            return directToken;
        }

        String accessToken = root.path("access_token").asText(null);
        if (hasText(accessToken)) {
            return accessToken;
        }

        String jwtToken = root.path("jwt").asText(null);
        if (hasText(jwtToken)) {
            return jwtToken;
        }

        return root.path("data").path(LOGIN_TOKEN_FIELD).asText(null);
    }

    private boolean isAuthFailureBody(FinMindResponse body) {
        if (body == null) {
            return false;
        }
        return isAuthFailureMessage(body.getMsg());
    }

    private boolean isAuthFailurePayload(String payload) {
        if (!hasText(payload)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String msg = root.path("msg").asText("");
            return isAuthFailureMessage(msg);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPermissionRestrictedBody(FinMindResponse body) {
        if (body == null) {
            return false;
        }
        return isPermissionRestrictedMessage(body.getMsg());
    }

    private boolean isPermissionRestrictedPayload(String payload) {
        if (!hasText(payload)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String msg = root.path("msg").asText("");
            return isPermissionRestrictedMessage(msg);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPermissionRestrictedMessage(String msg) {
        if (!hasText(msg)) {
            return false;
        }
        String normalized = msg.toLowerCase();
        return normalized.contains("update your user level")
                || normalized.contains("your level is")
                || normalized.contains("sponsor")
                || normalized.contains("permission")
                || normalized.contains("not enough permission")
                || normalized.contains("forbidden");
    }

    private boolean isAuthFailureMessage(String msg) {
        if (!hasText(msg)) {
            return false;
        }
        String normalized = msg.toLowerCase();
        return normalized.contains("token")
                || normalized.contains("auth")
                || normalized.contains("login")
                || normalized.contains("expired")
                || normalized.contains("unauthorized")
                || normalized.contains("invalid");
    }

    private boolean isAuthStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 401 || value == 403;
    }

    private boolean hasLoginCredentials() {
        return hasText(userId) && hasText(password);
    }

    private boolean canRelogin() {
        return autoLogin && hasLoginCredentials();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 取得 ETF 折溢價比率（decimal，例：-0.012 表示折價 1.2%）。
     * 若資料源不可用或欄位格式不符，回傳 null。
     */
    public EtfPremiumResult fetchEtfDiscountPremium(String symbol) {
        if (!hasText(symbol)) {
            return new EtfPremiumResult(null, false, "NOT_AVAILABLE");
        }

        // 策略：穩定來源優先（TWSE/TPEx NAV 類公開資料）→ 其餘來源備援（FinMind datasets）
        List<String> navSources = resolveEtfNavSourceUrls(symbol);
        for (String navSourceUrl : navSources) {
            try {
                String payload = fetchEtfNavPayload(navSourceUrl);
                Double ratio = parseTwseEtfDiscountPremiumRatio(payload, symbol);
                if (ratio != null) {
                    logger.info("✅ [ETF NAV Primary] 折溢價接入成功: " + symbol + " source=" + navSourceUrl + " ratio=" + ratio);
                    return new EtfPremiumResult(ratio, true, "TWSE_TPEX_PRIMARY");
                }
            } catch (Exception ex) {
                logger.fine("ETF NAV primary 失敗(" + symbol + ", " + navSourceUrl + "): " + ex.getMessage());
            }
        }

        String startDate = java.time.LocalDate.now().minusDays(14).toString();
        List<String> datasets = List.of(
                "TaiwanStockETFDiscountPremium",
                "TaiwanETFDiscountPremium",
                "TaiwanEtfDiscountPremium",
                "TaiwanStockEtfDiscountPremium"
        );

        for (String dataset : datasets) {
            try {
                String payload = requestDatasetRaw(dataset, symbol, startDate, true);
                Double ratio = parseLatestDiscountPremiumRatio(payload);
                if (ratio != null) {
                    logger.info("✅ [FinMind Fallback] ETF 折溢價資料接入成功: " + symbol + " dataset=" + dataset + " ratio=" + ratio);
                    return new EtfPremiumResult(ratio, true, "FINMIND_FALLBACK");
                }
            } catch (Exception ex) {
                logger.fine("ETF 折溢價備援查詢失敗(" + dataset + "/" + symbol + "): " + ex.getMessage());
            }
        }

        return new EtfPremiumResult(null, false, "NOT_AVAILABLE");
    }

    public Double fetchEtfDiscountPremiumRatio(String symbol) {
        return fetchEtfDiscountPremium(symbol).getRatio();
    }

    /**
     * 取得 ETF 受益權單位申贖淨流（真實申贖欄位）。
     *
     * @param symbol ETF 代號
     * @param lookbackDays 回看交易日數（建議 5~20）
     * @return 淨申贖結果（netUnits 與 flowRatio）
     */
    public EtfBeneficiaryFlowResult fetchEtfBeneficiaryFlow(String symbol, int lookbackDays) {
        if (!hasText(symbol)) {
            return new EtfBeneficiaryFlowResult(null, null, false, "NOT_AVAILABLE");
        }
        int window = Math.max(3, lookbackDays);
        String startDate = java.time.LocalDate.now().minusDays(window * 4L).toString();

        List<String> datasets = List.of(
                "TaiwanETFBeneficiaryShares",
                "TaiwanEtfBeneficiaryShares",
                "TaiwanStockETFBeneficiaryShares"
        );

        for (String dataset : datasets) {
            try {
                String payload = requestDatasetRaw(dataset, symbol, startDate, true);
                EtfBeneficiaryFlowResult parsed = parseEtfBeneficiaryFlow(payload, symbol, window, dataset);
                if (parsed != null && parsed.isAvailable()) {
                    logger.info("✅ [FinMind] ETF 申贖資料接入成功: " + symbol + " dataset=" + dataset +
                            " netUnits=" + parsed.getNetUnits() + ", flowRatio=" + parsed.getFlowRatio());
                    return parsed;
                }
            } catch (Exception ex) {
                logger.fine("ETF 申贖資料查詢失敗(" + dataset + "/" + symbol + "): " + ex.getMessage());
            }
        }
        return new EtfBeneficiaryFlowResult(null, null, false, "NOT_AVAILABLE");
    }

    private List<String> resolveEtfNavSourceUrls(String symbol) {
        if (!hasText(twseEtfNavUrlTemplates)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : twseEtfNavUrlTemplates.split(",")) {
            if (!hasText(raw)) {
                continue;
            }
            String template = raw.trim();
            out.add(template.contains("%s") ? String.format(template, symbol) : template);
        }
        return out;
    }

    private EtfBeneficiaryFlowResult parseEtfBeneficiaryFlow(String payload, String symbol, int lookbackDays, String source) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                return null;
            }

            class FlowRow {
                String date;
                double netUnits;
                Double outstanding;
            }

            List<FlowRow> rows = new ArrayList<>();
            for (JsonNode row : dataNode) {
                if (row == null || !row.isObject()) {
                    continue;
                }
                boolean hasSymbolField = containsSymbolField(row);
                if (hasSymbolField && !isMatchingSymbol(row, symbol)) {
                    continue;
                }

                Double change = readFirstNumeric(row,
                        "change", "unit_change", "net_change", "net_units", "unit_net_change", "units_change",
                        "beneficiary_shares_change", "增減", "變動");
                Double offering = readFirstNumeric(row,
                        "offering", "creation", "subscribe", "subscription", "申購", "申購受益權單位數");
                Double redemption = readFirstNumeric(row,
                        "redemption", "redeem", "贖回", "贖回受益權單位數");
                Double outstanding = readFirstNumeric(row,
                        "outstanding", "outstanding_units", "beneficiary_shares", "beneficiaryShares",
                        "流通在外受益權單位數", "受益權單位數");

                double netUnits;
                if (change != null) {
                    netUnits = change;
                } else if (offering != null || redemption != null) {
                    netUnits = (offering == null ? 0.0 : offering) - (redemption == null ? 0.0 : redemption);
                } else {
                    continue;
                }

                FlowRow r = new FlowRow();
                r.date = readFirstText(row, "date", "Date", "trade_date");
                r.netUnits = netUnits;
                r.outstanding = outstanding;
                rows.add(r);
            }

            if (rows.isEmpty()) {
                return null;
            }

            rows.sort((a, b) -> {
                String da = a.date == null ? "" : a.date;
                String db = b.date == null ? "" : b.date;
                return da.compareTo(db);
            });

            int from = Math.max(0, rows.size() - lookbackDays);
            double netSum = 0.0;
            Double latestOutstanding = null;
            for (int i = from; i < rows.size(); i++) {
                FlowRow row = rows.get(i);
                netSum += row.netUnits;
                if (row.outstanding != null && Math.abs(row.outstanding) > 1e-9) {
                    latestOutstanding = row.outstanding;
                }
            }

            Double flowRatio = null;
            if (latestOutstanding != null && Math.abs(latestOutstanding) > 1e-9) {
                flowRatio = netSum / latestOutstanding;
            }

            return new EtfBeneficiaryFlowResult(netSum, flowRatio, true, source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchEtfNavPayload(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return null;
        }
        return response.getBody();
    }

    private Double parseTwseEtfDiscountPremiumRatio(String payload, String symbol) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            return findDiscountPremiumRatio(root, symbol);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double findDiscountPremiumRatio(JsonNode node, String symbol) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            boolean hasSymbolField = containsSymbolField(node);
            boolean matchedSymbol = !hasSymbolField || isMatchingSymbol(node, symbol);

            if (matchedSymbol) {
                Double directRatio = readFirstNumeric(node,
                        "折溢價(%)", "折溢價％", "折溢價", "折價溢價",
                        "discount_premium_ratio", "discountPremiumRatio",
                        "premium_discount_ratio", "premiumDiscountRatio");
                if (directRatio != null) {
                    return normalizePercentageRatio(directRatio);
                }

                Double nav = readFirstNumeric(node,
                        "淨值", "ETF淨值", "NAV", "nav", "net_asset_value", "netAssetValue");
                Double marketPrice = readFirstNumeric(node,
                        "市價", "收盤價", "成交價", "close", "Close", "price", "market_price", "marketPrice");
                if (nav != null && marketPrice != null && Math.abs(nav) > 1e-9) {
                    return (marketPrice - nav) / nav;
                }
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                Double childRatio = findDiscountPremiumRatio(entry.getValue(), symbol);
                if (childRatio != null) {
                    return childRatio;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                Double childRatio = findDiscountPremiumRatio(child, symbol);
                if (childRatio != null) {
                    return childRatio;
                }
            }
        }

        return null;
    }

    private Double parseLatestDiscountPremiumRatio(String payload) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                return null;
            }

            JsonNode latest = dataNode.get(dataNode.size() - 1);
            if (latest == null || !latest.isObject()) {
                return null;
            }

            Double directRatio = readFirstNumeric(latest,
                    "discount_premium_ratio",
                    "discount_premium",
                    "discountPremiumRatio",
                    "discountPremium",
                    "premium_discount_ratio",
                    "premiumDiscountRatio",
                    "折溢價(%)"
            );
            if (directRatio != null) {
                return normalizePercentageRatio(directRatio);
            }

            Double nav = readFirstNumeric(latest,
                    "nav", "NAV", "net_asset_value", "netAssetValue", "淨值");
            Double marketPrice = readFirstNumeric(latest,
                    "close", "Close", "closing_price", "closingPrice", "price", "market_price", "市價", "收盤價");
            if (nav == null || marketPrice == null || Math.abs(nav) < 1e-9) {
                return null;
            }
            return (marketPrice - nav) / nav;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double normalizePercentageRatio(Double value) {
        if (value == null) {
            return null;
        }
        // 若資料是百分比格式（例如 -1.23），轉為 decimal（-0.0123）
        if (Math.abs(value) > 1.0) {
            return value / 100.0;
        }
        return value;
    }

    private Double readFirstNumeric(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return v.asDouble();
            }
            String raw = v.asText("").trim();
            if (raw.isEmpty() || "--".equals(raw)) {
                continue;
            }
            try {
                return Double.parseDouble(raw.replace(",", "").replace("%", "").trim());
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return null;
    }

    private boolean containsSymbolField(JsonNode node) {
        return readFirstText(node,
                "stock_id", "stockId", "stockNo", "Code", "證券代號", "基金代號", "代號") != null;
    }

    private boolean isMatchingSymbol(JsonNode node, String symbol) {
        String found = readFirstText(node,
                "stock_id", "stockId", "stockNo", "Code", "證券代號", "基金代號", "代號");
        return hasText(found) && found.trim().equalsIgnoreCase(symbol.trim());
    }

    private String readFirstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) {
                continue;
            }
            String text = v.asText("").trim();
            if (!text.isEmpty() && !"--".equals(text)) {
                return text;
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════
    //  融資融券 API
    // ════════════════════════════════════════════════════════════

    /**
     * 取得最近 N 個交易日的融資融券歷史。
     * Dataset: TaiwanStockMarginPurchaseShortSale
     *
     * @param symbol  股票代號
     * @param days    需要的交易日數（至少需 5 筆，建議傳 10~20）
     * @return 融資融券歷史列表（按日期升序），若取得失敗回傳空列表
     */
    public List<FinMindMarginData> fetchMarginHistory(String symbol, int days) {
        if (!hasText(symbol)) {
            return List.of();
        }
        try {
            java.time.LocalDate startDate = java.time.LocalDate.now().minusDays(days * 2L);

            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("dataset", "TaiwanStockMarginPurchaseShortSale")
                    .queryParam("data_id", symbol)
                    .queryParam("start_date", startDate.toString())
                    .queryParam("token", resolveToken())
                    .toUriString();

            ResponseEntity<FinMindMarginResponse> response =
                    restTemplate.getForEntity(url, FinMindMarginResponse.class);

            if (response.getBody() == null || response.getBody().getData() == null) {
                return List.of();
            }

            List<FinMindMarginData> all = new ArrayList<>(response.getBody().getData());
            all.sort(java.util.Comparator.comparing(FinMindMarginData::getDate));
            if (all.size() > days) {
                return all.subList(all.size() - days, all.size());
            }
            return all;

        } catch (Exception e) {
            logger.warning("fetchMarginHistory 失敗 (" + symbol + "): " + e.getMessage());
            return List.of();
        }
    }

    /** 取得目前有效的 token（優先用快取，否則用設定值） */
    private String resolveToken() {
        return hasText(cachedToken) ? cachedToken : apiToken;
    }

    /**
     * 取得 ETF 淨值 (NAV) 歷史數據
     *
     * @param symbol ETF 代碼 (如 0050、0056)
     * @param startDate 查詢起始日期 (格式: YYYY-MM-DD)
     * @return FinMindNavData 清單 (升序排列)
     */
    @Cacheable(value = "finmind.nav", key = "#symbol + ':' + #startDate", cacheManager = "historicalDataCacheManager")
    public List<FinMindNavData> fetchNavData(String symbol, String startDate) {
        try {
            logger.info("🔗 [FinMind] 發送 NAV 資料請求: symbol=" + symbol + ", startDate=" + startDate);

            String token = resolveToken(false);
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("dataset", "TaiwanETFNavigation")
                    .queryParam("data_id", symbol)
                    .queryParam("start_date", startDate)
                    .queryParam("token", token)
                    .toUriString();

            ResponseEntity<FinMindResponse> response = restTemplate.getForEntity(url, FinMindResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warning("⚠️ [FinMind] NAV 回傳狀態異常: " + response.getStatusCode());
                return List.of();
            }

            FinMindResponse body = response.getBody();
            List<FinMindNavData> navList = new ArrayList<>();

            if (body.getData() != null && !body.getData().isEmpty()) {
                for (Object item : body.getData()) {
                    if (item instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) item;
                        FinMindNavData nav = new FinMindNavData();
                        nav.setDate((String) map.get("date"));
                        nav.setStockId(symbol);
                        nav.setNav(((Number) map.get("NAV")).doubleValue());
                        navList.add(nav);
                    }
                }
            }

            navList.sort(java.util.Comparator.comparing(FinMindNavData::getDate));
            return navList;

        } catch (Exception e) {
            logger.warning("fetchNavData 失敗 (" + symbol + "): " + e.getMessage());
            return List.of();
        }
    }

    /** 判斷 HTTP 回應是否為 HTML（含 BOM 前綴處理）。JSON 永遠不以 '<' 開頭。 */
    private static boolean isHtmlResponse(String body) {
        if (body == null) return true;
        int i = 0;
        while (i < body.length() && (body.charAt(i) <= ' ' || body.charAt(i) == '\uFEFF')) i++;
        return i < body.length() && body.charAt(i) == '<';
    }
}
