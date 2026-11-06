package com.leon.simpleeditor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 单文件文本编辑器：打开即读，改完自动回写。
 * 自动保存策略：停止输入 1.2 秒后保存；退出 / 切后台时再保存一次。
 */
public class EditorActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";

    private static final long MAX_BYTES = 3L * 1024 * 1024;     // 超过 3MB 不编辑，避免写回时丢内容
    private static final long AUTO_SAVE_DELAY_MS = 1200L;

    private static final String PREF_NAME = "editor_prefs";
    private static final String KEY_FONT_SP = "font_sp";
    private static final int DEFAULT_FONT_SP = 16;
    private static final int[] FONT_CHOICES = {12, 14, 16, 18, 20, 22, 26, 30, 36};

    private EditText editor;
    private WebView previewView;
    private TextView statusView;
    private Button previewButton;
    private Button fontButton;
    private int fontSp = DEFAULT_FONT_SP;

    private Uri uri;
    private String charsetName = "UTF-8";
    private boolean utf8Bom;
    private boolean crlf;
    private boolean loading = true;
    private boolean dirty;
    private boolean markdown;
    private boolean previewMode;
    private String lastEditStatus = "自动保存已开启";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Runnable autoSaveTask = new Runnable() {
        @Override
        public void run() {
            save(false);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        editor = findViewById(R.id.editor);
        statusView = findViewById(R.id.status);
        previewView = findViewById(R.id.preview);
        previewButton = findViewById(R.id.btn_preview);
        Button saveButton = findViewById(R.id.btn_save);
        saveButton.setOnClickListener(v -> save(true));

        previewView.setBackgroundColor(Color.WHITE);
        previewView.getSettings().setJavaScriptEnabled(false);
        previewView.getSettings().setSupportZoom(true);
        previewView.getSettings().setBuiltInZoomControls(false);

        fontButton = findViewById(R.id.btn_font);
        fontSp = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(KEY_FONT_SP, DEFAULT_FONT_SP);
        applyFontSize();
        fontButton.setOnClickListener(v -> showFontDialog());

        uri = getIntent().getData();
        String name = getIntent().getStringExtra(EXTRA_NAME);

        markdown = MarkdownRenderer.isMarkdownFile(name);
        previewButton.setVisibility(markdown ? View.VISIBLE : View.GONE);
        previewButton.setOnClickListener(v -> togglePreview());

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(name == null || name.isEmpty() ? "编辑" : name);
            bar.setSubtitle(markdown ? "自动保存 · 支持 Markdown 预览" : "自动保存已开启");
        }

        if (uri == null) {
            Toast.makeText(this, "没有拿到文件地址", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (loading) {
                    return;
                }
                dirty = true;
                setStatus("编辑中…");
                handler.removeCallbacks(autoSaveTask);
                handler.postDelayed(autoSaveTask, AUTO_SAVE_DELAY_MS);
            }
        });

        load();
    }

    // ---------------------------------------------------------------- 读取

    private void load() {
        byte[] data;
        try {
            data = readAll(uri);
        } catch (Exception e) {
            Toast.makeText(this, "打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (data.length > MAX_BYTES) {
            Toast.makeText(this, "文件超过 3MB，暂不支持编辑", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (TextCodec.looksBinary(data)) {
            Toast.makeText(this, "这不是文本文件，无法编辑", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int offset = 0;
        utf8Bom = TextCodec.hasUtf8Bom(data);
        if (utf8Bom) {
            offset = 3;
        }
        Charset charset = TextCodec.detectCharset(data, offset, data.length - offset);
        charsetName = charset.name();

        String text = new String(data, offset, data.length - offset, charset);
        crlf = text.contains("\r\n");
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        loading = true;
        editor.setText(text);
        editor.setSelection(0);
        loading = false;
        dirty = false;

        setStatus("已加载 · " + charsetName + " · " + data.length + " 字节");
    }

    private byte[] readAll(Uri target) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(target)) {
            if (in == null) {
                throw new IOException("无法打开文件");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                total += read;
                if (total > MAX_BYTES) {
                    break;
                }
            }
            return buffer.toByteArray();
        }
    }

    // ---------------------------------------------------------------- 保存

    private void save(boolean manual) {
        handler.removeCallbacks(autoSaveTask);
        if (uri == null) {
            return;
        }
        if (!dirty && !manual) {
            return;
        }

        String text = editor.getText().toString();
        if (crlf) {
            text = text.replace("\n", "\r\n");
        }

        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IOException("无法写入该文件");
            }
            if (utf8Bom) {
                out.write(TextCodec.UTF8_BOM);
            }
            out.write(text.getBytes(charsetName));
            out.flush();
            dirty = false;
            setStatus("已保存 " + clock.format(new Date()) + " · " + charsetName);
        } catch (Exception e) {
            setStatus("保存失败：" + e.getMessage());
            if (manual) {
                Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- 字号

    private void applyFontSize() {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
        fontButton.setText(getString(R.string.font_size) + " " + fontSp);
        if (previewMode) {
            renderPreview();
        }
    }

    private void showFontDialog() {
        String[] labels = new String[FONT_CHOICES.length + 1];
        for (int i = 0; i < FONT_CHOICES.length; i++) {
            int sp = FONT_CHOICES[i];
            labels[i] = sp + " sp" + (sp == fontSp ? getString(R.string.font_current) : "");
        }
        labels[FONT_CHOICES.length] = getString(R.string.font_reset);
        new AlertDialog.Builder(this)
                .setTitle(R.string.font_dialog_title)
                .setItems(labels, (dialog, which) -> {
                    int chosen = which < FONT_CHOICES.length ? FONT_CHOICES[which] : DEFAULT_FONT_SP;
                    if (chosen == fontSp) {
                        return;
                    }
                    fontSp = chosen;
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                            .edit().putInt(KEY_FONT_SP, fontSp).apply();
                    applyFontSize();
                    setStatus(getString(R.string.font_size) + " " + fontSp + "sp");
                })
                .show();
    }

    // ---------------------------------------------------------------- Markdown 预览

    private void renderPreview() {
        previewView.loadDataWithBaseURL(null,
                MarkdownRenderer.toHtml(editor.getText().toString(), fontSp),
                "text/html", "utf-8", null);
    }

    private void togglePreview() {
        if (!markdown) {
            return;
        }
        if (!previewMode) {
            save(true);     // 预览前先落盘，保证看到的就是文件内容
            renderPreview();
            previewView.setVisibility(View.VISIBLE);
            editor.setVisibility(View.GONE);
            previewMode = true;
            previewButton.setText(R.string.edit);
            hideKeyboard();
            statusView.setText("预览模式，点「编辑」返回修改");
        } else {
            previewView.setVisibility(View.GONE);
            editor.setVisibility(View.VISIBLE);
            previewMode = false;
            previewButton.setText(R.string.preview);
            statusView.setText(lastEditStatus);
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (previewMode) {
            togglePreview();
            return;
        }
        super.onBackPressed();
    }

    private void setStatus(String text) {
        if (!previewMode) {
            lastEditStatus = text;
        }
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(autoSaveTask);
        save(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(autoSaveTask);
        save(false);
        super.onStop();
    }
}
