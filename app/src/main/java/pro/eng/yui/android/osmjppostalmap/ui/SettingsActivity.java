package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Intent;
import org.json.JSONObject;
import okhttp3.ResponseBody;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.osm.OsmApi;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.BuildConfig;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import org.osmdroid.tileprovider.modules.SqlTileWriter;

public class SettingsActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private static final String CLIENT_ID = BuildConfig.OSM_CLIENT_ID;
    private static final String CLIENT_SECRET = BuildConfig.OSM_CLIENT_SECRET;
    private static final String REDIRECT_URI = "osmjppostalmap://oauth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View mainLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        authRepository = new AuthRepository(this);

        TextView loginStatus = findViewById(R.id.login_status);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnUserPage = findViewById(R.id.btn_user_page);
        Button btnLogout = findViewById(R.id.btn_logout);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);

        btnLogin.setOnClickListener(v -> {
            String url = "https://www.openstreetmap.org/oauth2/authorize" +
                    "?client_id=" + CLIENT_ID +
                    "&redirect_uri=" + REDIRECT_URI +
                    "&response_type=code" +
                    "&scope=read_prefs write_api";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        btnUserPage.setOnClickListener(v -> {
            String userName = authRepository.getUserName();
            String url = (userName != null && !userName.isEmpty()) 
                ? "https://www.openstreetmap.org/user/" + Uri.encode(userName)
                : "https://www.openstreetmap.org/user/";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("ログアウト")
                .setMessage("ログアウトしますか？")
                .setPositiveButton("はい", (dialog, which) -> {
                    authRepository.logout();
                    updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
                })
                .setNegativeButton("いいえ", null)
                .show();
        });

        findViewById(R.id.btn_osm_copyright).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_osm_copyright)));
            startActivity(intent);
        });

        findViewById(R.id.btn_github_repo).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_github_repo)));
            startActivity(intent);
        });

        findViewById(R.id.btn_delete_cache).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_map_cache_title)
                .setMessage(R.string.settings_delete_map_cache_confirm)
                .setPositiveButton("はい", (dialog, which) -> {
                    deleteMapCache();
                })
                .setNegativeButton("いいえ", null)
                .show();
        });

        findViewById(R.id.btn_show_boundary).setOnClickListener(v -> showBoundaryDialog());
        findViewById(R.id.btn_delete_boundary).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("行政界削除")
                .setMessage("行政界のキャッシュデータを削除しますか？")
                .setPositiveButton("はい", (dialog, which) -> {
                    JpPostalUtil.truncatePrefectureCache();
                    Toast.makeText(this, "行政界キャッシュを削除しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("いいえ", null)
                .show();
        });

        TextView appVersionInfo = findViewById(R.id.app_version_info);
        String versionInfo = String.format("v%s(%d) + %s", 
                BuildConfig.VERSION_NAME, 
                BuildConfig.VERSION_CODE, 
                BuildConfig.GIT_COMMIT_HASH);
        appVersionInfo.setText("OSM JP Postal Map " + versionInfo);

        // 認可コードの処理
        handleIntent(getIntent(), loginStatus, btnLogin, btnUserPage, btnLogout);
    }

    private void updateUi(TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        if (authRepository.isLoggedIn()) {
            String userName = authRepository.getUserName();
            loginStatus.setText("ログイン中: " + (userName != null ? userName : "取得中..."));
            if (userName == null) {
                fetchUserDetails(authRepository.getAccessToken(), loginStatus, btnLogin, btnUserPage, btnLogout);
            }
            btnLogin.setVisibility(View.GONE);
            btnUserPage.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.VISIBLE);
        } else {
            loginStatus.setText("未ログイン");
            btnLogin.setVisibility(View.VISIBLE);
            btnUserPage.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, findViewById(R.id.login_status), findViewById(R.id.btn_login), 
                    findViewById(R.id.btn_user_page), findViewById(R.id.btn_logout));
    }

    private void handleIntent(android.content.Intent intent, TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(REDIRECT_URI)) {
            String code = data.getQueryParameter("code");
            if (code != null) {
                exchangeToken(code, loginStatus, btnLogin, btnUserPage, btnLogout);
            }
        }
    }

    private void exchangeToken(String code, TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        if (CLIENT_ID == null || CLIENT_ID.isEmpty()) {
            Toast.makeText(this, "CLIENT_ID が設定されていません。ビルド設定を確認してください。", Toast.LENGTH_LONG).show();
            return;
        }

        Call<ResponseBody> call;
        if (CLIENT_SECRET == null || CLIENT_SECRET.isEmpty()) {

            call = JpPostalUtil.getOsmApi().getAccessTokenPublic(
                    CLIENT_ID, code, "authorization_code", REDIRECT_URI
            );
        } else {
            call = JpPostalUtil.getOsmApi().getAccessToken(
                    CLIENT_ID, CLIENT_SECRET, code, "authorization_code", REDIRECT_URI
            );
        }

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JSONObject obj = new JSONObject(json);
                        String token = obj.getString("access_token");
                        authRepository.saveAccessToken(token);
                        fetchUserDetails(token, loginStatus, btnLogin, btnUserPage, btnLogout);
                    } else {
                        Toast.makeText(SettingsActivity.this, "ログイン失敗: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "ネットワークエラー", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteMapCache() {
        try {
            SqlTileWriter writer = new SqlTileWriter();
            boolean success = writer.purgeCache();
            if (success) {
                Toast.makeText(this, R.string.settings_delete_map_cache_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.settings_delete_map_cache_fail, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.settings_delete_map_cache_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void showBoundaryDialog() {
        JpPostalUtil.getRawPrefecturesJson().thenAccept(json -> {
            runOnUiThread(() -> {
                try {
                    String prettyJson = (json == null || json.isEmpty()) ? "{}" : new JSONObject(json).toString(4);

                    ScrollView scrollView = new ScrollView(this);
                    TextView textView = new TextView(this);
                    textView.setText(prettyJson);
                    textView.setPadding(32, 32, 32, 32);
                    textView.setTextSize(12f);
                    scrollView.addView(textView);

                    new MaterialAlertDialogBuilder(this)
                            .setTitle("行政界キャッシュ")
                            .setView(scrollView)
                            .setPositiveButton("閉じる", null)
                            .setNeutralButton("更新", (dialog, which) -> {
                                updateBoundaryData();
                            })
                            .show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "JSONの解析に失敗しました", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateBoundaryData() {
        Toast.makeText(this, "行政界データを更新しています...", Toast.LENGTH_SHORT).show();
        JpPostalUtil.fetchPrefectures().thenAccept(v -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "行政界データを更新しました", Toast.LENGTH_SHORT).show();
                showBoundaryDialog(); // リロードして再表示
            });
        }).exceptionally(ex -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "更新に失敗しました: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }

    private void fetchUserDetails(String token, TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        JpPostalUtil.getOsmApi().getUserDetailsJson("Bearer " + token).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        android.util.Log.d("OSM_AUTH", "User Details Response: " + json);
                        
                        JSONObject obj = new JSONObject(json);
                        JSONObject user = obj.getJSONObject("user");
                        String displayName = user.getString("display_name");
                        
                        if (displayName != null && !displayName.isEmpty()) {
                            authRepository.saveUserName(displayName);
                            runOnUiThread(() -> {
                                updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
                                Toast.makeText(SettingsActivity.this, "ログインしました: " + authRepository.getUserName(), Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            android.util.Log.e("OSM_AUTH", "display_name is empty in JSON response");
                            runOnUiThread(() -> updateUi(loginStatus, btnLogin, btnUserPage, btnLogout));
                        }
                    } else {
                        android.util.Log.e("OSM_AUTH", "Failed to fetch user details: " + response.code());
                        runOnUiThread(() -> updateUi(loginStatus, btnLogin, btnUserPage, btnLogout));
                    }
                } catch (Exception e) {
                    android.util.Log.e("OSM_AUTH", "Error parsing user details", e);
                    runOnUiThread(() -> updateUi(loginStatus, btnLogin, btnUserPage, btnLogout));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                android.util.Log.e("OSM_AUTH", "Network error fetching user details", t);
                runOnUiThread(() -> updateUi(loginStatus, btnLogin, btnUserPage, btnLogout));
            }
        });
    }
}
