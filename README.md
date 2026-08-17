# Spring Bootで学ぶBigDecimalのキー等価性デバッグラボ

Spring Bootサービスの価格検索を題材に、`BigDecimal` の `equals` と `compareTo` の契約差が `HashMap` のキー検索へ与える影響を最小再現します。失敗テスト、観測結果、正規化による最小修正、回帰テストを二つのGitコミットに分離しています。

## 前提環境

| 項目 | 固定値 |
|---|---|
| Java | 21.0.11 |
| Spring Boot | 3.3.5 |
| ビルド | Maven 3.8.7 以上 |
| 実行基盤 | Spring Boot Test とインメモリのApplicationContext |

## 再現

修正前の失敗状態では、`1.0` をキーとして登録し、`1.00` で検索します。

```bash
git checkout 326a090
mvn test
```

失敗時は `compareTo()` が数値的同値を示す一方、`HashMap` 検索結果が `null` になります。

## 修正後の確認

```bash
git checkout 5c1d2f1
mvn clean test
```

修正では登録時と検索時に `stripTrailingZeros()` を適用し、業務上の金額キーを一つの正規形へ揃えています。全2テストが成功します。

## ファイル

| パス | 役割 |
|---|---|
| `src/main/java/com/example/bigdecimallab/price/PriceCatalog.java` | BigDecimalキーを扱うSpringサービス |
| `src/test/java/com/example/bigdecimallab/price/PriceCatalogTest.java` | 利用者視点の検索テストと言語契約の対照テスト |
| `docs/debug-investigation.md` | 日本語記事本文 |
| `docs/novelty-report.md` | 既存Java教材との差分記録 |
| `evidence/bug-test-output.txt` | 修正前の失敗出力 |
| `evidence/fixed-test-output.txt` | 修正後の成功出力 |

## コミット履歴

```text
326a090 bug: use BigDecimal directly as a HashMap key
5c1d2f1 fix: normalize BigDecimal keys before lookup
```
