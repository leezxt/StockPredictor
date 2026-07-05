# Strategy Parameter Table

This table documents all score weights now externalized in `application.properties` under `scoring.*`.

## Hot-tuning workflow

1. Edit values in `src/main/resources/application.properties`.
2. Trigger hot reload endpoint to apply values immediately (no restart):

```bash
curl -X POST http://localhost:8080/api/config/reload
```

3. Re-run one symbol (`/api/stocks/{symbol}/radar`) and compare `moneySource` and six-dimension scores.

---

## 1) Fusion Weights (Money)

| Key | Default | Suggested Range | Effect |
|---|---:|---:|---|
| `scoring.money.weight.institutional` | 0.70 | 0.50 ~ 0.85 | Higher = trust/institutional flow dominates money score. |
| `scoring.money.weight.big-holder` | 0.30 | 0.15 ~ 0.50 | Higher = large-holder concentration dominates anti-fake filter. |

> Keep sum near `1.0`.

---

## 2) Trend

| Key Prefix | Typical Range | Notes |
|---|---:|---|
| `scoring.trend.bonus.*` | 5 ~ 30 | Rewards bullish MA structure and healthy distance. |
| `scoring.trend.penalty.*` | 5 ~ 30 | Punishes bearish MA structure, overheat, or breakdown. |

Major levers:
- `scoring.trend.bonus.ma20-above-ma60`: medium-term trend confirmation.
- `scoring.trend.penalty.distance-broken`: strongest downside punishment when far below MA20.
- `scoring.trend.penalty.distance-bubble`: over-extension guard in euphoric phases.

---

## 3) Momentum

| Key Prefix | Typical Range | Notes |
|---|---:|---|
| `scoring.momentum.bonus.rsi-*` | 8 ~ 40 | Strength by RSI regime. |
| `scoring.momentum.penalty.rsi-*` | 8 ~ 25 | Overheat or extreme-weak correction. |
| `scoring.momentum.bonus.macd-*` | 5 ~ 25 | Positive/expanding MACD acceleration. |
| `scoring.momentum.penalty.macd-*` | 4 ~ 20 | Negative MACD drag. |
| `scoring.momentum.bonus.kd-*` | 5 ~ 25 | Golden cross / low-zone reversal reward. |
| `scoring.momentum.penalty.kd-*` | 5 ~ 20 | Death cross risk control. |

---

## 4) Money (Institutional Sub-score)

| Key Prefix | Typical Range | Notes |
|---|---:|---|
| `scoring.money.bonus.trust-days-*` | 5 ~ 45 | Consecutive trust buying signal. |
| `scoring.money.penalty.trust-days-0` | 5 ~ 15 | No-trust-flow penalty. |
| `scoring.money.bonus.lock-ratio-*` | 8 ~ 40 | Lock ratio conviction reward. |
| `scoring.money.penalty.lock-ratio-low` | 5 ~ 15 | Weak lock ratio penalty. |
| `scoring.money.bonus.net-buy-*` | 8 ~ 20 | Positive net-buy and acceleration reward. |
| `scoring.money.penalty.net-buy-*` | 5 ~ 15 | Distribution and weak average penalties. |

---

## 5) Volatility

| Key | Default | Suggested Range | Effect |
|---|---:|---:|---|
| `scoring.volatility.base-when-range-invalid` | 50 | 35 ~ 65 | Neutral fallback when BBW range invalid. |
| `scoring.volatility.squeeze-scale` | 70.0 | 50 ~ 90 | Sensitivity to squeeze quality. |
| `scoring.volatility.bonus.opening` | 20 | 8 ~ 25 | Reward opening after squeeze. |
| `scoring.volatility.bonus.low-band` | 10 | 5 ~ 15 | Reward low-volatility setup. |
| `scoring.volatility.penalty.expanded` | 8 | 4 ~ 15 | Punish expansion risk. |
| `scoring.volatility.penalty.over-expanded` | 15 | 8 ~ 25 | Punish over-expanded instability. |
| `scoring.volatility.penalty.opening-over-expanded` | 6 | 3 ~ 12 | Extra caution if opening occurs too late. |

---

## 6) Context (Market Breadth)

| Key | Default | Suggested Range | Effect |
|---|---:|---:|---|
| `scoring.context.base-score` | 50 | 40 ~ 60 | Neutral center in context scoring. |
| `scoring.context.bonus.breadth-*` | 10 ~ 50 | Boost when broad market supports trend following. |
| `scoring.context.penalty.breadth-*` | 5 ~ 40 | Cut score in weak market climate. |

---

## 7) Fundamental

| Key Prefix | Typical Range | Notes |
|---|---:|---|
| `scoring.fundamental.bonus.*` | 3 ~ 12 | Rewards strong YoY / MoM growth. |
| `scoring.fundamental.penalty.*` | 5 ~ 15 | Penalizes deterioration. |

---

## Safe tuning rules

- Change one dimension at a time, then observe 20~50 symbols.
- Keep total behavior stable: avoid pushing multiple penalties to max simultaneously.
- If scores collapse too often, reduce strongest penalties first (`distance-broken`, `kd-death`, `breadth-0`).
- If false positives are high, increase risk penalties first (overheat/over-expanded/negative flow).

