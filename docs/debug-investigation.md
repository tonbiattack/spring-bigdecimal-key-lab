# Spring Bootで価格検索が見つからない理由：BigDecimalの`equals`と`compareTo`を最小再現から理解する

## この記事で扱う問題

Spring Bootのサービスで、`1.0` と `1.00` を同じ金額として扱いたいのに、価格検索が `null` を返す問題を扱います。失敗の見た目はSpringのDIやテスト設定にありそうですが、直接原因はJavaの `BigDecimal` が数値の値とscaleを別々に保持し、`equals` と `compareTo` で異なる等価性を定義していることです。[1]

本記事では、`BigDecimal` を `HashMap` のキーに直接使う最小のSpring Bootサービスを作り、失敗する利用者視点のテスト、言語契約の対照テスト、競合仮説の比較、正規化による最小修正、回帰確認を行います。

## 既存題材との差分

既存のJava・Spring教材には、Springのトランザクション境界、チェック例外のロールバック、MyBatisのキャッシュや自動マッピング、Spring Data JPAの楽観ロックを扱う記事・ラボがあります。今回の直接原因はそれらとは異なり、**Java標準ライブラリにおけるBigDecimalの等価性とハッシュキー契約**です。Spring Bootは実行コンテキストとテスト基盤として使いますが、DB、トランザクション、HTTP、MyBatisには依存しません。詳細な比較は `docs/novelty-report.md` に残しています。

## 期待していた挙動と実際の挙動

価格カタログには `1.0` の商品を登録しています。金額としては `1.00` も同じなので、`findProduct(new BigDecimal("1.00"))` は `coffee` を返すのが自然な期待です。

| 観点 | 結果 |
|---|---|
| `new BigDecimal("1.0").compareTo(new BigDecimal("1.00"))` | `0` |
| `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` | `false` |
| `HashMap` に `1.0` を登録して `1.00` で検索 | `null` |
| 修正後の同じ検索 | `coffee` |

「数値として同じ」と「Javaオブジェクトの `equals` がtrue」は同じ意味ではありません。`HashMap` はキーのハッシュ値と `equals` に基づいて検索するため、数値的に同じでもscaleが異なるキーは一致しません。[2]

## 最小再現プロジェクト

プロジェクトはSpring Boot 3.3.5、Java 21、Mavenで固定しています。重要なファイルは次のとおりです。

```text
src/main/java/com/example/bigdecimallab/
├── BigDecimalLabApplication.java
└── price/PriceCatalog.java
src/test/java/com/example/bigdecimallab/price/PriceCatalogTest.java
```

修正前のサービスは、値をそのままHashMapキーへ入出力します。

```java
@Service
public class PriceCatalog {
    private final Map<BigDecimal, String> prices = new HashMap<>();

    public PriceCatalog() {
        prices.put(new BigDecimal("1.0"), "coffee");
    }

    public String findProduct(BigDecimal amount) {
        return prices.get(amount);
    }
}
```

次のコマンドで失敗を再現できます。

```bash
git checkout 326a090
mvn test
```

保存した実行結果には、次のアサーション差分が含まれます。

```text
expected: "coffee"
 but was: null
Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

一方、同じテストクラスにある対照テストは、検索問題を言語契約へ分解します。

```java
assertThat(onePointZero.compareTo(onePointZeroZero)).isZero();
assertThat(onePointZero.equals(onePointZeroZero)).isFalse();
```

このテストが成功していることから、失敗は比較処理の実装ミスではなく、二つの比較メソッドの仕様差をMapキーの用途へ持ち込んだことだと分かります。

## 調査：何を観測し、どの仮説を除外したか

原因候補を二つに分け、入力と観測結果を固定しました。

| 仮説 | 予測 | 最小実験 | 結果 | 判定 |
|---|---|---|---|---|
| A: Spring BootのBean生成またはテストContextが別のMapを作っている | `1.0` と `1.00` の比較テスト自体は等しくなるか、毎回不安定になる | 同一サービスのコンストラクタ登録と検索を実行する | `compareTo` と `equals` の対照は安定し、検索だけが `null` | 棄却 |
| B: BigDecimalのscaleを含む `equals` がHashMapキー照合に使われる | 数値的には同じでもscaleが違うと検索に失敗する | `1.0` を登録し `1.00` を検索する | `null`、`compareTo()==0`、`equals()==false` | 採用 |

Java公式Javadocは、`BigDecimal.equals` について、`compareTo` と異なり値だけでなくscaleも等しい場合に限って等しいと説明しています。[1] また、`compareTo` は数値的に等しいBigDecimalを同じ順序として扱います。[1] したがって `1.0` と `1.00` は `compareTo` では同値ですが、`equals` とハッシュ値の観点では異なるオブジェクトです。

`HashMap.get` の仕様は、指定キーに対応する値を返し、対応するマッピングがなければ `null` を返します。キーの照合はMapの契約に従うため、`BigDecimal` の `equals` とhashCodeの関係がそのまま結果へ反映されます。[2]

## 修正：なぜ正規化で直るのか

金額検索の契約を「scaleではなく数値で同じなら同じ商品」と定義するなら、Mapへ入れる前と検索する前にキーを同じ正規形へ変換します。今回の最小修正は `stripTrailingZeros()` の共通利用です。

```java
@Service
public class PriceCatalog {
    private final Map<BigDecimal, String> prices = new HashMap<>();

    public PriceCatalog() {
        prices.put(normalize(new BigDecimal("1.0")), "coffee");
    }

    public String findProduct(BigDecimal amount) {
        return prices.get(normalize(amount));
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.stripTrailingZeros();
    }
}
```

`1.0` と `1.00` はどちらも正規化後に同じキーになるため、HashMapが使う `equals` とhashCodeの比較が一致します。修正の中心はMapの種類を変えることではなく、**業務上の等価性を、コレクションが使うキー等価性へ変換すること**です。

`TreeMap` を `BigDecimal::compareTo` のComparator付きで使う方法もあります。ただし、Comparatorによる順序が `equals` と一致しないことを明示的に理解する必要があります。金額キーがシリアライズ、ログ、別Map、キャッシュキーへ広がるなら、境界で正規化するほうが契約を共有しやすい場合があります。どちらを選ぶかは、アプリケーション全体の金額表現の契約として決めるべきです。

## 回帰テスト

修正後は次のコマンドを実行しました。

```bash
git checkout 5c1d2f1
mvn clean test
```

結果は次のとおりです。

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

元の検索テストは残したまま、`compareTo` と `equals` の対照テストも維持しています。前者は利用者が期待する商品検索契約、後者は将来の実装変更でJavaの比較規則を取り違えないための回帰テストです。

Spring Bootの `@SpringBootTest` はアプリケーションの設定を読み込んだテスト用ApplicationContextを構築するため、ここではサービスを実際のBeanとして検証できます。[3] ただし、失敗の証拠はSpringのContextが作れたことではなく、Context上で生成されたサービスがJavaのMapキー契約に従って `null` を返したことです。

## まとめ

覚えるべき判断規則は三つです。

1. `BigDecimal.compareTo()==0` は数値的同値を示すが、`equals()==true` を意味しません。[1]
2. `HashMap` のキーでは `equals` とhashCodeの契約が検索結果を決めるため、scaleを含むBigDecimalをそのまま金額キーにしないよう注意します。[2]
3. 「金額として同じ」の定義がscale非依存なら、登録・検索の両境界で同じ正規化を適用します。

## 参考資料

[1]: https://docs.oracle.com/javase/8/docs/api/java/math/BigDecimal.html "Java Platform SE: BigDecimal"

[2]: https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html "Java Platform SE: HashMap"

[3]: https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html "Spring Boot Reference: Testing Spring Boot Applications"
