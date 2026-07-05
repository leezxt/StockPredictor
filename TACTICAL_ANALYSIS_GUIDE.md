# 戰術解讀組態實裝完成（Tactical Analysis Configuration）

## 📊 概述

### 核心概念
根據雷達圖六軸分佈的**幾何形狀**自動判定操盤戰術型態，並輸出機構級決策建議。

```
六維軸向對應表：
┌─────────────────────────────────┐
│ A: 基本面（Fundamentals）        │ → 企業成長質量
│ B: 技術面（Momentum/Technicals） │ → 短期動能
│ C: 波動爆發（Volatility）        │ → 價格變動性
│ D: 風控基期（Context=Market）    │ → 大盤環境/週基期
│ E: 籌碼結構（Money/Microstructure）│ → 法人買賣超
│ F: 消息輿情（Sentiment/News）    │ → 市場情緒/新聞反應
└─────────────────────────────────┘
```

### 三大極端幾何圖形

#### 1️⃣ **「左下擴張型」** 🔮 潛伏珍珠
**幾何特徵**
- **高分軸向**：A(基本) > 70, E(籌碼) > 70, F(消息) > 70, D(基期) > 70
- **低分軸向**：B(技術) < 50, C(波動) < 50
- **視覺形狀**：左側（A/E/F）鼓脹，右側（B/C）凹陷 → 珍珠球形

**操盤決策**
```
🎯 核心策略
- 股價處於死寂底部，但法人大戶已偷偷吃飽
- 籌碼與基本面優異，技術面仍在蓄能
- 等待技術突破啟動，高勝率低風險長線建倉

📈 建倉邏輯
1. 法人持續買超（E > 70）= 機構布局完成
2. 基本面優良（A > 70）= 獲利動能已備
3. 技術面靜謐（B < 50）= 尚未形成推升動能
→ 等同「珍珠股」底部潛伏期

💰 投資仓位：★★★★★ (80~100%)
```

**進場訊號**
- KD 底背離形成
- 陽線收高 × 3 根連續
- 投信連買天數 ≥ 5
- 單日成交量較平日放大 30%+

**出場訊號**
- 技術面突破 20 日線後，支撑位回踩當為加碼機會
- 利空負面新聞出現立即止損
- 基期位置達到 25~30% 時獲利了結 50%

---

#### 2️⃣ **「右側尖刺型」** 🔥 短線妖股
**幾何特徵**
- **高分軸向**：B(技術) > 75, C(波動) > 75, F(消息) > 75
- **低分軸向**：D(基期) < 40, E(籌碼) < 40
- **視覺形狀**：右側（B/C/F）尖刺突出，左側（D/E）凹陷 → 危險怪物

**操盤決策**
```
⚠️ 高風險預警
- 純散戶當沖與題材熱錢炒作
- 基本面空心（無實質支撑）
- 融資爆增 = 下鄉訊號（隨時可能崩盤）

🚀 當沖邏輯
1. 消息面熱度爆表（F > 75）= 散戶跟風狂發
2. 技術面拉升（B > 75）= 短線資金推升
3. 波動放大（C > 75）= 進出場快速
→ 但籌碼虛弱（E < 40）= 基本無機構支撑

💰 投資仓位：★☆☆☆☆ (0~20% 高手專用)
⏱️ 持倉時間：當日沖銷，絕不過夜
```

**進場訊號**
- 短線拉升啟動（破昨日高）
- 單日成交量異常放大
- KD 值急速拉升至 80+ 且 D 未跟上（死叉風險）

**出場訊號（嚴格執行停損）**
- KD 死叉形成（K ≤ D 且走勢反轉）
- 跌破前 5 日低點 → **立即止損**
- 獲利超過目標（例：+5~8%）→ 50% 鎖定出場，30% 繼續追
- 消息面反轉 → **瞬間崩盤風險**，投機性止損優先

---

#### 3️⃣ **「全面飽滿型」** 💎 黃金完全體
**幾何特徵**
- **所有軸向**：A ≥ 75, B ≥ 75, C ≥ 75, D ≥ 75, E ≥ 75, F ≥ 75
- **視覺形狀**：六邊形完全規則飽滿，無明顯凹陷 → 黃金聖盒

**操盤決策**
```
🏆 市場極罕見的黃金交集
條件組合：
  ✓ Q1 財報優異（A > 75）= 基本面確認無誤
  ✓ 低基期剛突破（D > 75）= 估值空間待釋放
  ✓ 大戶鎖碼（E > 75）= 機構長期持有
  ✓ NRI/大盤環境有利（D > 75）= 系統性利好

📈 操盤級機構決策
1. 啟動凱利公式最大下注組態 → 倍數配置
2. 預期 3-6 月持續上漲 → 長線抱牢
3. 無近期獲利壓力 → 穩健持有

💰 投資仓位：★★★★★★ (100%+ 融資槓桿可考慮)
```

**進場訊號**
- 收盤價突破 20 日線
- KD 金叉確認（K > D 且斜率向上）
- 單日量能溫和放大（非爆量）
- 進場確認後開始加碼

**出場訊號（保留 50% 長期持有）**
- 基期位置達到 40%+ → 獲利了結 50%，留倉 50%
- 跌破月線 → 完全出場
- Q2 財報預期不佳 → 提早減碼

---

## 🔧 技術實裝

### 後端：RadarTacticalAnalyzer

```java
/* 位置：org/gtalent/RadarTacticalAnalyzer.java */

// 调用方式（在 RadarService.calculateRadarScores() 中）
RadarTacticalAnalyzer analyzer = new RadarTacticalAnalyzer();
result.tacticalAnalysis = analyzer.analyzeTactical(
    result.fundamental,   // A: 基本面
    result.momentum,      // B: 技術動能
    result.volatility,    // C: 波動
    result.context,       // D: 大盤環境 (基期代理)
    result.money,         // E: 籌碼
    result.news           // F: 消息輿情
);

// 返回結果結構
TacticalAnalysisResult {
    TacticalType type;           // 戰術型態枚舉
    String tacticName;           // 戰術名稱（例："潛伏珍珠"）
    String tacticEmoji;          // 視覺代號（🔮/🔥/💎）
    String description;          // 戰術解讀
    String operationAdvice;      // 操盤決策
    String riskWarning;          // 風險警示
    String entrySignal;          // 進場訊號
    String exitSignal;           // 出場訊號
    int confidenceScore;         // 置信度 0~100
}
```

### 前端：dashboard.html

**渲染函式**
```javascript
// renderTacticalAnalysis(tacticalData)
// 根據 TacticalType 動態渲染卡片顏色、圖標、文本
// 位置：dashboard.html 第 ~1120 行

/* 視覺樣式對應表 */
.tactical-card.golden_complete  → 💎 金色邊框 (FFD700)
.tactical-card.lurking_pearl    → 🔮 紫色邊框 (9966FF)
.tactical-card.short_term_monster → 🔥 紅色邊框 (FF6B6B)
.tactical-card.balanced         → ⚖️ 灰色邊框 (預設)
.tactical-card.unknown          → ❓ 詢問標記
```

**HTML 結構**
```html
<div class="tactical-card [type]">
  <div class="tactical-header">
    <h5>[emoji] [tacticName]</h5>
    <span class="confidence-badge [confidenceClass]">[score]%</span>
  </div>
  <div class="tactical-body">
    <div class="tactical-row">
      <span class="tactical-label">戰術型態：</span>
      <span class="tactical-value">[description]</span>
    </div>
    <div class="tactical-row">
      <span class="tactical-label">操盤決策：</span>
      <span class="tactical-value">[operationAdvice]</span>
    </div>
    <div class="tactical-row">
      <span class="tactical-label">風險警示：</span>
      <span class="tactical-value text-warning">[riskWarning]</span>
    </div>
    <div class="tactical-signals">
      <div class="signal-box entry">進場訊號：[entrySignal]</div>
      <div class="signal-box exit">出場訊號：[exitSignal]</div>
    </div>
  </div>
</div>
```

---

## 📋 判定邏輯流程圖

```
分析六軸分數（A-F）
    ↓
[第一層判定] 黃金完全體？
├─ YES (All ≥ 75)  → 💎 GOLDEN_COMPLETE ✓
└─ NO ↓

[第二層判定] 左下擴張型？
├─ YES (A,D,E,F > 70 & B,C < 50)  → 🔮 LURKING_PEARL ✓
└─ NO ↓

[第三層判定] 右側尖刺型？
├─ YES (B,C,F > 75 & D,E < 40)  → 🔥 SHORT_TERM_MONSTER ✓
└─ NO ↓

[第四層判定] 均衡中性？
├─ YES (各維差距 < 20)  → ⚖️ BALANCED ✓
└─ NO ↓

[最終判定] 未知組態
└─ ❓ UNKNOWN
```

---

## 🧪 測試案例

### 案例 1: 潛伏珍珠（2024 某電子股）
```
六軸分數：
  基本面 (A): 76   ✓
  技術面 (B): 38   ✓
  波動  (C): 42   ✓
  基期  (D): 72   ✓
  籌碼  (E): 74   ✓
  消息  (F): 71   ✓

判定：🔮 潛伏珍珠
置信度：85%

✅ 測試通過
```

### 案例 2: 短線妖股（2024 某生技股）
```
六軸分數：
  基本面 (A): 35   ✓
  技術面 (B): 82   ✓
  波動  (C): 88   ✓
  基期  (D): 28   ✓
  籌碼  (E): 32   ✓
  消息  (F): 79   ✓

判定：🔥 短線妖股
置信度：75%

⚠️ 風險提示：嚴格停損紀律必須
✅ 測試通過
```

### 案例 3: 黃金完全體（理想狀況）
```
六軸分數：
  基本面 (A): 78   ✓
  技術面 (B): 76   ✓
  波動  (C): 75   ✓
  基期  (D): 77   ✓
  籌碼  (E): 80   ✓
  消息  (F): 79   ✓

判定：💎 黃金完全體
置信度：95%

🏆 市場極罕見組合
✅ 測試通過
```

---

## 📖 使用規則

### ✅ 遵循原則

1. **黃金完全體** → 長期持有配置，凱利公式最大下注
2. **潛伏珍珠** → 耐心蓄能，等待技術突破確認
3. **短線妖股** → 高手專用，嚴格停損 2-3%
4. **均衡中性** → 靜觀其變，等待明確訊號
5. **未知組態** → 持續監視，等待更多資訊

### ⚠️ 禁忌雷區

- 🚫 短線妖股絕不過夜
- 🚫 利空出現時無條件止損
- 🚫 基期突破 40% 時必須減碼
- 🚫 消息反轉時立即止損
- 🚫 單一戶別持股不超過組合 25%

---

## 📊 錯誤處理

### 缺少資料場景

當任一軸向資料不足時：
```
• 新股發行 < 5 日 → type = UNKNOWN
• 營收資料缺失 → fundamental = 0, 自動降等
• 消息面無回應 → news = 50 (中性)
• 並發回覆說明「成分不足」
```

### 防禦機制

```java
if (allScoresValid && hasMinimumDataPoints) {
    return calculateTactical();  // 正常流程
} else {
    return unknownTactic();      // 降級為未知
}
```

---

## 🚀 後續增強方向

1. **機器學習校準**：根據歷史檢驗微調判定臨界值
2. **事件驅動權重**：財報期間自動提高 A 軸權重
3. **流動性檢查**：融資額度不足時自動降檔
4. **技術形態匹配**：搭配 K 線形態（頭肩頂、雙底等）
5. **跨標的比較**：同產業對標分析

---

## 📝 更新日期
- **實裝完成**：2026-05-28
- **測試驗證**：✅ 通過
- **前端整合**：✅ 完成
- **文檔更新**：✅ 完整

