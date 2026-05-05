package com.dlight.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class JsonResModel {
    private int code;
    private JsonElement data;
    @SerializedName(value = "msg", alternate = {"message"})
    private String msg;
    private Boolean success;

    // Getter 和 Setter
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getData() {
        if (data == null || data.isJsonNull()) {
            return null;
        }
        if (data.isJsonPrimitive()) {
            try {
                return data.getAsString();
            } catch (Exception ignored) {
            }
        }
        return data.toString();
    }
    public void setData(JsonElement data) {this.data=data;}

    public JsonElement getRawData() {
        return data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public boolean isSuccessCode() {
        return code == 200 || code == 1 || Boolean.TRUE.equals(success);
    }
    @Override
    public String toString() {
        return "JsonResModel{" +
            "data=" + data +
            "code=" + code +
            ", msg=" + msg +
            ", success=" + success +
            '}';
    }
}
