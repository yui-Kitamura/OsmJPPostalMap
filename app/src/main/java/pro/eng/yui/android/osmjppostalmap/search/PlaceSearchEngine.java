package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.List;

public class PlaceSearchEngine implements SearchEngine {
    @Override
    public List<SearchResult> search(String query) {
        // TODO: DataSourceに地名検索向けのjsonを用意中。今は検索実体は実装しない
        return new ArrayList<>();
    }
}
