package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Intent;
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
    private static final String CLIENT_ID = "YOUR_CLIENT_ID"; // 本来はソース管理外にすべきだがMVPとして定義
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, findViewById(R.id.login_status), findViewById(R.id.btn_login), 
                    findViewById(R.id.btn_user_page), findViewById(R.id.btn_logout));
    }

    private void handleIntent(Intent intent, TextView loginStatus, Button btnLogin, Button btnUserPage, Button btnLogout) {
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(REDIRECT_URI)) {
            String code = data.getQueryParameter("code");
            if (code != null) {
                // TODO: 本来はここでサーバーサイドまたはRetrofitでトークン交換を行う
                // MVPの簡略化として、コードが取得できたら成功（デモ用）
                authRepository.saveAccessToken("dummy_token_" + code);
                authRepository.saveUserName("OSMユーザー");
                updateUi(loginStatus, btnLogin, btnUserPage, btnLogout);
                Toast.makeText(this, "ログインしました", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
