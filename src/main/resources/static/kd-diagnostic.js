/**
 * KD 高級診斷模組
 *
 * 提供 KD 指標的 AI 智能解讀功能
 * 可在任何前端頁面中引入使用
 *
 * @module kdDiagnostic
 * @version 2.1
 * @author StockPredictor Team
 */

const KdDiagnostic = {
    toNumber(value) {
        const num = Number(value);
        return Number.isFinite(num) ? num : Number.NaN;
    },

    format2(value) {
        return Number.isFinite(value) ? value.toFixed(2) : '--';
    },

    /**
     * AI 智能診斷 KD 狀態
     *
     * @param {Object} kdDetail - KD 詳細資料
     * @param {boolean} kdDetail.highPassivation - 高檔鈍化（連續3天 K >= 80）
     * @param {boolean} kdDetail.lowPassivation - 低檔鈍化（連續3天 K <= 20）
     * @param {boolean} kdDetail.isGoldenCross - 黃金交叉（K 上穿 D）
     * @param {boolean} kdDetail.isDeathCross - 死亡交叉（K 下穿 D）
     * @param {boolean} kdDetail.nearFifty - 接近中軸50
     * @param {boolean} kdDetail.isLowZone - 低檔區（K < 30）
     * @param {boolean} kdDetail.isHighZone - 高檔區（K > 70）
     * @param {boolean} kdDetail.lowDivergence - 低檔背離
     * @returns {Object} 診斷結果
     */
    getDiagnostic(kdDetail) {
        const indicators = kdDetail.extendedIndicators || {};
        const diagnosisHint = String(kdDetail.diagnosis || '').trim();
        const rsi = this.toNumber(indicators.rsi);
        const mfi = this.toNumber(indicators.mfi);
        const macdDif = this.toNumber(indicators.macdDif);
        const macdDea = this.toNumber(indicators.macdDea);
        const macdHistogram = this.toNumber(indicators.macdHistogram);
        const cmf = this.toNumber(indicators.cmf);
        const atrPct = this.toNumber(indicators.atrPct);
        const obvStrength = this.toNumber(indicators.obvStrength);
        const aroonOscillator = this.toNumber(indicators.aroonOscillator);
        const superTrendLabel = String(indicators.superTrendLabel || '中性');
        const diagnosisText = diagnosisHint || 'KD 目前屬於整理盤，短線方向尚未完全表態。';

        if (kdDetail.highPassivation) {
            return {
                status: "🔥 高檔狂熱鈍化",
                statusShort: "高檔鈍化",
                desc: "指標進入軋空強勢期，K值已連續多日大於80。這段時間更像是趨勢延伸而不是終點，操作上以順勢持有、拉回不破支撐為主，直到動能明顯跌破 80 再評估減碼。",
                advice: "持股觀望，不追高，等待K值跌破80後再獲利了結",
                color: "#ff4d4f",
                bgColor: "#fff1f0",
                level: "極強",
                icon: "🔥",
                priority: 1
            };
        }

        if (kdDetail.lowPassivation) {
            return {
                status: "⚠️ 低檔疲弱鈍化",
                statusShort: "低檔鈍化",
                desc: "K值連續多日低於20，顯示動能極度疲弱。這不是急著抄底的訊號，而是提醒市場仍在消化賣壓；必須等黃金交叉、底背離或量價轉強同時出現，才有較高勝率的切入點。",
                advice: "避免抄底，等待黃金交叉或底背離訊號",
                color: "#faad14",
                bgColor: "#fffbe6",
                level: "極弱",
                icon: "⚠️",
                priority: 2
            };
        }

        if (kdDetail.isGoldenCross && kdDetail.nearFifty) {
            return {
                status: "⚡ 中軸強勢交叉",
                statusShort: "中軸金叉",
                desc: "KD 在 50 分界線附近完成黃金交叉，代表多方重新拿回主導權。這通常不是追高點，而是整理後的再發動，若同步看到 RSI 與 MACD 轉強，波段延續的可信度會更高。",
                advice: "可積極布局，波段中繼起漲點",
                color: "#73d13d",
                bgColor: "#f6ffed",
                level: "強",
                icon: "⚡",
                priority: 3
            };
        }

        if (kdDetail.isGoldenCross && kdDetail.isLowZone) {
            return {
                status: "✅ 低檔黃金交叉",
                statusShort: "低檔金叉",
                desc: "K 線在低檔區向上穿越 D 線，這是典型的打底訊號。若同時看到 MFI、CMF 轉正，代表籌碼與資金也開始站回多方，屬於可以分批布局的區域。",
                advice: "安全進場點，可分批建立多頭部位",
                color: "#52c41a",
                bgColor: "#f6ffed",
                level: "中強",
                icon: "✅",
                priority: 4
            };
        }

        if (kdDetail.lowDivergence) {
            return {
                status: "🚀 底背離轉折現形",
                statusShort: "底背離",
                desc: "股價仍在走低，但 KD 低點一底比一底高，代表動能已先行修復。若同步搭配 OBV 轉強與 ATR 收斂，通常是底部換手完成、反轉行情醞釀中的訊號。",
                advice: "波段見底訊號，可逢低分批布局",
                color: "#1890ff",
                bgColor: "#e6f7ff",
                level: "強",
                icon: "🚀",
                priority: 5
            };
        }

        if (kdDetail.isDeathCross && kdDetail.isHighZone) {
            return {
                status: "⬇️ 高檔死亡交叉",
                statusShort: "高檔死叉",
                desc: "K 線在高檔區向下穿越 D 線，代表短線買盤開始退潮。若同時 MACD 柱狀體縮短、RSI/MFI 轉弱，通常是波段獲利了結的較佳時點。",
                advice: "波段獲利了結，分批減碼",
                color: "#ff7a45",
                bgColor: "#fff2e8",
                level: "轉弱",
                icon: "⬇️",
                priority: 6
            };
        }

        if (kdDetail.isGoldenCross) {
            return {
                status: "📈 黃金交叉",
                statusShort: "金叉",
                desc: "K 線向上穿越 D 線，動能轉強。這是多頭重新接手的基本訊號，但仍需看 SuperTrend、CMF 與 MACD 是否同步翻多，才能確認是不是有效突破。",
                advice: "動能轉強，可視技術面決定是否追價",
                color: "#52c41a",
                bgColor: "#f6ffed",
                level: "中性偏多",
                icon: "📈",
                priority: 7
            };
        }

        if (kdDetail.isDeathCross) {
            return {
                status: "📉 死亡交叉",
                statusShort: "死叉",
                desc: "K 線向下穿越 D 線，短線動能轉弱。若 RSI、MFI 沒有止穩、且 CMF 仍在負值區，這通常不是假跌破，而是要先降風險的訊號。",
                advice: "動能轉弱，建議觀望或減碼",
                color: "#ff7a45",
                bgColor: "#fff2e8",
                level: "中性偏空",
                icon: "📉",
                priority: 8
            };
        }

        const isBullishBias = superTrendLabel === '多頭' || (Number.isFinite(cmf) && cmf >= 0.08) || (Number.isFinite(macdDif) && Number.isFinite(macdDea) && macdDif >= macdDea);
        const isBearishBias = superTrendLabel === '空頭' || (Number.isFinite(cmf) && cmf <= -0.08) || (Number.isFinite(macdDif) && Number.isFinite(macdDea) && macdDif < macdDea);
        const valueComment = [];
        valueComment.push(`KD 目前在 ${this.format2(Number(kdDetail.kValue))} / ${this.format2(Number(kdDetail.dValue))} 附近整理，屬於市場等待方向表態的階段。`);
        valueComment.push(`RSI ${this.format2(rsi)}、MFI ${this.format2(mfi)}，如果都落在中性區，代表多空尚未分出明顯勝負。`);
        if (Number.isFinite(macdDif) && Number.isFinite(macdDea)) {
            valueComment.push(`MACD ${macdDif >= macdDea ? '維持偏多結構' : '尚未完成翻多'}，柱狀體 ${Number.isFinite(macdHistogram) ? (macdHistogram >= 0 ? '偏正' : '偏負') : '未提供'}，顯示趨勢仍在確認。`);
        }
        if (isBullishBias && !isBearishBias) {
            valueComment.push('SuperTrend 與資金流仍偏多，這種盤整較像蓄勢，不像明顯出貨。');
        } else if (isBearishBias && !isBullishBias) {
            valueComment.push('趨勢與資金訊號偏弱，整理更像弱勢反彈或換手修復。');
        } else {
            valueComment.push('趨勢與資金流尚未同步，建議等突破箱頂或跌破箱底後再定方向。');
        }
        if (Number.isFinite(atrPct) && atrPct > 0) {
            valueComment.push(`ATR/收盤約 ${this.format2(atrPct)}%，${atrPct >= 6 ? '波動偏大，倉位要收斂。' : '波動相對收斂，適合耐心等待確認。'}`);
        }
        if (Number.isFinite(obvStrength)) {
            valueComment.push(`OBV 強度約 ${this.format2(obvStrength)}%，${obvStrength >= 12 ? '量價結構偏多。' : obvStrength <= -12 ? '量價結構偏空。' : '量能方向仍在觀察。'}`);
        }
        if (Number.isFinite(aroonOscillator)) {
            valueComment.push(`Aroon Oscillator 約 ${this.format2(aroonOscillator)}，${aroonOscillator >= 35 ? '趨勢仍偏多方主導。' : aroonOscillator <= -35 ? '趨勢仍偏空方主導。' : '趨勢仍在均衡帶。'}`);
        }
        valueComment.push(diagnosisText);

        return {
            status: "📊 盤整收斂區",
            statusShort: "盤整",
            desc: valueComment.join(' '),
            advice: "多空不明，建議等突破箱頂或跌破箱底後再定方向",
            color: "#bfbfbf",
            bgColor: "#fafafa",
            level: "中性",
            icon: "📊",
            priority: 9
        };
    },

    /**
     * 根據 KD 分析結果生成詳細資料
     *
     * @param {Object} analysisResult - 後端返回的分析結果
     * @returns {Object} KD 詳細資料
     */
    parseAnalysisResult(analysisResult) {
        const signals = analysisResult.signals || [];

        return {
            // 判斷高檔鈍化
            highPassivation: signals.some(s => s.includes('高檔鈍化')),

            // 判斷低檔鈍化
            lowPassivation: signals.some(s => s.includes('低檔鈍化')),

            // 判斷黃金交叉
            isGoldenCross: signals.some(s => s.includes('黃金交叉')),

            // 判斷死亡交叉
            isDeathCross: signals.some(s => s.includes('死亡交叉')),

            // 判斷是否接近中軸
            nearFifty: signals.some(s => s.includes('中軸')),

            // 判斷是否在低檔區
            isLowZone: signals.some(s => s.includes('低檔') && s.includes('黃金')),

            // 判斷是否在高檔區
            isHighZone: signals.some(s => s.includes('高檔') && (s.includes('鈍化') || s.includes('死亡'))),

            // 判斷低檔背離
            lowDivergence: signals.some(s => s.includes('背離'))
        };
    },

    /**
     * 生成 HTML 診斷卡片
     *
     * @param {Object} diagnostic - 診斷結果
     * @returns {string} HTML 字串
     */
    generateCard(diagnostic) {
        return `
            <div class="kd-diagnostic-card" style="
                background: ${diagnostic.bgColor};
                border-left: 5px solid ${diagnostic.color};
                border-radius: 12px;
                padding: 20px;
                margin: 15px 0;
            ">
                <div style="
                    font-size: 22px;
                    font-weight: 700;
                    color: ${diagnostic.color};
                    margin-bottom: 12px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                ">
                    ${diagnostic.status}
                    <span style="
                        font-size: 12px;
                        padding: 4px 10px;
                        background: ${diagnostic.color};
                        color: white;
                        border-radius: 12px;
                        font-weight: 600;
                    ">${diagnostic.level}</span>
                </div>
                <div style="
                    font-size: 15px;
                    line-height: 1.8;
                    color: #444;
                    background: rgba(255, 255, 255, 0.7);
                    padding: 12px;
                    border-radius: 8px;
                    margin-bottom: 10px;
                ">
                    ${diagnostic.desc}
                </div>
                <div style="
                    font-size: 14px;
                    color: ${diagnostic.color};
                    font-weight: 600;
                    padding: 10px;                    background: rgba(255, 255, 255, 0.9);
                    border-radius: 6px;
                    border-left: 3px solid ${diagnostic.color};
                ">
                    💡 操作建議：${diagnostic.advice}
                </div>
            </div>
        `;
    },

    /**
     * 生成簡化版標籤
     *
     * @param {Object} diagnostic - 診斷結果
     * @returns {string} HTML 字串
     */
    generateBadge(diagnostic) {
        return `
            <span style="
                display: inline-block;
                padding: 6px 14px;
                background: ${diagnostic.color};
                color: white;
                border-radius: 16px;
                font-size: 13px;
                font-weight: 600;
                margin: 4px;
            ">
                ${diagnostic.icon} ${diagnostic.statusShort}
            </span>
        `;
    },

    /**
     * 根據評分獲取動能等級
     *
     * @param {number} score - KD 評分
     * @returns {Object} 等級信息
     */
    getScoreLevel(score) {
        if (score >= 20) {
            return {
                level: "極強",
                color: "#52c41a",
                icon: "🔥",
                desc: "極端多頭動能"
            };
        } else if (score >= 10) {
            return {
                level: "強",
                color: "#73d13d",
                icon: "✅",
                desc: "多頭動能強勁"
            };
        } else if (score >= -5) {
            return {
                level: "中性",
                color: "#bfbfbf",
                icon: "📊",
                desc: "多空均衡"
            };
        } else if (score >= -10) {
            return {
                level: "弱",
                color: "#ff7a45",
                icon: "⚠️",
                desc: "空頭動能轉強"
            };
        } else {
            return {
                level: "極弱",
                color: "#ff4d4f",
                icon: "❌",
                desc: "極端空頭動能"
            };
        }
    },

    /**
     * 格式化評分顯示
     *
     * @param {number} score - KD 評分
     * @returns {string} 格式化後的評分字串
     */
    formatScore(score) {
        return score >= 0 ? `+${score}` : `${score}`;
    }
};

// 如果在 Node.js 環境中，導出模組
if (typeof module !== 'undefined' && module.exports) {
    module.exports = KdDiagnostic;
}

// 如果在瀏覽器環境中，掛載到 window 對象
if (typeof window !== 'undefined') {
    window.KdDiagnostic = KdDiagnostic;
}
