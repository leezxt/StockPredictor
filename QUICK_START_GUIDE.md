# 🚀 StockPredictor 2.1 - 快速使用指南

## 📋 啟動應用

```bash
# 進入項目目錄
cd "C:\Users\lee\Documents\intel j ide\StockPredictor\StockPredictor"

# 啟動應用
mvn spring-boot:run

# 或者直接運行
mvn clean spring-boot:run
```

---

## 🎯 訪問入口

### ⭐ 新版儀表板（推薦）
```
http://localhost:8080/dashboard.html
```

**特色**：
- 🎨 現代化設計
- ✨ 流暢動畫
- 📊 統計展示
- 🚀 快速搜索

### 📈 經典分析介面
```
http://localhost:8080/index.html
```

**特色**：
- 📊 詳細技術指標
- 📉 多維度分析
- 🎯 法人買賣
- 📈 市場廣度

### 🔍 KD 高級分析
```
http://localhost:8080/kd-advanced-analysis.html
```

**特色**：
- 🤖 AI 智能解讀
- 📊 9 種場景識別
- 💡 操作建議
- 🎨 視覺化呈現

---

## ⚡ 快速操作

### 1. 分析單一股票

#### 方法 A：使用儀表板快速搜索
```
1. 訪問 http://localhost:8080/dashboard.html
2. 在搜索框輸入股票代號（例如：2330）
3. 點擊「快速分析」或按 Enter 鍵
4. 自動跳轉到分析頁面
```

#### 方法 B：直接訪問經典介面
```
http://localhost:8080/index.html?symbol=2330
```

#### 方法 C：使用 KD 分析
```
1. 訪問 http://localhost:8080/kd-advanced-analysis.html
2. 輸入股票代號
3. 點擊「開始分析」
```

### 2. 市場掃描（熱門功能）

```
1. 訪問 http://localhost:8080/index.html
2. 點擊「市場掃描 Top10」按鈕
3. 查看最適合投資的標的
```

### 3. 查看圖表

```
http://localhost:8080/chart.html
```

---

## 📊 功能導航

### 從儀表板訪問

```
dashboard.html
    ├── KD 高級動能分析 → kd-advanced-analysis.html
    ├── 法人買賣分析 → index.html
    ├── 市場廣度分析 → test-market-breadth.html
    ├── 投信鎖碼檢測 → test-trust-locked.html
    ├── 進出場時機 → test-enter.html
    └── 技術圖表 → chart.html
```

---

## 🎨 頁面特色對比

| 頁面 | 視覺風格 | 適合場景 | 核心功能 |
|------|---------|---------|---------|
| **dashboard.html** | 🎨 現代漸變 | 首次訪問、導航 | 快速搜索、功能總覽 |
| **index.html** | 📊 經典專業 | 深度分析 | 技術指標、法人買賣 |
| **kd-advanced-analysis.html** | 🤖 AI 智能 | KD 分析 | 9 種場景識別 |
| **chart.html** | 📈 圖表專業 | 技術分析 | K 線圖、指標疊加 |

---

## 💡 使用技巧

### 1. 快速分析工作流

```
dashboard.html（輸入代號）
    ↓ Enter
index.html（查看詳細分析）
    ↓ 法人買賣
    ↓ 市場廣度
    ↓ 技術指標
kd-advanced-analysis.html（KD 深度分析）
    ↓ AI 解讀
    ↓ 操作建議
做出投資決策 ✅
```

### 2. 市場掃描工作流

```
index.html
    ↓ 點擊「市場掃描」
    ↓ 查看 Top 10 標的
    ↓ 點擊股票代號
自動分析該股票 ✅
```

### 3. 綜合分析工作流

```
儀表板總覽
    ↓
個股技術分析（index.html）
    ├── 均線系統
    ├── RSI 動能
    ├── MACD 分析
    └── KD 指標
    ↓
KD 深度分析（kd-advanced-analysis.html）
    ├── 高檔鈍化檢測
    ├── 黃金交叉判斷
    ├── 背離識別
    └── AI 操作建議
    ↓
圖表確認（chart.html）
    └── K 線走勢驗證
    ↓
決策執行 ✅
```

---

## 🎯 核心功能使用

### 1. KD 高級分析（新功能）

```
訪問：http://localhost:8080/kd-advanced-analysis.html

使用步驟：
1. 輸入股票代號（例如：2330）
2. 點擊「開始分析」
3. 查看 AI 診斷結果
4. 閱讀操作建議
5. 查看評分和動能等級
```

**輸出示例**：
```
╔════════════════════════════════════╗
║ 🔥 高檔狂熱鈍化        [極強]     ║
╠════════════════════════════════════╣
║ 指標進入軋空強勢期，K值已連續多日  ║
║ 大於80。此時應「看多不追多，沿五日 ║
║ 線抱牢」...                        ║
╠════════════════════════════════════╣
║ 💡 持股觀望，不追高                ║
╚════════════════════════════════════╝

評分：+15
動能：極強
```

### 2. 法人買賣分析

```
訪問：http://localhost:8080/index.html

查看內容：
- 投信買賣超
- 外資買賣超
- 自營商買賣超
- 60 日趨勢圖表
```

### 3. 市場廣度分析

```
訪問：http://localhost:8080/test-market-breadth.html

功能：
- 即時漲跌家數
- 市場強弱指標
- 大盤情緒判斷
- 歷史趨勢圖
```

### 4. 投信鎖碼檢測

```
訪問：http://localhost:8080/test-trust-locked.html

檢測：
- 投信持股比例
- 長期持倉股票
- 潛在黑馬發現
```

---

## 📱 設備適配

### 桌面電腦（推薦）
- ✅ 全功能體驗
- ✅ 多圖表並列
- ✅ 詳細數據展示

### 平板設備
- ✅ 響應式佈局
- ✅ 觸控優化
- ✅ 核心功能完整

### 手機設備
- ✅ 單欄佈局
- ✅ 垂直滾動
- ✅ 簡化導航

---

## 🔧 常見問題

### Q1: 應用啟動失敗？
```bash
# 檢查 Java 版本
java -version  # 需要 Java 17+

# 清理並重新編譯
mvn clean install

# 重新啟動
mvn spring-boot:run
```

### Q2: 頁面顯示錯誤？
```
# 確認端口沒有被佔用
netstat -ano | findstr :8080

# 清理瀏覽器緩存
Ctrl + Shift + Del（Chrome）

# 強制刷新頁面
Ctrl + F5
```

### Q3: 數據無法載入？
```
# 檢查後端服務狀態
http://localhost:8080/actuator/health

# 查看控制台日誌
# 檢查是否有錯誤訊息
```

### Q4: 想切換回舊版介面？
```
# 訪問經典介面
http://localhost:8080/index.html

# 兩個版本可以共存使用
```

---

## 🎨 自訂化

### 1. 修改主題色

編輯 `dashboard.html`：
```css
:root {
    --primary-color: #667eea;  /* 改成你喜歡的顏色 */
    --secondary-color: #764ba2;
}
```

### 2. 調整卡片順序

在 `dashboard.html` 中調整功能卡片的 HTML 順序即可。

### 3. 添加新功能卡片

複製現有功能卡片的 HTML 結構：
```html
<div class="feature-card" onclick="location.href='your-page.html'">
    <div class="feature-icon">🎯</div>
    <h3>你的功能名稱</h3>
    <p>功能描述...</p>
    <a href="your-page.html" class="feature-link">
        立即使用 →
    </a>
</div>
```

---

## 📚 相關文檔

| 文檔 | 位置 | 用途 |
|------|------|------|
| 前端美化報告 | FRONTEND_BEAUTIFICATION_COMPLETE.md | 完整設計說明 |
| KD 分析指南 | KD_FRONTEND_UI_GUIDE.md | KD 功能使用 |
| 完整交付總結 | KD_COMPLETE_DELIVERY.md | 全面功能說明 |

---

## 🎉 開始使用

### 第一步：啟動應用
```bash
mvn spring-boot:run
```

### 第二步：訪問儀表板
```
http://localhost:8080/dashboard.html
```

### 第三步：探索功能
- 點擊功能卡片
- 使用快速搜索
- 查看統計數據

### 第四步：深度分析
- 輸入股票代號
- 查看多維度分析
- 參考 AI 建議

---

## 💫 推薦使用流程

### 新手用戶
```
1. 訪問 dashboard.html（認識系統）
2. 點擊「KD 高級動能分析」（體驗 AI）
3. 回到 index.html（深度分析）
4. 熟悉各項功能
```

### 進階用戶
```
1. dashboard.html 快速搜索
2. index.html 綜合分析
3. kd-advanced-analysis.html KD 確認
4. chart.html 圖表驗證
```

### 專業用戶
```
1. 市場掃描（發現標的）
2. 技術分析（多維評估）
3. KD 分析（精確時機）
4. 綜合決策（執行交易）
```

---

**版本**: v2.1  
**狀態**: ✅ Ready to Use  
**文件**: QUICK_START_GUIDE.md

_立即開始你的智能分析之旅！_ 🚀📈✨

