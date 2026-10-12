package com.munna.dc_calculator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.util.concurrent.Executor;

/** Local/offline Android host for the bundled DC Calculator UI. */
public final class MainActivity extends Activity {
    private WebView webView;
    private VipBillingManager vipBillingManager;
    private static final int APP_BG = Color.rgb(237, 240, 247);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(APP_BG));
        window.setStatusBarColor(APP_BG);
        window.setNavigationBarColor(APP_BG);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(APP_BG);
        webView = new WebView(this);
        webView.setBackgroundColor(APP_BG);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setGeolocationEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // All app UI is packaged locally; refuse unexpected non-file navigation.
                return !"file".equalsIgnoreCase(request.getUrl().getScheme());
            }
        });

        vipBillingManager = new VipBillingManager(this, webView);
        webView.addJavascriptInterface(vipBillingManager.javascriptBridge(), "DCPlayBilling");
        webView.addJavascriptInterface(new VaultBridge(), "DCCalculatorNative");

        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onResume() {
        super.onResume();
        if (vipBillingManager != null) vipBillingManager.refresh();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function(){if(window.DCCalculatorHandleBack){return !!window.DCCalculatorHandleBack();}return false;})()",
                value -> {
                    if (!"true".equals(value)) {
                        if (webView != null && webView.canGoBack()) webView.goBack();
                        else MainActivity.super.onBackPressed();
                    }
                });
            return;
        }
        super.onBackPressed();
    }

    /** Native device-authentication gate for the locally encrypted VIP vault.
     * The vault passphrase remains required after this system prompt succeeds. */
    public final class VaultBridge {
        @JavascriptInterface
        public void authenticateVault(String requestId) {
            if (requestId == null || !requestId.matches("[0-9]{1,12}")) return;
            runOnUiThread(() -> {
                if (webView == null || isFinishing()) {
                    deliverVaultAuth(requestId, false);
                    return;
                }
                Executor executor = ContextCompat.getMainExecutor(MainActivity.this);
                BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        deliverVaultAuth(requestId, true);
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        deliverVaultAuth(requestId, false);
                    }
                };
                BiometricPrompt prompt = new BiometricPrompt(MainActivity.this, executor, callback);
                BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock DC Calculator Private Vault")
                    .setSubtitle("Confirm with your phone's biometric or screen lock");
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                } else {
                    // Older Android versions use the compatible legacy device-credential option.
                    builder.setDeviceCredentialAllowed(true);
                }
                prompt.authenticate(builder.build());
            });
        }

        private void deliverVaultAuth(String requestId, boolean success) {
            final WebView current = webView;
            if (current == null) return;
            final String quotedId = JSONObject.quote(requestId);
            current.post(() -> {
                if (webView == current) {
                    current.evaluateJavascript(
                        "if(window.onDCCalculatorVaultAuthResult){window.onDCCalculatorVaultAuthResult("
                            + quotedId + "," + success + ");}", null);
                }
            });
        }
    }

    @Override protected void onDestroy() {
        if (vipBillingManager != null) { vipBillingManager.close(); vipBillingManager = null; }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
