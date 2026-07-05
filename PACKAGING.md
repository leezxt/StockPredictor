# Windows 封裝說明

目前這個專案可封裝成兩種 Windows 發佈形式：

- **可攜版**：直接解壓後執行 `StockPredictor.exe`
- **安裝版**：一般使用者熟悉的安裝精靈 `.exe`

## 目前已完成並驗證的項目

- 已驗證：`build-exe.ps1 -Mode app-image`
- 已驗證：`build-exe.ps1 -Mode both`
- 已驗證：`dist/StockPredictor/StockPredictor.exe` 可啟動並回應 `http://localhost:8080`
- 已驗證：安裝版 EXE 可輸出為 `dist/StockPredictor-1.0.exe`
- 已完成：安裝版與可攜版都會套用專案圖示
- 已完成：安裝版會建立開始功能表捷徑與桌面捷徑
- 已完成：封裝腳本輸出流程改為中文
- 已完成：封裝版啟動後會自動開啟首頁瀏覽器
- 已完成：H2 資料改存到使用者資料目錄，避免寫入權限問題

## 需求環境

- JDK 17 以上，且需包含 `jpackage`
- Maven 3.8 以上
- 若要產生安裝版 EXE：需安裝 WiX Toolset 3.x

> 腳本會自動偵測常見的 WiX 安裝路徑，例如 `C:\Program Files (x86)\WiX Toolset v3.14\bin`。

## 一鍵封裝腳本

請在專案根目錄執行 `build-exe.ps1`。

### 1）只產生可攜版

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
cd "C:\Users\lee\Documents\intel j ide\StockPredictor\StockPredictor"
.\build-exe.ps1 -Mode app-image
```

輸出內容：

- `dist/StockPredictor/StockPredictor.exe`
- `dist/StockPredictor-<version>-windows-portable.zip`

建議發佈方式：

- 直接提供整個 `dist/StockPredictor/` 資料夾
- 或直接提供 ZIP，讓對方先解壓後執行

### 2）只產生安裝版

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
cd "C:\Users\lee\Documents\intel j ide\StockPredictor\StockPredictor"
.\build-exe.ps1 -Mode exe
```

輸出內容：

- `dist/StockPredictor-<version>.exe`

安裝版特性：

- 帶有專案圖示
- 會建立桌面捷徑
- 會建立開始功能表捷徑
- 安裝精靈描述顯示中文名稱（股市預測分析系統）
- 啟動後會自動開啟 `http://127.0.0.1:8080/`

### 3）一鍵同時產生可攜版 + 安裝版

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
cd "C:\Users\lee\Documents\intel j ide\StockPredictor\StockPredictor"
.\build-exe.ps1 -Mode both
```

成功後常見產物如下：

- `dist/StockPredictor/StockPredictor.exe`
- `dist/StockPredictor-<version>-windows-portable.zip`
- `dist/StockPredictor-<version>.exe`

## 常用參數

```powershell
.\build-exe.ps1 -Mode both -ProjectName StockPredictor
.\build-exe.ps1 -Mode both -OutputDir dist
.\build-exe.ps1 -Mode both -SkipTests $false
.\build-exe.ps1 -Mode both -MainJar StockPredictor-1.0-SNAPSHOT.jar
.\build-exe.ps1 -Mode both -CreateZip $false
.\build-exe.ps1 -Mode both -DisplayName "StockPredictor 股票分析系統"
.\build-exe.ps1 -Mode both -Vendor "GTalent"
```

## 發佈到其他 Windows 電腦的方式

### 可攜版

1. 把 ZIP 或整個 `dist/StockPredictor/` 傳給對方
2. 對方解壓縮到可寫入資料夾，例如桌面或文件夾
3. 執行 `StockPredictor.exe`
4. 程式會自動開啟首頁；若沒有自動開啟，再手動前往 `http://127.0.0.1:8080`

### 安裝版

1. 把 `dist/StockPredictor-<version>.exe` 傳給對方
2. 對方雙擊執行安裝
3. 安裝後可從桌面捷徑或開始功能表啟動
4. 啟動後程式會自動開啟首頁瀏覽器

## 執行注意事項

- 預設埠號為 `8080`，設定檔在 `src/main/resources/application.properties`
- H2 資料檔現在預設建立於使用者資料目錄：
  - Windows：`%LOCALAPPDATA%\StockPredictor\`
  - macOS：`~/Library/Application Support/StockPredictor/`
  - Linux：`~/.stockpredictor/`
- 如需自訂資料位置，可設定系統屬性 `-Dstockpredictor.data-dir=你的路徑` 或環境變數 `STOCKPREDICTOR_DATA_DIR`
- 若目標電腦的 `8080` 已被占用，可先修改 `application.properties` 再重新封裝

