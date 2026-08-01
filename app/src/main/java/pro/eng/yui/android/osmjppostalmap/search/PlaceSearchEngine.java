package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class PlaceSearchEngine implements SearchEngine {
    private final PoiRepository repository;

    public PlaceSearchEngine(PoiRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<PlaceInfo> places = repository.searchPlaces(query);
        List<SearchResult> results = new ArrayList<>();

        for (PlaceInfo place : places) {
            double weight = 0.5;
            if (place.getName().equals(query) || place.getNameKana().equals(query)) {
                weight = 1.0;
            }

            results.add(new SearchResult(
                    SearchResult.Type.PLACE,
                    place.getName(),
                    place.getIsIn(),
                    place.getLat(),
                    place.getLon(),
                    weight,
                    place
            ));
        }

        Collections.sort(results);
        return results;
    }
}
