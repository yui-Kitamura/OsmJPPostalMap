package pro.eng.yui.android.osmjppostalmap.search;

import java.util.List;

public interface SearchEngine {
    List<SearchResult> search(String query);
}
