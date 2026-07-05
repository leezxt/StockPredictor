# 🎨 KD 前端 UI 視覺呈現與 AI 解讀 - 完整指南

## 📋 概述

本指南介紹如何在前端實現 KD 指標的 **AI 智能解讀** 和 **視覺化呈現**，讓技術分析不再是冰冷的數字，而是直觀易懂的專業建議。

---

## 📦 已創建的文件

| 文件 | 位置 | 用途 |
|------|------|------|
| **kd-advanced-analysis.html** | src/main/resources/static/ | 完整的 KD 分析頁面 |
| **kd-diagnostic.js** | src/main/resources/static/ | KD 診斷 JavaScript 模組 |
| **本文件** | 項目根目錄 | 使用指南 |

---

## 🎯 核心功能

### 1. AI 智能診斷

根據 KD 指標狀態，自動判斷並給出專業解讀：

#### 診斷優先級

| 優先級 | 訊號類型 | 狀態標籤 | 顏色 |
|--------|---------|---------|------|
| 1 | 高檔鈍化 | 🔥 高檔狂熱鈍化 | 紅色 #ff4d4f |
| 2 | 低檔鈍化 | ⚠️ 低檔疲弱鈍化 | 橙色 #faad14 |
| 3 | 中軸黃金交叉 | ⚡ 中軸強勢交叉 | 綠色 #73d13d |
| 4 | 低檔黃金交叉 | ✅ 低檔黃金交叉 | 綠色 #52c41a |
| 5 | 低檔背離 | 🚀 底背離轉折現形 | 藍色 #1890ff |
| 6 | 高檔死亡交叉 | ⬇️ 高檔死亡交叉 | 橙紅 #ff7a45 |
| 7 | 一般黃金交叉 | 📈 黃金交叉 | 綠色 #52c41a |
| 8 | 一般死亡交叉 | 📉 死亡交叉 | 橙紅 #ff7a45 |
| 9 | 盤整狀態 | 📊 盤整狀態 | 灰色 #bfbfbf |

### 2. 視覺化組件

#### 診斷卡片
```html
<div class="kd-diagnostic-card">
    <div class="diagnostic-status">🔥 高檔狂熱鈍化</div>
    <div class="diagnostic-desc">
        指標進入軋空強勢期，K值已連續多日大於80...
    </div>
    <div class="diagnostic-advice">
        💡 操作建議：持股觀望，不追高...
    </div>
</div>
```

#### 評分顯示
```html
<div class="score-display">
    <div class="score-value">+15</div>
    <div class="score-level">動能等級：強</div>
</div>
```

#### KD 值卡片
```html
<div class="kd-value-card">
    <div class="kd-value-label">K 值</div>
    <div class="kd-value-number">85.3</div>
</div>
```

---

## 💻 使用方式

### 方式 1：獨立頁面

直接訪問 `kd-advanced-analysis.html`：

```
http://localhost:8080/kd-advanced-analysis.html
```

**功能**：
- ✅ 輸入股票代號自動分析
- ✅ AI 智能解讀
- ✅ 視覺化呈現
- ✅ 操作建議

### 方式 2：集成到現有頁面

在你的 HTML 頁面中引入模組：

```html
<!-- 引入 KD 診斷模組 -->
<script src="/kd-diagnostic.js"></script>

<script>
    // 使用 AI 診斷
    const kdDetail = {
        highPassivation: true,
        isGoldenCross: false,
        nearFifty: false,
        lowDivergence: false,
        isLowZone: false,
        isDeathCross: false,
        isHighZone: true
    };

    const diagnostic = KdDiagnostic.getDiagnostic(kdDetail);
    console.log(diagnostic);
    
    // 生成診斷卡片 HTML
    const cardHtml = KdDiagnostic.generateCard(diagnostic);
    document.getElementById('kdCard').innerHTML = cardHtml;
</script>
```

### 方式 3：與後端 API 集成

```javascript
// 從後端獲取 KD 分析結果
fetch('/api/kd/2330/analysis')
    .then(response => response.json())
    .then(data => {
        // 解析後端結果
        const kdDetail = KdDiagnostic.parseAnalysisResult(data);
        
        // 獲取 AI 診斷
        const diagnostic = KdDiagnostic.getDiagnostic(kdDetail);
        
        // 顯示診斷結果
        displayDiagnostic(diagnostic);
    });

function displayDiagnostic(diagnostic) {
    document.getElementById('status').textContent = diagnostic.status;
    document.getElementById('desc').textContent = diagnostic.desc;
    document.getElementById('advice').textContent = diagnostic.advice;
    document.getElementById('card').style.borderLeftColor = diagnostic.color;
}
```

---

## 🎨 視覺設計規範

### 顏色系統

| 狀態 | 主色 | 背景色 | 使用場景 |
|------|------|--------|---------|
| 極強多頭 | #52c41a | #f6ffed | 高檔鈍化、黃金交叉 |
| 強勢 | #73d13d | #f6ffed | 中軸黃金交叉 |
| 反轉訊號 | #1890ff | #e6f7ff | 低檔背離 |
| 警告 | #faad14 | #fffbe6 | 低檔鈍化 |
| 轉弱 | #ff7a45 | #fff2e8 | 死亡交叉 |
| 危險 | #ff4d4f | #fff1f0 | 高檔死亡交叉（在本方案中用於高檔鈍化強調）|
| 中性 | #bfbfbf | #fafafa | 盤整狀態 |

### 圖標系統

```
🔥 高檔鈍化（極端強勢）
⚡ 中軸黃金交叉（加速點）
✅ 低檔黃金交叉（安全點）
🚀 底背離（反轉訊號）
⬇️ 高檔死亡交叉（獲利了結）
📈 黃金交叉（轉強）
📉 死亡交叉（轉弱）
⚠️ 低檔鈍化（警告）
📊 盤整狀態（中性）
```

### 字體大小

```css
.diagnostic-status { font-size: 24px; font-weight: 700; }
.diagnostic-desc { font-size: 16px; line-height: 1.8; }
.diagnostic-advice { font-size: 14px; font-weight: 600; }
.score-value { font-size: 64px; font-weight: 700; }
.kd-value-number { font-size: 32px; font-weight: 700; }
```

---

## 🔧 API 對接

### 後端返回格式

KD 分析 API 應返回以下格式的 JSON：

```json
{
    "symbol": "2330",
    "score": 15,
    "level": "強",
    "signals": [
        "🔥 強勢高檔鈍化：極端多頭動能（連續3天 K >= 80）"
    ],
    "kValue": 85.3,
    "dValue": 82.1,
    "closePrice": 620.0,
    "date": "2026-05-20"
}
```

### 前端處理範例

```javascript
async function analyzeStock(symbol) {
    try {
        // 調用後端 API
        const response = await fetch(`/api/kd/${symbol}/analysis`);
        const data = await response.json();
        
        // 解析成 kdDetail 格式
        const kdDetail = KdDiagnostic.parseAnalysisResult(data);
        
        // 獲取 AI 診斷
        const diagnostic = KdDiagnostic.getDiagnostic(kdDetail);
        
        // 顯示結果
        document.getElementById('diagnosticStatus').textContent = diagnostic.status;
        document.getElementById('diagnosticStatus').style.color = diagnostic.color;
        document.getElementById('diagnosticDesc').textContent = diagnostic.desc;
        document.getElementById('diagnosticAdvice').textContent = diagnostic.advice;
        
        // 更新評分
        document.getElementById('scoreValue').textContent = 
            KdDiagnostic.formatScore(data.score);
        
        // 更新動能等級
        const scoreLevel = KdDiagnostic.getScoreLevel(data.score);
        document.getElementById('scoreLevel').textContent = 
            `動能等級：${scoreLevel.level}`;
        
    } catch (error) {
        console.error('分析失敗：', error);
    }
}
```

---

## 📊 完整範例

### HTML 結構

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>KD 分析</title>
    <link rel="stylesheet" href="kd-styles.css">
</head>
<body>
    <div class="container">
        <!-- 搜索框 -->
        <div class="search-box">
            <input type="text" id="symbolInput" placeholder="股票代號">
            <button onclick="analyzeStock()">分析</button>
        </div>

        <!-- AI 診斷卡片 -->
        <div id="kdDiagnosticCard" class="kd-diagnostic-card">
            <div class="diagnostic-status" id="diagnosticStatus"></div>
            <div class="diagnostic-desc" id="diagnosticDesc"></div>
            <div class="diagnostic-advice" id="diagnosticAdvice"></div>
        </div>

        <!-- 評分顯示 -->
        <div class="score-display">
            <div class="score-value" id="scoreValue">--</div>
            <div class="score-level" id="scoreLevel">--</div>
        </div>

        <!-- KD 值 -->
        <div class="kd-values-grid">
            <div class="kd-value-card">
                <div class="kd-value-label">K 值</div>
                <div class="kd-value-number" id="kValue">--</div>
            </div>
            <div class="kd-value-card">
                <div class="kd-value-label">D 值</div>
                <div class="kd-value-number" id="dValue">--</div>
            </div>
        </div>
    </div>

    <script src="/kd-diagnostic.js"></script>
    <script src="/app.js"></script>
</body>
</html>
```

### JavaScript 邏輯

```javascript
async function analyzeStock() {
    const symbol = document.getElementById('symbolInput').value;
    
    try {
        // 調用後端 API
        const response = await fetch(`/api/kd/${symbol}/analysis`);
        const data = await response.json();
        
        // 解析並顯示
        displayKdAnalysis(data);
        
    } catch (error) {
        alert('分析失敗：' + error.message);
    }
}

function displayKdAnalysis(data) {
    // 使用 KD 診斷模組
    const kdDetail = KdDiagnostic.parseAnalysisResult(data);
    const diagnostic = KdDiagnostic.getDiagnostic(kdDetail);
    
    // 更新診斷卡片
    document.getElementById('diagnosticStatus').textContent = diagnostic.status;
    document.getElementById('diagnosticStatus').style.color = diagnostic.color;
    document.getElementById('diagnosticDesc').textContent = diagnostic.desc;
    document.getElementById('diagnosticAdvice').textContent = '💡 ' + diagnostic.advice;
    
    // 更新卡片背景色
    const card = document.getElementById('kdDiagnosticCard');
    card.style.borderLeftColor = diagnostic.color;
    card.style.background = `linear-gradient(135deg, ${diagnostic.bgColor} 0%, #ffffff 100%)`;
    
    // 更新評分
    document.getElementById('scoreValue').textContent = KdDiagnostic.formatScore(data.score);
    
    const scoreLevel = KdDiagnostic.getScoreLevel(data.score);
    document.getElementById('scoreLevel').textContent = `動能等級：${scoreLevel.level}`;
    
    // 更新 K/D 值
    document.getElementById('kValue').textContent = data.kValue.toFixed(2);
    document.getElementById('dValue').textContent = data.dValue.toFixed(2);
}
```

---

## 🎯 各場景視覺效果

### 場景 1：高檔鈍化
```
┌─────────────────────────────────────────┐
│ 🔥 高檔狂熱鈍化              [極強]     │
├─────────────────────────────────────────┤
│ 指標進入軋空強勢期，K值已連續多日大於   │
│ 80。此時應「看多不追多，沿五日線抱牢」， │
│ 不盲目預測高點，直到K值跌破80再行         │
│ 獲利了結。                               │
├─────────────────────────────────────────┤
│ 💡 操作建議：持股觀望，不追高           │
└─────────────────────────────────────────┘
顏色：紅色主題
```

### 場景 2：中軸黃金交叉
```
┌─────────────────────────────────────────┐
│ ⚡ 中軸強勢交叉              [強]       │
├─────────────────────────────────────────┤
│ KD在50多空分界線附近完成黃金交叉，代表   │
│ 多方擺脫盤整、重新拿回主導權，往往是波   │
│ 段中繼發動的起漲訊號。                   │
├─────────────────────────────────────────┤
│ 💡 操作建議：可積極布局，波段起漲點     │
└─────────────────────────────────────────┘
顏色：綠色主題
```

### 場景 3：底背離
```
┌─────────────────────────────────────────┐
│ 🚀 底背離轉折現形            [強]       │
├─────────────────────────────────────────┤
│ 注意！股價雖震盪走低，但KD低點一底比     │
│ 一底高，動能已悄悄與股價脫鉤，屬於法人   │
│ 暗中進貨的「波段見底訊號」。             │
├─────────────────────────────────────────┤
│ 💡 操作建議：可逢低分批布局             │
└─────────────────────────────────────────┘
顏色：藍色主題
```

---

## 🚀 快速開始

### 1. 查看完整頁面

```bash
# 啟動應用
mvn spring-boot:run

# 訪問 KD 分析頁面
http://localhost:8080/kd-advanced-analysis.html
```

### 2. 測試 API

```bash
# 測試 KD 分析 API
curl http://localhost:8080/api/kd/2330/analysis
```

### 3. 集成到你的頁面

```html
<!-- 在你的頁面中引入模組 -->
<script src="/kd-diagnostic.js"></script>

<!-- 使用 -->
<script>
    const diagnostic = KdDiagnostic.getDiagnostic({
        highPassivation: true,
        isGoldenCross: false,
        nearFifty: false,
        lowDivergence: false
    });
    
    console.log(diagnostic.status);  // 🔥 高檔狂熱鈍化
    console.log(diagnostic.desc);    // 詳細描述...
    console.log(diagnostic.advice);  // 操作建議...
</script>
```

---

## 📚 文檔目錄

| 文檔 | 用途 |
|------|------|
| KD_ADVANCED_ENGINE_GUIDE.md | 後端 API 使用指南 |
| KD_FRONTEND_UI_GUIDE.md | 前端 UI 指南（本文件）|
| KD_ENGINE_IMPLEMENTATION_COMPLETE.md | 實作完成報告 |

---

## 🎉 特色功能

### 1. AI 智能解讀
- ✅ 9 種場景自動識別
- ✅ 專業級操作建議
- ✅ 優先級排序

### 2. 視覺化設計
- ✅ 漂亮的漸層色卡
- ✅ 清晰的圖標系統
- ✅ 響應式佈局

### 3. 用戶體驗
- ✅ 動畫效果流暢
- ✅ 一鍵分析
- ✅ 範例數據展示

### 4. 模組化設計
- ✅ 獨立 JavaScript 模組
- ✅ 可重用組件
- ✅ 易於集成

---

**版本**: v2.1  
**狀態**: ✅ Production Ready  
**日期**: 2026-05-20

_讓 KD 分析從數字變成智慧！_ 🎨

