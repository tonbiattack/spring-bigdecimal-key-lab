# 題材重複調査記録

## 調査範囲

Repository Catalogはこの環境に存在しなかったため、選択済みの `tonbiattack/qiita` と、既存の非公開Java教材リポジトリ `tonbiattack/spring-transaction-self-invocation-lab` のGitHubツリー、README、主要テスト名、コミット履歴を直接確認しました。後者には、トランザクション境界を別Beanへ移す教材、チェック例外のロールバック、MyBatisのキャッシュ・自動マッピング・動的SQL・`selectOne` に関する複数ラボが存在します。

当初案の「Spring `@Transactional` 自己呼び出し」は、直接原因がproxy境界、観測契約がトランザクション状態とロールバック後の行数、最小修正が別Bean分離であり、既存の `トランザクション境界を別Beanへ移す` と四軸すべてが近いため採用しませんでした。

## 採用題材

採用した契約は、次の一文で表せます。

> `1.0` と `1.00` は金額として同じものとして検索できるべきだが、`BigDecimal` をそのまま `HashMap` のキーにすると検索できない。

## 四軸比較

| 比較軸 | 既存の近接題材 | 今回の題材 | 判定 |
|---|---|---|---|
| 直接原因 | Spring proxy、トランザクション境界、例外規則、またはMyBatisの挙動 | Java `BigDecimal` の `equals` がscaleを考慮し、`compareTo` は数値だけを比較する契約 | 異なる |
| 実境界 | トランザクション付きサービス、MyBatis Mapper、SQL実行 | Spring Beanの価格検索メソッドと、`HashMap` のキー照合 | 異なる |
| 観測契約 | ロールバック後のDB行数、SQL結果、キャッシュ値 | `1.00` に対する商品名、`compareTo()==0` と `equals()==false` の対照 | 異なる |
| 最小修正 | 別Bean分離、`rollbackFor`、Mapper設定など | 登録・検索の両方で `stripTrailingZeros()` を適用 | 異なる |

## 既存記事との差分

QiitaリポジトリにはSpringのトランザクション一般論、Spring Data JPAの楽観ロック、JavaとGoのトランザクション比較がありました。しかし、これらは `BigDecimal` のscale、`equals`、`compareTo`、ハッシュベースコレクションのキー契約を中心に扱っていません。本記事はSpring Bootを実行コンテキストとして使いますが、失敗の直接原因はSpringのトランザクションやDBではなく、Java標準ライブラリの値等価性契約です。

## 判定

当初案は重複のため破棄し、BigDecimal題材へ変更しました。採用題材は既存教材と、原因・実境界・観測契約・最小修正のすべてが異なるため、同じコードやデータの言い換えではありません。
