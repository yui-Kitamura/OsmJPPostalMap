package pro.eng.yui.android.osmjppostalmap.domain.model;

/**
 * ローカルに保存済みの都道府県のメタ情報。
 * 更新ダイアログでの「最終更新日」表示に使用する。
 */
public class PrefMeta {
    private final int prefCode;
    private final String name;
    /** epoch millis（ローカルで取得/更新した日時） */
    private final long lastUpdated;

    public PrefMeta(int prefCode, String name, long lastUpdated) {
        this.prefCode = prefCode;
        this.name = name;
        this.lastUpdated = lastUpdated;
    }

    public int getPrefCode() { return prefCode; }
    public String getName() { return name; }
    public long getLastUpdated() { return lastUpdated; }
}
