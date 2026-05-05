package com.dlight.data.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class VodPageResModel {

    private int code;              // 状态码
    private JsonElement data;      // 兼容数组或对象结构

    private static final Gson GSON = new Gson();
    private static final Type VOD_LIST_TYPE = new TypeToken<List<VodData>>() {}.getType();

    // Getter 和 Setter
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<VodData> getVodDataList() {
        if (data == null || data.isJsonNull()) {
            return new ArrayList<>();
        }
        if (data.isJsonArray()) {
            return GSON.fromJson(data, VOD_LIST_TYPE);
        }
        if (data.isJsonObject()) {
            JsonObject obj = data.getAsJsonObject();
            JsonElement list = obj.get("list");
            if (list != null && list.isJsonArray()) {
                return GSON.fromJson(list, VOD_LIST_TYPE);
            }
        }
        return new ArrayList<>();
    }

    public void setData(JsonElement data) {
        this.data = data;
    }

    public int getPage() {
        return getIntFromDataObject("page");
    }

    public void setPage(int page) {
        // no-op: 由后端 data 对象提供
    }

    public int getLimit() {
        return getIntFromDataObject("limit");
    }

    public void setLimit(int limit) {
        // no-op: 由后端 data 对象提供
    }

    public int getTotal() {
        return getIntFromDataObject("total");
    }

    public void setTotal(int total) {
        // no-op: 由后端 data 对象提供
    }

    public int getTotalPage() {
        return getIntFromDataObject("totalPage");
    }

    public void setTotalPage(int totalPage) {
        // no-op: 由后端 data 对象提供
    }

    private int getIntFromDataObject(String key) {
        if (data != null && data.isJsonObject()) {
            JsonElement value = data.getAsJsonObject().get(key);
            if (value != null && value.isJsonPrimitive()) {
                try {
                    return value.getAsInt();
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "VodPageResModel{" +
            "code=" + code +
            ", page=" + getPage() +
            ", limit=" + getLimit() +
            ", total=" + getTotal() +
            ", totalPage=" + getTotalPage() +
            ", dataListSize=" + getVodDataList().size() +
            '}';
    }
}
