# User Page Compact List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the oversized 2×2 action panel on the signed-in user page with a compact four-row menu while preserving every existing action and ID.

**Architecture:** Keep `UserFragment` and its click handling unchanged. Implement the redesign entirely in Android XML by adding one reusable icon-background drawable and replacing the internal structure of `fragment_user.xml`; reuse the existing colors, card style, icons, and chevron.

**Tech Stack:** Android Views XML, Material Components `MaterialCardView` and `ShapeableImageView`, Gradle Android build.

---

## File Map

- Create `app/src/main/res/drawable/bg_user_menu_icon.xml`: subtle brand-colored rounded background behind each menu icon.
- Modify `app/src/main/res/layout/fragment_user.xml`: compact profile header, section label, and four full-width action rows.
- No Java files change: existing IDs keep `UserFragment` behavior intact.

### Task 1: Establish the build baseline

**Files:**
- Verify only; no files changed.

- [ ] **Step 1: Compile the existing app before editing**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If it fails before any edit, record the original failure and do not attribute it to the layout change.

### Task 2: Implement the compact menu layout

**Files:**
- Create: `app/src/main/res/drawable/bg_user_menu_icon.xml`
- Modify: `app/src/main/res/layout/fragment_user.xml`

- [ ] **Step 1: Add the menu icon background**

Create `app/src/main/res/drawable/bg_user_menu_icon.xml` with exactly:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="10dp" />
    <solid android:color="@color/brand_500_alpha12" />
</shape>
```

- [ ] **Step 2: Replace the user-page layout**

Replace `app/src/main/res/layout/fragment_user.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:scrollbars="none">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingBottom="32dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <com.google.android.material.imageview.ShapeableImageView
                android:id="@+id/avatar_file"
                android:layout_width="64dp"
                android:layout_height="64dp"
                android:contentDescription="用户头像"
                android:scaleType="centerCrop"
                android:src="@drawable/button_style"
                app:shapeAppearanceOverlay="@style/CircularShape"
                app:strokeColor="@color/border_subtle"
                app:strokeWidth="1dp" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/user_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:text="用户名"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="欢迎回来"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />
            </LinearLayout>
        </LinearLayout>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="2dp"
            android:layout_marginTop="28dp"
            android:layout_marginBottom="10dp"
            android:text="我的服务"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            android:textStyle="bold" />

        <com.google.android.material.card.MaterialCardView
            style="@style/CardStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <LinearLayout
                    android:id="@+id/btn_history"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="?attr/selectableItemBackground"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:minHeight="64dp"
                    android:orientation="horizontal"
                    android:paddingStart="14dp"
                    android:paddingEnd="12dp">

                    <FrameLayout
                        android:layout_width="36dp"
                        android:layout_height="36dp"
                        android:background="@drawable/bg_user_menu_icon">

                        <ImageView
                            android:layout_width="22dp"
                            android:layout_height="22dp"
                            android:layout_gravity="center"
                            android:contentDescription="播放记录"
                            android:src="@drawable/ic_history_24" />
                    </FrameLayout>

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="14dp"
                        android:layout_weight="1"
                        android:text="播放记录"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:contentDescription="进入播放记录"
                        android:src="@drawable/ic_chevron_right_24" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:layout_marginStart="64dp"
                    android:background="@color/border_soft" />

                <LinearLayout
                    android:id="@+id/btn_star"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="?attr/selectableItemBackground"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:minHeight="64dp"
                    android:orientation="horizontal"
                    android:paddingStart="14dp"
                    android:paddingEnd="12dp">

                    <FrameLayout
                        android:layout_width="36dp"
                        android:layout_height="36dp"
                        android:background="@drawable/bg_user_menu_icon">

                        <ImageView
                            android:layout_width="22dp"
                            android:layout_height="22dp"
                            android:layout_gravity="center"
                            android:contentDescription="我的追番"
                            android:src="@drawable/ic_star_24" />
                    </FrameLayout>

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="14dp"
                        android:layout_weight="1"
                        android:text="我的追番"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:contentDescription="进入我的追番"
                        android:src="@drawable/ic_chevron_right_24" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:layout_marginStart="64dp"
                    android:background="@color/border_soft" />

                <LinearLayout
                    android:id="@+id/btn_download"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="?attr/selectableItemBackground"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:minHeight="64dp"
                    android:orientation="horizontal"
                    android:paddingStart="14dp"
                    android:paddingEnd="12dp">

                    <FrameLayout
                        android:layout_width="36dp"
                        android:layout_height="36dp"
                        android:background="@drawable/bg_user_menu_icon">

                        <ImageView
                            android:layout_width="22dp"
                            android:layout_height="22dp"
                            android:layout_gravity="center"
                            android:contentDescription="离线缓存"
                            android:src="@drawable/ic_cloud_download_24" />
                    </FrameLayout>

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="14dp"
                        android:layout_weight="1"
                        android:text="离线缓存"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:contentDescription="进入离线缓存"
                        android:src="@drawable/ic_chevron_right_24" />
                </LinearLayout>

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:layout_marginStart="64dp"
                    android:background="@color/border_soft" />

                <LinearLayout
                    android:id="@+id/btn_settings"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="?attr/selectableItemBackground"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center_vertical"
                    android:minHeight="64dp"
                    android:orientation="horizontal"
                    android:paddingStart="14dp"
                    android:paddingEnd="12dp">

                    <FrameLayout
                        android:layout_width="36dp"
                        android:layout_height="36dp"
                        android:background="@drawable/bg_user_menu_icon">

                        <ImageView
                            android:layout_width="22dp"
                            android:layout_height="22dp"
                            android:layout_gravity="center"
                            android:contentDescription="设置"
                            android:src="@drawable/ic_settings_24" />
                    </FrameLayout>

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="14dp"
                        android:layout_weight="1"
                        android:text="设置"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:contentDescription="进入设置"
                        android:src="@drawable/ic_chevron_right_24" />
                </LinearLayout>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: Confirm behavior IDs still exist exactly once**

Run:

```bash
for id in avatar_file user_name btn_history btn_star btn_download btn_settings; do
  count=$(rg -c "android:id=\"@\+id/$id\"" app/src/main/res/layout/fragment_user.xml)
  test "$count" -eq 1 || exit 1
done
```

Expected: exit status `0` with no output.

### Task 3: Verify and commit the implementation

**Files:**
- Verify: `app/src/main/res/drawable/bg_user_menu_icon.xml`
- Verify: `app/src/main/res/layout/fragment_user.xml`

- [ ] **Step 1: Check XML changes for whitespace errors**

Run:

```bash
git diff --check -- app/src/main/res/drawable/bg_user_menu_icon.xml app/src/main/res/layout/fragment_user.xml
```

Expected: exit status `0` with no output.

- [ ] **Step 2: Compile Android resources and the debug app**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and no resource-linking errors.

- [ ] **Step 3: Review the surgical diff**

Run:

```bash
git diff --stat && git diff -- app/src/main/res/drawable/bg_user_menu_icon.xml app/src/main/res/layout/fragment_user.xml
```

Expected: only the new icon background and the user-page layout are part of the implementation diff; no Java behavior changes appear.

- [ ] **Step 4: Commit the layout change**

Run:

```bash
git add app/src/main/res/drawable/bg_user_menu_icon.xml app/src/main/res/layout/fragment_user.xml
git commit -m "feat: refine user page with compact menu"
```

Expected: one commit containing exactly the two implementation files.
