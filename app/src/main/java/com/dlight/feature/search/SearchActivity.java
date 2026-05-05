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
        historyContainer = findViewById(R.id.historyContainer);
        textHistory=findViewById(R.id.textHistory);
        textClear=findViewById(R.id.textClear);
        listViewSuggestions = findViewById(R.id.listViewSuggestions);
        suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestionList);
        listViewSuggestions.setAdapter(suggestionAdapter);

        preferences = getSharedPreferences("SearchPrefs", MODE_PRIVATE);
        loadHistory();

        btnSearch.setOnClickListener(v -> {
            String keyword = editTextSearch.getText().toString().trim();
            if (!keyword.isEmpty()) {
                saveHistory(keyword);
                performSearch(keyword);
            }else{
                Toast.makeText(this, "请输入视频名！", Toast.LENGTH_SHORT).show();
            }
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
                if (!keyword.isEmpty()) {
                    getSuggestions(keyword);
                } else {
                    suggestionList.clear();
                    listViewSuggestions.setVisibility(View.GONE);
                }
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

        getRearch(keyword);
//        if (videoResultList.isEmpty()) {
//            Toast.makeText(this, "没有找到相关内容", Toast.LENGTH_SHORT).show();
//        }

        // 隐藏历史记录，显示结果
        textHistory.setVisibility(View.GONE);
        textClear.setVisibility(View.GONE);
        historyContainer.setVisibility(View.GONE);
        suggestionList.clear();
        editTextSearch.clearFocus();
        listViewSuggestions.setVisibility(View.GONE);
        recyclerViewResults.setVisibility(View.VISIBLE);

    }

    //搜索视频的请求处理
    private void getRearch(String input) {
        if (input == "" || input.isEmpty()) return;
        // 替换为你的实际请求接口，GET 方式携带 input_search 参数
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        //这里就不创建新的接受类了 使用泛 Map
        Call<VodResModel> call = apiService.requestRearchVodData(input);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodResModel>() {
            @Override
            public void onSuccess(VodResModel data) {

                if (data.getCode() == 200) {
                    adapter.setData(data.getVodDataList());
                }else{
                    recyclerViewResults.setVisibility(View.GONE);
                    Toast.makeText(getApplicationContext(), "没有找到相关内容", Toast.LENGTH_SHORT).show();
                    Log.e("Suggestion", "error:" + data);
                }
            }

            @Override
            public void onFailure(String error) {

                Log.e("Suggestion", "error:" + error);
            }
        });

    }
    //联想的请求处理
    private void getSuggestions(String input) {
        if(input ==" " || input.isEmpty()) return;
        // 替换为你的实际请求接口，GET 方式携带 input_search 参数
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        //这里就不创建新的接受类了 使用泛 Map
        Call<Map<String, Object>> call = apiService.requestSuggestData(input);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                //code 可能变成 Double 类型 是因为 Gson 在反序列化 JSON 数值时默认使用 Double
                Double codeDouble = (Double) data.get("code"); // Gson 处理整数字段可能会变成 Double
                int code = codeDouble.intValue();
                if (code == 200) {
                    suggestionList.clear();
                    List<String> list = (List<String>) data.get("data"); // 强转
                    suggestionList.addAll(list);
                    suggestionAdapter.notifyDataSetChanged();
                    listViewSuggestions.setVisibility(View.VISIBLE);
                } else {
                    listViewSuggestions.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(String error) {
                listViewSuggestions.setVisibility(View.GONE);
                Log.e("Suggestion", "error:" + error);
            }
        });
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
        if (recyclerViewResults.getVisibility() == View.VISIBLE) {
            // 当前显示的是搜索结果，返回时切换回历史记录界面
            recyclerViewResults.setVisibility(View.GONE);
            textHistory.setVisibility(View.VISIBLE);
            textClear.setVisibility(View.VISIBLE);
            historyContainer.setVisibility(View.VISIBLE);
            //直接设置为null 造成报错 如果返回之后再启动
            //adapter.setData(null);//清空recycle 防止下次请求使用上次数据
            editTextSearch.setText(""); // 可选：清空搜索框
        } else {
            // 否则执行默认的返回行为（退出页面）
            super.onBackPressed();
        }
    }


}

