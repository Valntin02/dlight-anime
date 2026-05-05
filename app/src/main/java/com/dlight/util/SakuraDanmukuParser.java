package com.dlight.util;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Color;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Danmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuFactory;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;
import master.flame.danmaku.danmaku.util.DanmakuUtils;
import java.nio.charset.Charset;
public class SakuraDanmukuParser extends BaseDanmakuParser {


        protected float mDispScaleX;
        protected float mDispScaleY;

        private  Map<Integer, Integer> conversionMap = new HashMap<>();
        @Override
        public Danmakus parse() {
            if (mDataSource != null) {
                AndroidFileSource source = (AndroidFileSource) mDataSource;
                //弹幕类型映射表，为了和前端的deplayer适配，目前底层就不修改，考虑到了以后可能使用哔哩哔哩类型，所以这里加了一个映射
                 int index = 0;
                conversionMap.put(0, 1);
                conversionMap.put(1, 5);
                conversionMap.put(2, 4);
                try {
                    Log.d("SakuraDanmukuParser", "parse: init");
                    // 使用 InputStreamReader 和 BufferedReader 读取字节数据，并转换为字符流
                    InputStreamReader reader = new InputStreamReader(source.data(), StandardCharsets.UTF_8);
                    BufferedReader bufferedReader = new BufferedReader(reader);

                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                        Log.d("SakuraDanmukuParser", "line"+line);
                    }

                    // 转换为最终的 JSON 字符串
                    String jsonData = stringBuilder.toString();
                    // 解析 JSON 对象
                    JSONObject jsonObject = new JSONObject(jsonData);
                    // 使用 JSON 解析器处理数据
                    JSONArray jsonArray = jsonObject.optJSONArray("data");
                    Danmakus result = new Danmakus();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonobj = jsonArray.getJSONObject(i);
                        //Log.d("SakuraDanmukuParser", "parse: 解析弹幕数据");
                        // 解析弹幕数据
                        BaseDanmaku item = parseDanmaku(jsonobj);
                        if (item != null) {
                        //    Log.d("SakuraDanmukuParser", "parse: item null");
                            item.index = index++;
                            result.addItem(item);

                        }
                    }
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }


        private BaseDanmaku parseDanmaku(JSONObject jsonObject) {
            try {
                // 0:时间(弹幕出现时间)
                // 1:类型(1从右至左滚动弹幕|6从左至右滚动弹幕|5顶端固定弹幕|4底端固定弹幕)
                // 2:颜色
                // 3:时间戳 ?
                // 4:用户hash
                // 5:弹幕id
                // 6:内容

                //需要注意解析的处理的time需要*1000 否则效果和不设置一样在视频开始就会播放，
                // 推测原因是底层采用的是ms单位，这就解决了之前根据弹幕播时间不对的问题
                long time =  (long) (jsonObject.getDouble("time")*1000); // 弹幕时间

                //Log.d("SakuraDanmukuParser", "parse: init"+time);
                boolean t=conversionMap.containsKey(jsonObject.getInt("type"));
                int type;
                //这里一开始测试数据 默认设置为false 因为没有考虑到测试数据type=0，调试了半天
                if(t){
                    type = conversionMap.get(jsonObject.getInt("type")) ; // 弹幕类型
                }else{
                    type = jsonObject.getInt("type"); // 弹幕类型
                }

                //float textSize = (float) jsonObject.getDouble("textSize"); // 字体大小
                float textSize = 17; // 字体大小
                int color = jsonObject.getInt("color"); // 颜色

                // 创建弹幕对象
                BaseDanmaku item = mContext.mDanmakuFactory.createDanmaku(type, mContext);

                if (item != null) {
                    item.setTime(time);
                    item.textSize = textSize * (mDispDensity - 0.6f);
                    item.textColor = color;
                    item.textShadowColor = color <= Color.BLACK ? Color.WHITE : Color.BLACK;

                    // 解析文本内容
                    String text = jsonObject.getString("text");
                    item.text = text;

                    // 设置其他属性，例如位置、动画等（如果有的话）
//                    if (jsonObject.has("position")) {
//                        JSONArray position = jsonObject.getJSONArray("position");
//                        float x = (float) position.getDouble(0);
//                        float y = (float) position.getDouble(1);
//                        mContext.mDanmakuFactory.fillTranslationData(item, x, y, x, y, 0, 0, mDispScaleX, mDispScaleY);
//                    }
                    //这两个属性需要设置，但目前不知道为什么
                    item.setTimer(mTimer);
                    item.flags = mContext.mGlobalFlagValues;

                    Log.d("SakuraDanmukuParser", "parse: init"+item.text);
                    return item;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public BaseDanmakuParser setDisplayer(IDisplayer disp) {
            super.setDisplayer(disp);
            mDispScaleX = mDispWidth / DanmakuFactory.BILI_PLAYER_WIDTH;
            mDispScaleY = mDispHeight / DanmakuFactory.BILI_PLAYER_HEIGHT;
            return this;
        }
    }
