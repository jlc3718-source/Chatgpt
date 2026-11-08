package com.jason.browsercompanion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONTokener;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<WebView> tabs = new ArrayList<>();
    private int activeTab = -1;
    private EditText address;
    private TextView tabLabel;
    private ProgressBar progress;
    private LinearLayout browserHost;
    private int ink = Color.rgb(35, 32, 40);
    private int accent = Color.rgb(103, 80, 164);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246, 244, 250));
        getWindow().setNavigationBarColor(Color.rgb(246, 244, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
        if (tabs.isEmpty()) newTab("https://www.google.com");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 244, 250));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(8), dp(8));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(15);
        address.setHint("Search or enter address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(14), dp(5), dp(14), dp(5));
        address.setBackground(roundRect(Color.WHITE, dp(24)));
        top.addView(address, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button go = button("Go", true);
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(54), dp(44));
        goLp.leftMargin = dp(7);
        top.addView(go, goLp);
        go.setOnClickListener(v -> navigateFromAddress());
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateFromAddress(); return true;
            }
            return false;
        });
        root.addView(top);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(8), 0, dp(8), dp(7));
        Button back = button("‹", false), forward = button("›", false), reload = button("↻", false);
        tabLabel = new TextView(this);
        tabLabel.setTextColor(ink); tabLabel.setTextSize(13); tabLabel.setGravity(Gravity.CENTER);
        Button tabsButton = button("Tabs", false), bookmark = button("☆", false), share = button("Share", true), menu = button("⋮", false);
        share.setContentDescription("Share page with ChatGPT");
        controls.addView(back, new LinearLayout.LayoutParams(dp(42), dp(40)));
        controls.addView(forward, new LinearLayout.LayoutParams(dp(42), dp(40)));
        controls.addView(reload, new LinearLayout.LayoutParams(dp(42), dp(40)));
        controls.addView(tabLabel, new LinearLayout.LayoutParams(0, dp(40), 1));
        controls.addView(tabsButton, new LinearLayout.LayoutParams(dp(52), dp(40)));
        controls.addView(bookmark, new LinearLayout.LayoutParams(dp(42), dp(40)));
        controls.addView(share, new LinearLayout.LayoutParams(dp(60), dp(40)));
        controls.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(controls);
        back.setOnClickListener(v -> { WebView w = current(); if (w != null && w.canGoBack()) w.goBack(); });
        forward.setOnClickListener(v -> { WebView w = current(); if (w != null && w.canGoForward()) w.goForward(); });
        reload.setOnClickListener(v -> { WebView w = current(); if (w != null) w.reload(); });
        tabsButton.setOnClickListener(v -> showTabPicker());
        bookmark.setOnClickListener(v -> addBookmark());
        share.setOnClickListener(v -> shareCurrentPage());
        menu.setOnClickListener(v -> showMenu(v));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        browserHost = new LinearLayout(this);
        browserHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(browserHost, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void newTab(String url) {
        WebView web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { if (view == current()) address.setText(url); }
            @Override public void onPageFinished(WebView view, String url) { if (view == current()) { address.setText(url); progress.setVisibility(View.GONE); updateTabLabel(); } }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { if (view == current()) { progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE); progress.setProgress(value); } }
            @Override public void onReceivedTitle(WebView view, String title) { if (view == current()) updateTabLabel(); }
        });
        tabs.add(web);
        selectTab(tabs.size() - 1);
        web.loadUrl(url);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (activeTab >= 0) browserHost.removeView(tabs.get(activeTab));
        activeTab = index;
        browserHost.addView(tabs.get(activeTab), new LinearLayout.LayoutParams(-1, -1));
        WebView w = current();
        address.setText(w.getUrl() == null ? "" : w.getUrl());
        progress.setVisibility(View.GONE);
        updateTabLabel();
    }

    private void navigateFromAddress() {
        String input = address.getText().toString().trim();
        if (input.isEmpty()) return;
        String url;
        if (input.matches("(?i)^[a-z][a-z0-9+.-]*://.*") || input.startsWith("about:")) url = input;
        else if (input.matches("(?i)^[^\\s]+\\.[^\\s]+$")) url = "https://" + input;
        else url = "https://www.google.com/search?q=" + Uri.encode(input);
        WebView w = current(); if (w != null) w.loadUrl(url);
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
    }

    private void shareCurrentPage() {
        WebView w = current();
        if (w == null) return;
        w.evaluateJavascript("JSON.stringify({title:document.title,url:location.href,text:(document.body?document.body.innerText:'').slice(0,16000)})", (ValueCallback<String>) raw -> {
            try {
                Object parsed = new JSONTokener(raw).nextValue();
                org.json.JSONObject page = new org.json.JSONObject((String) parsed);
                String content = "Use this webpage content to help me.\n\nTitle: " + page.optString("title") + "\nURL: " + page.optString("url") + "\n\n" + page.optString("text");
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT, content);
                startActivity(Intent.createChooser(intent, "Share page with ChatGPT"));
            } catch (Exception ex) { toast("Could not read this page. Try again after it finishes loading."); }
        });
    }

    private void addBookmark() {
        WebView w = current(); if (w == null || w.getUrl() == null) return;
        getPreferences(MODE_PRIVATE).edit().putString("bookmark_" + System.currentTimeMillis(), w.getTitle() + "\n" + w.getUrl()).apply();
        toast("Bookmark saved on this phone");
    }

    private void showMenu(View anchor) {
        String[] items = {"New tab", "Bookmarks", "Clear browsing data", "About"};
        new AlertDialog.Builder(this).setTitle("Browser menu").setItems(items, (d, which) -> {
            if (which == 0) newTab("https://www.google.com");
            if (which == 1) showBookmarks();
            if (which == 2) confirmClearData();
            if (which == 3) new AlertDialog.Builder(this).setTitle("ChatGPT Browser Companion").setMessage("A free, on-device Android browser. Page sharing happens only when you tap the share button.\n\nThis app does not remotely control Samsung Internet or connect directly to ChatGPT in the background.").setPositiveButton("OK", null).show();
        }).show();
    }

    private void showBookmarks() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        java.util.Map<String, ?> all = prefs.getAll();
        ArrayList<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        for (String key : all.keySet()) if (key.startsWith("bookmark_")) { keys.add(key); labels.add(((String)all.get(key)).split("\n", 2)[0]); }
        if (keys.isEmpty()) { toast("No bookmarks yet"); return; }
        String[] list = labels.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Bookmarks").setItems(list, (d, which) -> {
            String value = prefs.getString(keys.get(which), "");
            if (value.contains("\n")) { WebView w = current(); if (w != null) w.loadUrl(value.substring(value.indexOf('\n') + 1)); }
        }).setNegativeButton("Close", null).show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this).setTitle("Clear browsing data?").setMessage("This clears cookies, site storage, cache, and saved bookmarks on this phone. You may be signed out of websites.").setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> {
            CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush();
            WebView.clearClientCertPreferences(); WebView web = current();
            if (web != null) { web.clearCache(true); web.clearFormData(); web.clearHistory(); web.clearSslPreferences(); }
            getPreferences(MODE_PRIVATE).edit().clear().apply(); toast("Browsing data cleared");
        }).show();
    }

    private void showTabPicker() {
        if (tabs.isEmpty()) { newTab("https://www.google.com"); return; }
        String[] labels = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) labels[i] = (i == activeTab ? "● " : "") + (tabs.get(i).getTitle() == null ? "New tab" : tabs.get(i).getTitle());
        labels[tabs.size()] = "+  New tab";
        new AlertDialog.Builder(this).setTitle("Open tabs").setItems(labels, (d, which) -> { if (which == tabs.size()) newTab("https://www.google.com"); else selectTab(which); }).setNeutralButton("Close current tab", (d, w) -> closeCurrentTab()).setNegativeButton("Done", null).show();
    }

    private void closeCurrentTab() {
        if (tabs.size() <= 1) { WebView w = current(); if (w != null) w.loadUrl("about:blank"); return; }
        WebView old = current(); browserHost.removeView(old); old.stopLoading(); old.destroy(); tabs.remove(activeTab);
        activeTab = Math.min(activeTab, tabs.size() - 1); selectTab(activeTab);
    }

    private void updateTabLabel() { if (tabLabel != null) tabLabel.setText((activeTab + 1) + " / " + tabs.size()); }
    private WebView current() { return activeTab >= 0 && activeTab < tabs.size() ? tabs.get(activeTab) : null; }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(label.length() > 5 ? 10 : 15); b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(4), 0, dp(4), 0); b.setTextColor(primary ? Color.WHITE : ink);
        b.setBackground(roundRect(primary ? accent : Color.rgb(238, 234, 244), dp(18)));
        return b;
    }
    private GradientDrawable roundRect(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() { WebView w = current(); if (w != null && w.canGoBack()) w.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { for (WebView w : tabs) { w.stopLoading(); w.destroy(); } tabs.clear(); super.onDestroy(); }
}
