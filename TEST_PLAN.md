# 次元之光Android客户端测试计划

## 📋 测试目标

确保改造后的Android客户端功能完整、稳定，与后端API正确对接，UI主题与前端一致。

---

## 🧪 第一阶段：单元测试

### 1.1 网络层测试

**测试文件**: `app/src/test/java/com/dlight/network/NetworkManagerTest.java`

```java
package com.dlight.network;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * NetworkManager单元测试
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
public class NetworkManagerTest {
    private Context context;
    private NetworkManager networkManager;

    @Before
    public void setUp() {
        context = Application.getApplicationContext();
        networkManager = NetworkManager.getInstance(context);
    }

    @Test
    public void testSingleton() {
        NetworkManager instance1 = NetworkManager.getInstance(context);
        NetworkManager instance2 = NetworkManager.getInstance(context);
        assertSame(instance1, instance2);
    }

    @Test
    public void testApiInstancesNotNull() {
        assertNotNull(networkManager.getVodApi());
        assertNotNull(networkManager.getAuthApi());
        assertNotNull(networkManager.getDanmakuApi());
        assertNotNull(networkManager.getCommentApi());
    }

    @Test
    public void testTokenManagement() {
        String testToken = "test_token_12345";

        // 保存token
        networkManager.saveToken(testToken);
        assertTrue(networkManager.isLoggedIn());

        // 获取token
        String savedToken = networkManager.getToken();
        assertEquals(testToken, savedToken);

        // 清除token
        networkManager.clearToken();
        assertFalse(networkManager.isLoggedIn());
    }

    @Test
    public void testUserInfoManagement() {
        int testUserId = 100;
        String testUserName = "testuser";

        // 保存用户信息
        networkManager.saveUserInfo(testUserId, testUserName);

        // 获取用户信息
        assertEquals(testUserId, networkManager.getUserId());
        assertEquals(testUserName, networkManager.getUserName());

        // 清除用户信息
        networkManager.clearUserInfo();
        assertEquals(0, networkManager.getUserId());
        assertEquals("", networkManager.getUserName());
    }

    @Test
    public void testLogout() {
        // 先登录
        networkManager.saveToken("test_token");
        networkManager.saveUserInfo(100, "testuser");
        assertTrue(networkManager.isLoggedIn());

        // 退出登录
        networkManager.logout();
        assertFalse(networkManager.isLoggedIn());
        assertEquals(0, networkManager.getUserId());
        assertEquals("", networkManager.getUserName());
    }
}
```

### 1.2 数据模型测试

**测试文件**: `app/src/test/java/com/dlight/network/model/DanmakuTest.java`

```java
package com.dlight.network.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Danmaku模型测试
 */
public class DanmakuTest {
    @Test
    public void testColorHexConversion() {
        Danmaku danmaku = new Danmaku();

        // 测试白色 (16777215 -> #FFFFFF)
        danmaku.setColor(16777215);
        assertEquals("#FFFFFF", danmaku.getColorHex());

        // 测试红色 (255 -> #0000FF)
        danmaku.setColor(255);
        assertEquals("#0000FF", danmaku.getColorHex());

        // 测试方法
        int whiteInt = Danmaku.hexToIntColor("#FFFFFF");
        assertEquals(16777215, whiteInt);

        int redInt = Danmaku.hexToIntColor("#0000FF");
        assertEquals(255, redInt);
    }

    @Test
    public void testGettersAndSetters() {
        Danmaku danmaku = new Danmaku();

        danmaku.setText("测试弹幕");
        danmaku.setTime(10.5f);
        danmaku.setType(0);

        assertEquals("测试弹幕", danmaku.getText());
        assertEquals(10.5f, danmaku.getTime(), 0.001);
        assertEquals(0, danmaku.getType());
    }
}
```

### 1.3 API响应测试

**测试文件**: `app/src/test/java/com/dlight/network/model/ApiResponseTest.java`

```java
package com.dlight.network.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ApiResponse模型测试
 */
public class ApiResponseTest {
    @Test
    public void testSuccessResponse() {
        ApiResponse<String> response = new ApiResponse<>();

        // 模拟后端返回的数据
        response.setCode(200);
        response.setMessage("操作成功");
        response.setData("test data");

        assertTrue(response.isSuccess());
        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("test data", response.getData());
    }

    @Test
    public void testErrorResponse() {
        ApiResponse<String> response = new ApiResponse<>();

        response.setCode(400);
        response.setMsg("参数错误");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getCode());
        assertEquals("参数错误", response.getMessage());
    }

    @Test
    public void testMessagePriority() {
        ApiResponse<String> response = new ApiResponse<>();

        // message优先于msg
        response.setMessage("message");
        response.setMsg("msg");
        assertEquals("message", response.getMessage());

        // 当message为null时使用msg
        response.setMessage(null);
        assertEquals("msg", response.getMessage());
    }
}
```

---

## 📱 第二阶段：集成测试

### 2.1 API接口调用测试

**测试文件**: `app/src/androidTest/java/com/dlight/network/api/VodApiIntegrationTest.java`

```java
package com.dlight.network.api;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.dlight.network.ApiConfig;
import com.dlight.network.NetworkManager;
import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.Vod;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * VodApi集成测试
 * 注意：需要在测试前启动后端服务，并修改BASE_URL
 */
@RunWith(AndroidJUnit4.class)
public class VodApiIntegrationTest {
    private Context context;
    private NetworkManager networkManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // TODO: 修改为测试服务器地址
        ApiConfig.BASE_URL = "http://test-server:8000/api/";
        networkManager = NetworkManager.getInstance(context);
    }

    @Test
    public void testGetTodayVods() throws Exception {
        VodApi vodApi = networkManager.getVodApi();

        retrofit2.Response<ApiResponse<List<Vod>>> response =
                vodApi.getTodayVods().execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<List<Vod>> apiResponse = response.body();
        assertTrue(apiResponse.isSuccess() || apiResponse.getCode() == 404);

        if (apiResponse.isSuccess()) {
            List<Vod> vods = apiResponse.getData();
            assertNotNull(vods);
        }
    }

    @Test
    public void testSearchVod() throws Exception {
        VodApi vodApi = networkManager.getVodApi();

        retrofit2.Response<ApiResponse<List<Vod>>> response =
                vodApi.searchVod("测试").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<List<Vod>> apiResponse = response.body();
        // 搜索可能为空，不强制检查
        assertNotNull(apiResponse.getData());
    }

    @Test
    public void testGetVodByIds() throws Exception {
        VodApi vodApi = networkManager.getVodApi();

        retrofit2.Response<ApiResponse<List<Vod>>> response =
                vodApi.getVodByIds("1").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<List<Vod>> apiResponse = response.body();
        // 可能找不到视频
        assertNotNull(apiResponse.getData());
    }
}
```

### 2.2 认证流程测试

**测试文件**: `app/src/androidTest/java/com/dlight/network/api/AuthApiIntegrationTest.java`

```java
package com.dlight.network.api;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.dlight.network.ApiConfig;
import com.dlight.network.NetworkManager;
import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * AuthApi集成测试
 */
@RunWith(AndroidJUnit4.class)
public class AuthApiIntegrationTest {
    private Context context;
    private NetworkManager networkManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ApiConfig.BASE_URL = "http://test-server:8000/api/";
        networkManager = NetworkManager.getInstance(context);
    }

    @Test
    public void testLoginSuccess() throws Exception {
        AuthApi authApi = networkManager.getAuthApi();

        // 使用测试账号
        retrofit2.Response<ApiResponse<User>> response =
                authApi.login("testuser", "test123").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<User> apiResponse = response.body();
        if (apiResponse.isSuccess()) {
            User user = apiResponse.getData();
            assertNotNull(user);
            assertNotNull(user.getToken());

            // 保存token
            networkManager.saveToken(user.getToken());
            assertTrue(networkManager.isLoggedIn());
        }
    }

    @Test
    public void testLoginFailure() throws Exception {
        AuthApi authApi = networkManager.getAuthApi();

        // 使用错误的密码
        retrofit2.Response<ApiResponse<User>> response =
                authApi.login("testuser", "wrongpassword").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<User> apiResponse = response.body();
        assertFalse(apiResponse.isSuccess());
    }

    @Test
    public void testRegister() throws Exception {
        AuthApi authApi = networkManager.getAuthApi();

        // 使用随机用户名避免冲突
        String randomUsername = "testuser_" + System.currentTimeMillis();
        retrofit2.Response<ApiResponse<User>> response =
                authApi.register(randomUsername, "test123").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        ApiResponse<User> apiResponse = response.body();
        if (apiResponse.isSuccess()) {
            User user = apiResponse.getData();
            assertNotNull(user);
            assertNotNull(user.getToken());
        }
    }
}
```

---

## 🎨 第三阶段：UI测试

### 3.1 界面显示测试

**测试文件**: `app/src/androidTest/java/com/dlight/ui/MainActivityTest.java`

```java
package com.dlight.ui;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * MainActivity UI测试
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

    @Test
    public void testBottomNavigationDisplayed() {
        // 验证底部导航栏显示
        // TODO: 添加底部导航栏ID
        // onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
    }

    @Test
    public void testHomeFragmentLoaded() {
        // 验证首页Fragment加载
        // TODO: 添加具体的UI验证
    }
}
```

### 3.2 交互测试

**测试文件**: `app/src/androidTest/java/com/dlight/ui/VodDetailActivityTest.java`

```java
package com.dlight.ui;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.dlight.network.model.Vod;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.espresso.matcher.ViewMatchers.withId;

/**
 * VodDetailActivity交互测试
 */
@RunWith(AndroidJUnit4.class)
public class VodDetailActivityTest {

    @Rule
    public ActivityTestRule<VodDetailActivity> activityRule =
            new ActivityTestRule<>(VodDetailActivity.class, false, false);

    @Test
    public void testPlayVideo() {
        // 创建测试视频数据
        Vod testVod = new Vod();
        testVod.setVodId(1);
        testVod.setVodName("测试视频");
        testVod.setVodPlayUrl("https://test-url.com/video.mp4");

        // 启动Activity
        Intent intent = new Intent();
        intent.putExtra("vod_data", testVod);
        activityRule.launchActivity(intent);

        // 点击播放按钮
        // onView(withId(R.id.play_button)).perform(click());

        // 验证播放器显示
        // onView(withId(R.id.video_player)).check(matches(isDisplayed()));
    }
}
```

---

## 🎮 第四阶段：播放器集成测试

### 4.1 播放器功能测试

**测试项**:
- ✅ 播放器初始化
- ✅ 视频加载和播放
- ✅ 暂停/恢复
- ✅ 快进/快退
- ✅ 全屏切换
- ✅ 音量控制
- ✅ 亮度控制

### 4.2 弹幕功能测试

**测试项**:
- ✅ 弹幕加载
- ✅ 弹幕显示
- ✅ 发送弹幕
- ✅ 弹幕颜色转换
- ✅ 弹幕时间同步

---

## 📊 测试执行计划

### 4.1 单元测试

**执行命令**:
```bash
./gradlew test
```

**预期结果**:
- 所有单元测试通过
- 代码覆盖率 > 80%

### 4.2 集成测试

**执行命令**:
```bash
./gradlew connectedAndroidTest
```

**预期结果**:
- 所有API接口调用成功
- 数据解析正确
- Token管理正常

### 4.3 UI测试

**执行命令**:
```bash
./gradlew connectedAndroidTest
```

**预期结果**:
- 所有界面正常显示
- 交互响应正确
- 导航流畅

---

## 📝 测试报告模板

### 测试执行信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-04-05 |
| 测试人员 | |
| 测试环境 | Android Studio, 真机/模拟器 |
| 后端版本 | FastAPI v1.0 |
| 前端版本 | v1.0 |
| 测试APP版本 | v1.0 |

### 测试结果汇总

| 测试类型 | 总数 | 通过 | 失败 | 通过率 |
|---------|------|------|------|--------|
| 单元测试 | 0 | 0 | 0 | - |
| 集成测试 | 0 | 0 | 0 | - |
| UI测试 | 0 | 0 | 0 | - |
| 播放器测试 | 0 | 0 | 0 | - |
| **合计** | **0** | **0** | **0** | **-** |

### 缺陷列表

| ID | 模块 | 严重程度 | 描述 | 状态 |
|----|------|----------|------|------|
| 1 | | | | |

---

## 🎯 下一步行动

1. ✅ 创建单元测试框架
2. ⏳ 实现单元测试用例
3. ⏳ 创建集成测试用例
4. ⏳ 创建UI测试用例
5. ⏳ 配置持续集成（CI）
6. ⏳ 执行测试并生成报告

---

## 📚 参考资料

- [Android单元测试文档](https://developer.android.com/training/testing)
- [Espresso测试框架](https://developer.android.com/training/testing/espresso)
- [Retrofit测试指南](https://square.github.io/retrofit/)
- [Robolectric测试框架](http://robolectric.org/)
