package pro.eng.yui.android.osmjppostalmap.search;

import android.location.Location;
import java.util.List;

public interface SearchEngine {
    List<SearchResult> search(String query, Location currentLoc);
}
