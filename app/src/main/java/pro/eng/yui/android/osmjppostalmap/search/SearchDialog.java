package pro.eng.yui.android.osmjppostalmap.search;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
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
        engines.add(new PlaceSearchEngine());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_search, container, false);

        EditText input = view.findViewById(R.id.search_input);
        CheckBox checkPostOffice = view.findViewById(R.id.check_post_office);
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

        checkPostOffice.setOnCheckedChangeListener((buttonView, isChecked) -> performSearch(currentQuery));

        return view;
    }

    private void performSearch(final String query) {
        if (query.trim().isEmpty()) {
            adapter.setResults(Collections.emptyList());
            return;
        }

        executor.execute(() -> {
            List<SearchResult> allResults = new ArrayList<>();
            for (SearchEngine engine : engines) {
                allResults.addAll(engine.search(query));
            }
            Collections.sort(allResults);
            
            mainHandler.post(() -> {
                if (query.equals(currentQuery)) {
                    adapter.setResults(allResults);
                }
            });
        });
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
            TextView title;
            TextView subtitle;
            Button btnShow;
            Button btnShowCenter;
            Button btnShowAll;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.result_title);
                subtitle = itemView.findViewById(R.id.result_subtitle);
                btnShow = itemView.findViewById(R.id.btn_show);
                btnShowCenter = itemView.findViewById(R.id.btn_show_center);
                btnShowAll = itemView.findViewById(R.id.btn_show_all);
            }

            void bind(final SearchResult result) {
                title.setText(result.getTitle());
                subtitle.setText(result.getSubTitle());

                btnShow.setVisibility(View.GONE);
                btnShowCenter.setVisibility(View.GONE);
                btnShowAll.setVisibility(View.GONE);

                if (result.getType() == SearchResult.Type.POST_OFFICE) {
                    btnShow.setVisibility(View.VISIBLE);
                    btnShow.setOnClickListener(v -> {
                        if (listener != null) listener.onPostOfficeSelected((OsmPoi) result.getOriginalData());
                        dismiss();
                    });
                } else if (result.getType() == SearchResult.Type.ADDRESS) {
                    btnShow.setVisibility(View.VISIBLE);
                    btnShow.setOnClickListener(v -> {
                        if (listener != null) listener.onAddressSelected((OsmPoi) result.getOriginalData());
                        dismiss();
                    });
                } else if (result.getType() == SearchResult.Type.PLACE) {
                    btnShowCenter.setVisibility(View.VISIBLE);
                    btnShowAll.setVisibility(View.VISIBLE);
                    btnShowCenter.setOnClickListener(v -> {
                        if (listener != null) listener.onPlaceCenterSelected(result);
                        dismiss();
                    });
                    btnShowAll.setOnClickListener(v -> {
                        if (listener != null) listener.onPlaceAreaSelected(result);
                        dismiss();
                    });
                }
            }
        }
    }
}
