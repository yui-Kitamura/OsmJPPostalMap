package pro.eng.yui.android.osmjppostalmap.search;

import android.location.Location;
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
    public List<SearchResult> search(String query, Location currentLoc) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String trimmedQuery = query.trim();
        List<PlaceInfo> places = repository.searchPlaces(trimmedQuery);
        List<SearchResult> results = new ArrayList<>();

        for (PlaceInfo place : places) {
            double weight = 60.0;
            if (place.getName().equals(trimmedQuery)) {
                weight = 100.0;
            } else if (place.getName().startsWith(trimmedQuery)) {
                weight = 95.0;
            } else if (place.getName().contains(trimmedQuery)) {
                weight = 90.0;
            }

            Double lat = place.getLat();
            Double lon = place.getLon();

            // 距離による重みづけ (誘導用)
            if (currentLoc != null && lat != null && lon != null) {
                float[] distResults = new float[1];
                Location.distanceBetween(currentLoc.getLatitude(), currentLoc.getLongitude(), lat, lon, distResults);
                double distanceKm = distResults[0] / 1000.0;
                weight += 1.0 / (1.0 + distanceKm);
            }

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
