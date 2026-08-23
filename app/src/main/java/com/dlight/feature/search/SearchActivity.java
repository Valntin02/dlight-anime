package com.dlight.feature.search;



import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.model.VodData;
import com.dlight.ui.widget.LoadStateView;
import com.dlight.util.Param;

import java.util.*;

import retrofit2.Call;
import com.dlight.data.model.VodResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class SearchActivity extends AppCompatActivity {

    private EditText editTextSearch;
    private Button btnSearch;
    private RecyclerView recyclerViewResults;
    private LoadStateView searchLoadState;
    private LinearLayout historyContainer;
    private TextView textHistory,textClear;
    private SharedPreferences preferences;
    private final String HISTORY_KEY = "search_history";

    private ListView listViewSuggestions;
    private ArrayAdapter<String> suggestionAdapter;
    private List<String> suggestionList = new ArrayList<>();

    private List<String> historyList = new ArrayList<>();
    private SearchResultAdapter adapter;
    private List<VodData> videoResultList = new ArrayList<>();
    private final SearchRequestTracker searchRequestTracker = new SearchRequestTracker();
    private Call<VodResModel> activeSearchCall;
    private Call<Map<String, Object>> activeSuggestionCall;
    private int suggestionGeneration;
    private boolean destroyed;

    //用来处理点击联想列表的关闭不了的bug
    private boolean isSettingText = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        //设置状态栏透明
        Param.setStatusBarTransparent(this, false, getResources().getColor(R.color.dark_bg));

        editTextSearch = findViewById(R.id.editTextSearch);
        btnSearch = findViewById(R.id.btnSearch);
        recyclerViewResults = findViewById(R.id.recyclerViewResults);
        searchLoadState = findViewById(R.id.search_load_state);
        historyContainer = findViewById(R.id.historyContainer);
        textHistory=findViewById(R.id.textHistory);
        textClear=findViewById(R.id.textClear);
        listViewSuggestions = findViewById(R.id.listViewSuggestions);
        suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestionList);
        listViewSuggestions.setAdapter(suggestionAdapter);
        searchLoadState.setOnRetryListener(v -> retrySearch());

        preferences = getSharedPreferences("SearchPrefs", MODE_PRIVATE);
        loadHistory();

        btnSearch.setOnClickListener(v -> {
            String keyword = editTextSearch.getText().toString().trim();
            if (!keyword.isEmpty()) {
                saveHistory(keyword);
            }
            performSearch(keyword);
        });

        textClear.setOnClickListener(v->{
            historyList.clear();
            preferences.edit().remove(HISTORY_KEY).apply();
            loadHistory();
        });

        // 输入框监听
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(isSettingText) return;
                String keyword = s.toString().trim();
                getSuggestions(keyword);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        listViewSuggestions.setOnItemClickListener((parent, view, position, id) -> {
            String selected = suggestionList.get(position);
            isSettingText=true;
            editTextSearch.setText(selected);
            editTextSearch.setSelection(selected.length()); // 光标移到最后
            isSettingText=false;
            suggestionList.clear();
            suggestionGeneration++;
            cancelSuggestions();
            editTextSearch.clearFocus();
            listViewSuggestions.setVisibility(View.GONE);


            // 隐藏软键盘
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(editTextSearch.getWindowToken(), 0);
            }
            performSearch(selected);

        });

        recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultAdapter(videoResultList);
        recyclerViewResults.setAdapter(adapter);
    }

    private void performSearch(String keyword) {
        cancelActiveSearch();
        SearchRequestTracker.Request request = searchRequestTracker.begin(keyword);
        if (!request.shouldRequest()) {
            showHistory();
            return;
        }

        cancelSuggestions();
        textHistory.setVisibility(View.GONE);
        textClear.setVisibility(View.GONE);
        historyContainer.setVisibility(View.GONE);
        suggestionList.clear();
        suggestionAdapter.notifyDataSetChanged();
        editTextSearch.clearFocus();
        listViewSuggestions.setVisibility(View.GONE);
        adapter.setData(Collections.emptyList());
        recyclerViewResults.setVisibility(View.VISIBLE);
        searchLoadState.showLoading(null);
        startSearch(request);
    }

    private void retrySearch() {
        SearchRequestTracker.Request request = searchRequestTracker.retry();
        if (!request.shouldRequest()) {
            return;
        }
        cancelActiveSearch();
        recyclerViewResults.setVisibility(View.VISIBLE);
        searchLoadState.showLoading(null);
        startSearch(request);
    }

    //搜索视频的请求处理
    private void startSearch(SearchRequestTracker.Request request) {
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<VodResModel> call = apiService.requestRearchVodData(request.getKeyword());
        activeSearchCall = call;

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodResModel>() {
            @Override
            public void onSuccess(VodResModel data) {
                if (!canHandleSearch(call, request.getGeneration())) {
                    return;
                }
                activeSearchCall = null;
                SearchRequestTracker.State state = searchRequestTracker.onSuccess(
                    request.getGeneration(),
                    data.getCode(),
                    data.getVodDataList()
                );
                renderSearchState(state, data.getVodDataList());
            }

            @Override
            public void onFailure(String error) {
                if (!canHandleSearch(call, request.getGeneration())) {
                    return;
                }
                activeSearchCall = null;
                renderSearchState(
                    searchRequestTracker.onFailure(request.getGeneration()),
                    null
                );
                Log.e("Suggestion", "error:" + error);
            }
        });
    }

    private boolean canHandleSearch(Call<VodResModel> call, int generation) {
        return activeSearchCall == call
            && searchRequestTracker.isCurrent(generation)
            && !isActivityInactive();
    }

    private void renderSearchState(SearchRequestTracker.State state, List<VodData> results) {
        if (state == SearchRequestTracker.State.CONTENT) {
            adapter.setData(results);
            recyclerViewResults.setVisibility(View.VISIBLE);
            searchLoadState.hide();
        } else if (state == SearchRequestTracker.State.EMPTY) {
            adapter.setData(Collections.emptyList());
            recyclerViewResults.setVisibility(View.GONE);
            searchLoadState.showEmpty(null);
        } else if (state == SearchRequestTracker.State.ERROR) {
            adapter.setData(Collections.emptyList());
            recyclerViewResults.setVisibility(View.GONE);
            searchLoadState.showError(null);
        }
    }

    //联想的请求处理
    private void getSuggestions(String input) {
        String keyword = input == null ? "" : input.trim();
        cancelSuggestions();
        int generation = ++suggestionGeneration;
        if (keyword.isEmpty()) {
            suggestionList.clear();
            suggestionAdapter.notifyDataSetChanged();
            listViewSuggestions.setVisibility(View.GONE);
            return;
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<Map<String, Object>> call = apiService.requestSuggestData(keyword);
        activeSuggestionCall = call;

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!canHandleSuggestion(call, generation)) {
                    return;
                }
                activeSuggestionCall = null;
                Number codeValue = (Number) data.get("code");
                int code = codeValue == null ? -1 : codeValue.intValue();
                if (code == 200) {
                    suggestionList.clear();
                    Object result = data.get("data");
                    if (result instanceof List) {
                        suggestionList.addAll((List<String>) result);
                    }
                    suggestionAdapter.notifyDataSetChanged();
                    listViewSuggestions.setVisibility(
                        suggestionList.isEmpty() ? View.GONE : View.VISIBLE
                    );
                } else {
                    listViewSuggestions.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String error) {
                if (!canHandleSuggestion(call, generation)) {
                    return;
                }
                activeSuggestionCall = null;
                listViewSuggestions.setVisibility(View.GONE);
                Log.e("Suggestion", "error:" + error);
            }
        });
    }

    private boolean canHandleSuggestion(Call<Map<String, Object>> call, int generation) {
        return activeSuggestionCall == call
            && generation == suggestionGeneration
            && !isActivityInactive();
    }

    private void cancelActiveSearch() {
        if (activeSearchCall != null) {
            activeSearchCall.cancel();
            activeSearchCall = null;
        }
    }

    private void cancelSuggestions() {
        if (activeSuggestionCall != null) {
            activeSuggestionCall.cancel();
            activeSuggestionCall = null;
        }
    }

    private void showHistory() {
        searchLoadState.hide();
        recyclerViewResults.setVisibility(View.GONE);
        textHistory.setVisibility(View.VISIBLE);
        textClear.setVisibility(View.VISIBLE);
        historyContainer.setVisibility(View.VISIBLE);
        suggestionList.clear();
        suggestionAdapter.notifyDataSetChanged();
        listViewSuggestions.setVisibility(View.GONE);
    }

    private boolean isActivityInactive() {
        return destroyed || isFinishing()
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }


    private void saveHistory(String keyword) {
        if (!historyList.contains(keyword)) {
            historyList.add(0, keyword);
            Set<String> set = new LinkedHashSet<>(historyList);
            preferences.edit().putStringSet(HISTORY_KEY, set).apply();
            loadHistory();
        }
    }

    private void loadHistory() {
        historyContainer.removeAllViews();
        Set<String> set = preferences.getStringSet(HISTORY_KEY, new LinkedHashSet<>());
        historyList = new ArrayList<>(set);
        if(historyList.isEmpty()) return;

        //采用动态设置
        GradientDrawable drawable = new GradientDrawable();
        drawable.setStroke(2, Color.GRAY); // 边框宽度和颜色
        drawable.setCornerRadius(8);       // 可选：圆角半径
        drawable.setColor(Color.TRANSPARENT); // 背景色（透明）

        // 设置 TextView 的 layoutParams 来添加 margin
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(12, 12, 12, 12); // 左上右下的 margin（可以根据需要调整）
        for (String item : historyList) {
            TextView tv = new TextView(this);
            tv.setText(item);
            tv.setTextSize(16);
            tv.setPadding(8, 8, 20, 8);
            // 创建一个 shape drawable 设置边框

            tv.setBackground(drawable);
            tv.setLayoutParams(params);
            tv.setOnClickListener(v -> {
                isSettingText=true;
                editTextSearch.setText(item);
                isSettingText=false;
                performSearch(item);
            });
            historyContainer.addView(tv);
        }
    }

    @Override
    public void onBackPressed() {
        if (recyclerViewResults.getVisibility() == View.VISIBLE
            || searchLoadState.getVisibility() == View.VISIBLE) {
            editTextSearch.setText("");
            performSearch("");
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        searchRequestTracker.destroy();
        suggestionGeneration++;
        cancelActiveSearch();
        cancelSuggestions();
        super.onDestroy();
    }


}
