package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Intent;
import org.json.JSONObject;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;

public class SettingsActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private static final String CLIENT_ID = "YOUR_CLIENT_ID";
    private static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET";
    private static final String REDIRECT_URI = "osmjppostalmap://oauth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        authRepository = new AuthRepository(this);

        TextView loginStatus = findViewById(R.id.login_status);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnUserPage = findViewById(R.id.btn_user_page);
        Button btnLogout = findViewById(R.id.btn_logout);

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
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/user/current"));
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

        // 認可コードの処理
        handleIntent(getIntent(), loginStatus, btnLogin, btnUserPage, btnLogout);
    }

    private void updateUi(TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        if (authRepository.isLoggedIn()) {
            loginStatus.setText("ログイン中: " + (authRepository.getUserName() != null ? authRepository.getUserName() : ""));
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
        authRepository.getAuthApi().getAccessToken(
                CLIENT_ID, CLIENT_SECRET, code, "authorization_code", REDIRECT_URI
        ).enqueue(new Callback<ResponseBody>() {
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

    private void fetchUserDetails(String token, TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        authRepository.getAuthApi().getUserDetailsJson("Bearer " + token).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JSONObject obj = new JSONObject(json);
                        JSONObject user = obj.getJSONObject("user");
                        String displayName = user.getString("display_name");
                        authRepository.saveUserName(displayName);
                        updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
                        Toast.makeText(SettingsActivity.this, "ログインしました: " + displayName, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
            }
        });
    }
}
