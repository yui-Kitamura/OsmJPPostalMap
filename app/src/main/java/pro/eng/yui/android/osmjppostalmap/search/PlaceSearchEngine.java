package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        String trimmedQuery = query.trim();
        List<PlaceInfo> places = repository.searchPlaces(trimmedQuery);
        List<SearchResult> results = new ArrayList<>();

        for (PlaceInfo place : places) {
            double weight = 40.0;
            if (place.getName().equals(trimmedQuery)) {
                weight = 60.0;
            }

            Double lat = place.getLat();
            Double lon = place.getLon();

            results.add(new SearchResult(
                    SearchResult.Type.PLACE,
                    place.getName(),
                    repository.getPrefectureName(place.getPrefCode()),
                    lat,
                    lon,
                    weight,
                    place
            ));
        }

        Collections.sort(results);
        return results;
    }
}
