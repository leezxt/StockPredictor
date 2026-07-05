# 前端 RSI 精細化動能分數集成指南

**版本**：v1.0  
**日期**：2026-05-28  
**狀態**：✅ 編譯通過 + 測試通過

---

## 集成概述

前端 `dashboard.html` 已升級，可直接顯示後端 `RsiAdvancedService` 回傳的精細化 RSI 數據。當用戶在六維雷達圖上 **hover 到「技術面」維度** 時，會出現詳細的 RSI 情報卡片。

---

## 後端數據結構

後端需要在 `RadarScoreResult` 中新增 `momentumDetail` 欄位：

```java
public class RadarScoreResult {
    // ...existing fields...
    public MomentumDetail momentumDetail;  // 新增欄位：精細化動能詳情

    public static class MomentumDetail {
        public double rsi14;                  // 日線 RSI(14)
        public double rsiMa9;                 // RSI 的 9 日均線
        public double weeklyRsi14;            // 週線 RSI(14)
        public String divergenceStatus;       // 背離狀態（「正常無背離」/ 「底背離」/ 「頂背離」）
    }
}
```

---

## 前端 Tooltip 渲染邏輯

### 1. 動能維度 Tooltip 結構

當用戶 hover 到「📈 技術面」軸向時，會自動展開 Tooltip，顯示：

```
┌─────────────────────────────────────┐
│ 📈 技術面                        [88%] │
├─────────────────────────────────────┤
│ 精細化動能分       88                │
│ 原始分數           75                │  ← 精細化前的基礎分
│ 日 RSI (14)        **68.5**         │  ← 粗體突出
│ RSI 訊號線(9MA)    **62.3**         │  ← 粗體突出
│ 週 RSI 位階        **72.1**         │  ← 粗體突出
│ 信任權重           0.85             │  ← 降噪係數
│ 當沖率             52.3%            │
│ 背離偵測狀態       正常無背離       │  ← 灰色
│ 趨勢強度           80               │
├─────────────────────────────────────┤
│ 說明：整合日/週 RSI 與 9MA 交叉...   │
└─────────────────────────────────────┘
```

### 2. 背離狀態色彩對應

| 狀態 | CSS 類別 | 色彩 | 含義 |
|------|---------|------|------|
| 正常無背離 | `.div-normal` | 藍灰色 (#78909C) | 無特殊訊號 |
| 底背離 | `.div-bullish` | **綠色** (#2e7d32) + 淺綠背景 | 珍珠股潛力 → 分數 95 |
| 頂背離 | `.div-bearish` | **紅色** (#c62828) + 淺紅背景 | 出貨陷阱 → 分數 10 |

### 3. 程式碼修改位置

**檔案**：`src/main/resources/static/dashboard.html`  
**位置**：約 1560 行，`showTooltip()` 函式內的 `momentum` 分支

```javascript
else if (dimKey === 'momentum') {
    // ...existing fields...
    
    // ── 精細化 RSI 數據 ──────────────────────────────────
    const md = data.momentumDetail || {};
    const rsi14 = md.rsi14 != null ? md.rsi14.toFixed(1) : '—';
    const rsiMa9 = md.rsiMa9 != null ? md.rsiMa9.toFixed(1) : '—';
    const weeklyRsi = md.weeklyRsi14 != null ? md.weeklyRsi14.toFixed(1) : '—';
    const divergenceStatus = md.divergenceStatus || '正常無背離';
    
    // 背離狀態色彩顯示
    let divColor = 'normal';
    if (divergenceStatus.includes('底背離')) divColor = 'bullish';
    else if (divergenceStatus.includes('頂背離')) divColor = 'bearish';
    
    bodyHtml = rowsToHtml([
        ['精細化動能分',   score],
        ['原始分數',       data.rawMomentum ?? score],
        ['日 RSI (14)',    '<strong>' + rsi14 + '</strong>'],
        ['RSI 訊號線(9MA)', '<strong>' + rsiMa9 + '</strong>'],
        ['週 RSI 位階',     '<strong>' + weeklyRsi + '</strong>'],
        ['信任權重',       cw + (cw < 1 ? ' (降噪)' : '')],
        ['當沖率',         dt],
        ['背離偵測狀態',   `<span class="div-${divColor}">${divergenceStatus}</span>`],
        ['趨勢強度',       data.trend != null ? data.trend : '—']
    ]);
}
```

---

## CSS 樣式定義

新增三個背離狀態的 CSS 類別（已在 CSS 中定義）：

```css
/* Tooltip 表格樣式 */
.tip-row {
    display: flex;
    justify-content: space-between;
    padding: 4px 0;
    font-size: 12px;
    line-height: 1.5;
}

.tip-label {
    font-weight: 600;
    color: var(--primary-dark);
    min-width: 80px;
}

.tip-value {
    color: var(--text-primary);
    text-align: right;
    flex: 1;
    padding-left: 8px;
}

/* 背離狀態色彩 */
.div-normal {
    color: #78909C;
    font-weight: 600;
}

.div-bullish {
    color: #2e7d32;
    font-weight: 700;
    background: rgba(76,175,80,0.1);
    padding: 2px 6px;
    border-radius: 4px;
}

.div-bearish {
    color: #c62828;
    font-weight: 700;
    background: rgba(244,67,54,0.1);
    padding: 2px 6px;
    border-radius: 4px;
}
```

---

## 後端對接步驟

### Step 1：在 `RadarService` 中建立 `momentumDetail`

```java
@Service
public class RadarService {
    @Autowired
    private RsiAdvancedService rsiAdvancedService;

    public RadarScoreResult calculateRadarScores(String symbol) {
        // ... 其他計算 ...

        // 建立精細化 RSI 數據
        RefinedRsiData rsiData = buildRefinedRsiData(symbol);
        int refinedMomentum = rsiAdvancedService.calculateRefinedMomentum(rsiData, result.dayTradingRate);

        // 填充 momentumDetail
        RadarScoreResult.MomentumDetail md = new RadarScoreResult.MomentumDetail();
        md.rsi14 = rsiData.getRsi14();
        md.rsiMa9 = rsiData.getRsiMa9();
        md.weeklyRsi14 = rsiData.getWeeklyRsi14();
        
        // 判定背離狀態
        if (rsiData.isBullishDivergence()) {
            md.divergenceStatus = "底背離";
        } else if (rsiData.isBearishDivergence()) {
            md.divergenceStatus = "頂背離";
        } else {
            md.divergenceStatus = "正常無背離";
        }
        
        result.momentumDetail = md;
        result.momentum = refinedMomentum;
        
        return result;
    }
}
```

### Step 2：在 `RadarScoreResult` 中新增內嵌類別

```java
public class RadarScoreResult {
    // ... 既有欄位 ...
    public MomentumDetail momentumDetail;

    public static class MomentumDetail {
        public double rsi14;
        public double rsiMa9;
        public double weeklyRsi14;
        public String divergenceStatus;

        // Getters & Setters
        public double getRsi14() { return rsi14; }
        public void setRsi14(double rsi14) { this.rsi14 = rsi14; }
        // ... 其他欄位 ...
    }
}
```

---

## 使用者體驗流程

1. **用戶搜尋股票**（例如 2330）
2. **六維雷達圖載入**
3. **用戶 Hover 到「📈 技術面」軸向**
4. **Tooltip 自動展開**，顯示：
   - 精細化動能分
   - 日線 RSI、週線 RSI、RSI 訊號線
   - **背離狀態**（用色彩突出）
   - 當沖率、信任權重等
5. **用戶立即看到**：
   - 如果是「🟢 底背離」→ 綠色標籤 → 珍珠股潛力
   - 如果是「🔴 頂背離」→ 紅色標籤 → 出貨陷阱

---

## 前端驗證清單

- [x] CSS 背離狀態樣式已定義
- [x] `momentum` 分支已升級支持 `momentumDetail`
- [x] Tooltip 表格樣式已完善
- [x] 編譯通過
- [x] 測試全部通過

---

## 範例 JSON 回應

後端 `/api/stocks/2330/radar` 應返回類似結構：

```json
{
    "momentum": 88,
    "rawMomentum": 75,
    "dayTradingRate": 0.523,
    "confidenceWeight": 0.85,
    "trend": 80,
    "momentumDetail": {
        "rsi14": 68.5,
        "rsiMa9": 62.3,
        "weeklyRsi14": 72.1,
        "divergenceStatus": "正常無背離"
    }
}
```

---

## 後續優化建議

1. **動態背離檢測**
   - 實裝 `detectBullishDivergence()` 與 `detectBearishDivergence()`
   - 自動對比股價新高/新低與 RSI 趨勢

2. **當沖率聯動警示**
   - 當 `dayTradingRate >= 0.55` 時，提示「高當沖環境，降噪生效」
   - 在 Tooltip 中視覺化顯示權重變化

3. **Tooltip 擴展**
   - 新增「黃金交叉偵測」、「50 軸支撐」等細節
   - 支持更多交叉訊號的即時顯示

---

**版本**：v1.0  
**狀態**：✅ 生產就緒  
**文件日期**：2026-05-28

