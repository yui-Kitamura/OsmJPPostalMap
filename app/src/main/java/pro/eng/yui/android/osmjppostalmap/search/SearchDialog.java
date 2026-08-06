package pro.eng.yui.android.osmjppostalmap.search;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.Util;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

public class SearchDialog extends DialogFragment {

    public interface OnResultSelectedListener {
        void onPostOfficeSelected(OsmPoi poi);
        void onPlaceCenterSelected(SearchResult result);
        void onPlaceAreaSelected(SearchResult result);
        void onAddressSelected(OsmPoi poi);
    }

    private OnResultSelectedListener listener;
    private PoiRepository repository;
    private List<SearchEngine> engines;
    private SearchAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentQuery = "";
    private CompoundButton checkPostOffice;
    private CompoundButton checkAddress;
    private CompoundButton checkPlace;
    private ProgressBar searchProgress;

    public void setOnResultSelectedListener(OnResultSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = PoiRepositoryImpl.getInstance();
        engines = new ArrayList<>();
        engines.add(new PostOfficeSearchEngine(repository));
        engines.add(new AddressSearchEngine(repository));
        engines.add(new PlaceSearchEngine(repository));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_search, container, false);

        EditText input = view.findViewById(R.id.search_input);
        checkPostOffice = view.findViewById(R.id.check_post_office);
        checkAddress = view.findViewById(R.id.check_address);
        checkPlace = view.findViewById(R.id.check_place);
        searchProgress = view.findViewById(R.id.search_progress);
        RecyclerView resultsList = view.findViewById(R.id.search_results);

        resultsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SearchAdapter();
        resultsList.setAdapter(adapter);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                performSearch(currentQuery);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        applyPlaceholderStyle(getContext(), input);

        checkPostOffice.setOnCheckedChangeListener((buttonView, isChecked) -> performSearch(currentQuery));
        checkAddress.setOnCheckedChangeListener((buttonView, isChecked) -> performSearch(currentQuery));
        checkPlace.setOnCheckedChangeListener((buttonView, isChecked) -> performSearch(currentQuery));

        view.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dismiss());

        return view;
    }

    private void performSearch(final String query) {
        if (query.trim().isEmpty()) {
            adapter.setResults(Collections.emptyList());
            if (searchProgress != null) searchProgress.setVisibility(View.GONE);
            return;
        }

        if (searchProgress != null) searchProgress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            boolean searchPO = checkPostOffice.isChecked();
            boolean searchAddress = checkAddress.isChecked();
            boolean searchPlace = checkPlace.isChecked();

            Location currentLoc = null;
            if (repository.getLocationLiveData() != null) {
                currentLoc = repository.getLocationLiveData().getValue();
            }

            Map<String, SearchResult> resultMap = new HashMap<>();

            // Phase 1: Priority Results (City/Town start match, Post Office name start match)
            for (SearchEngine engine : engines) {
                if (engine instanceof PostOfficeSearchEngine && !searchPO) continue;
                if (engine instanceof PlaceSearchEngine && !searchPlace) continue;
                if (engine instanceof AddressSearchEngine) continue; // Not priority

                List<SearchResult> engineResults = engine.search(query, currentLoc);
                for (SearchResult res : engineResults) {
                    // Only include prefix matches in Phase 1
                    boolean isPrefixMatch = false;
                    if (res.getType() == SearchResult.Type.POST_OFFICE || res.getType() == SearchResult.Type.POST_BOX) {
                        if (res.getTitle().startsWith(query)) isPrefixMatch = true;
                    } else if (res.getType() == SearchResult.Type.PLACE) {
                        if (res.getTitle().startsWith(query)) isPrefixMatch = true;
                    }

                    if (isPrefixMatch) {
                        addToResultMap(resultMap, res);
                    }
                }
            }

            final List<SearchResult> phase1Results = new ArrayList<>(resultMap.values());
            Collections.sort(phase1Results);

            mainHandler.post(() -> {
                if (query.equals(currentQuery)) {
                    adapter.setResults(phase1Results);
                }
            });

            // Phase 2: Other matches
            for (SearchEngine engine : engines) {
                if (engine instanceof PostOfficeSearchEngine && !searchPO) continue;
                if (engine instanceof AddressSearchEngine && !searchAddress) continue;
                if (engine instanceof PlaceSearchEngine && !searchPlace) continue;

                List<SearchResult> engineResults = engine.search(query, currentLoc);
                for (SearchResult res : engineResults) {
                    addToResultMap(resultMap, res);
                }
            }

            final List<SearchResult> allResults = new ArrayList<>(resultMap.values());
            Collections.sort(allResults);

            mainHandler.post(() -> {
                if (query.equals(currentQuery)) {
                    adapter.setResults(allResults);
                    if (searchProgress != null) searchProgress.setVisibility(View.GONE);
                }
            });
        });
    }

    private void addToResultMap(Map<String, SearchResult> resultMap, SearchResult res) {
        String key;
        if (res.getOriginalData() instanceof OsmPoi) {
            key = "POI_" + ((OsmPoi) res.getOriginalData()).getId();
        } else {
            key = res.getType() + "_" + res.getTitle() + "_" + res.getLat() + "_" + res.getLon();
        }

        if (!resultMap.containsKey(key) || resultMap.get(key).getWeight() < res.getWeight()) {
            resultMap.put(key, res);
        }
    }

    private void applyPlaceholderStyle(android.content.Context context, EditText input) {
        CharSequence hint = input.getHint();
        if (hint == null) { return; }
        SpannableString styled = new SpannableString(hint.toString());
        styled.setSpan(new StyleSpan(Typeface.ITALIC), 0, styled.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        input.setHint(styled);
        input.setHintTextColor(ContextCompat.getColor(context, R.color.input_placeholder));
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final List<SearchResult> results = new ArrayList<>();

        public void setResults(List<SearchResult> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = results.get(position);
            holder.bind(result);
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title;
            TextView subtitle;
            Button btnShow;
            Button btnShowCenter;
            Button btnShowAll;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.result_icon);
                title = itemView.findViewById(R.id.result_title);
                subtitle = itemView.findViewById(R.id.result_subtitle);
                btnShow = itemView.findViewById(R.id.btn_show);
                btnShowCenter = itemView.findViewById(R.id.btn_show_center);
                btnShowAll = itemView.findViewById(R.id.btn_show_all);
            }

            void bind(final SearchResult result) {
                String q = currentQuery != null ? currentQuery.trim() : "";
                
                String displayTitle;
                if (result.getType() == SearchResult.Type.POST_OFFICE && result.getNameJaHira() != null) {
                    displayTitle = Util.getRubySpannable(result.getTitle(), result.getNameJaHira(), title.getTextSize()).toString();
                } else {
                    displayTitle = result.getTitle();
                }

                if (!q.isEmpty()) {
                    title.setText(highlightText(displayTitle, q));
                    subtitle.setText(highlightText(result.getSubTitle(), q));
                } else {
                    title.setText(displayTitle);
                    subtitle.setText(result.getSubTitle());
                }

                // Set icon
                if (result.getType() == SearchResult.Type.POST_OFFICE || result.getType() == SearchResult.Type.POST_BOX) {
                    icon.setImageDrawable(new SearchResultIconDrawable(itemView.getContext(), result.getType(), result.getSchedule(), result.getLimitedServiceSchedule()));
                } else {
                    switch (result.getType()) {
                        case PLACE:
                        case ADDRESS:
                        default:
                            icon.setImageResource(R.drawable.ic_search_pin_blue);
                            break;
                    }
                }

                btnShow.setVisibility(View.GONE);
                btnShowCenter.setVisibility(View.GONE);
                btnShowAll.setVisibility(View.GONE);

                if (result.getType() == SearchResult.Type.POST_OFFICE || result.getType() == SearchResult.Type.POST_BOX) {
                    btnShow.setVisibility(View.VISIBLE);
                    btnShow.setOnClickListener(v -> {
                        dismiss();
                        if (listener != null) listener.onPostOfficeSelected((OsmPoi) result.getOriginalData());
                    });
                } else if (result.getType() == SearchResult.Type.ADDRESS) {
                    btnShow.setVisibility(View.VISIBLE);
                    btnShow.setOnClickListener(v -> {
                        dismiss();
                        if (listener != null) listener.onAddressSelected((OsmPoi) result.getOriginalData());
                    });
                } else if (result.getType() == SearchResult.Type.PLACE) {
                    if (result.getLat() != null && result.getLon() != null) {
                        btnShowCenter.setVisibility(View.VISIBLE);
                    }
                    btnShowAll.setVisibility(View.VISIBLE);
                    btnShowCenter.setOnClickListener(v -> {
                        dismiss();
                        if (listener != null) listener.onPlaceCenterSelected(result);
                    });
                    btnShowAll.setOnClickListener(v -> {
                        dismiss();
                        if (listener != null) listener.onPlaceAreaSelected(result);
                    });
                }
            }
            private CharSequence highlightText(String text, String query) {
                if (text == null || query == null || query.isEmpty()) return text;
                SpannableString spannable = new SpannableString(text);
                int start = text.toLowerCase().indexOf(query.toLowerCase());
                while (start >= 0) {
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, start + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = text.toLowerCase().indexOf(query.toLowerCase(), start + query.length());
                }
                return spannable;
            }
        }
    }
}
