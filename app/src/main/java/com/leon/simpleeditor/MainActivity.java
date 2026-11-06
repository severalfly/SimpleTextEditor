package com.leon.simpleeditor;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_TREE = 1001;
    private static final int REQ_FILE = 1002;
    private static final String PREF = "editor";
    private static final String KEY_TREE = "folder_uri";

    private ListView listView;
    private TextView pathView;
    private TextView emptyView;
    private FileEntryAdapter adapter;

    private DocumentFile currentDir;
    private final List<DocumentFile> items = new ArrayList<>();
    private boolean firstResume = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        pathView = findViewById(R.id.path);
        emptyView = findViewById(R.id.empty);

        adapter = new FileEntryAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            DocumentFile file = items.get(position);
            if (file.isDirectory()) {
                currentDir = file;
                reload();
            } else {
                openEditor(file);
            }
        });

        findViewById(R.id.btn_folder).setOnClickListener(v -> pickFolder());
        findViewById(R.id.btn_file).setOnClickListener(v -> pickFile());
        findViewById(R.id.btn_new).setOnClickListener(v -> showNewFileDialog());
        findViewById(R.id.btn_up).setOnClickListener(v -> goUp());

        restoreFolder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        // 从编辑器返回时刷新一下大小 / 修改时间
        if (currentDir != null && currentDir.exists()) {
            reload();
        }
    }

    // ---------------------------------------------------------------- 选择入口

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件管理器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件管理器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // 某些第三方文件管理器不支持持久授权，不影响本次使用
        }

        if (requestCode == REQ_TREE) {
            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                    .putString(KEY_TREE, uri.toString())
                    .apply();
            DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
            if (dir == null) {
                Toast.makeText(this, "无法打开这个文件夹", Toast.LENGTH_LONG).show();
                return;
            }
            currentDir = dir;
            reload();
        } else if (requestCode == REQ_FILE) {
            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
            String name = file != null ? file.getName() : null;
            openEditorByUri(uri, name);
        }
    }

    // ---------------------------------------------------------------- 目录浏览

    private void restoreFolder() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String saved = sp.getString(KEY_TREE, null);
        if (saved == null) {
            showHint();
            return;
        }
        Uri uri;
        try {
            uri = Uri.parse(saved);
        } catch (Exception e) {
            showHint();
            return;
        }
        if (!hasPersistedRead(uri)) {
            showHint();
            return;
        }
        DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
        if (dir == null || !dir.exists() || !dir.canRead()) {
            showHint();
            return;
        }
        currentDir = dir;
        reload();
    }

    private boolean hasPersistedRead(Uri uri) {
        ContentResolver cr = getContentResolver();
        for (UriPermission p : cr.getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isReadPermission()) {
                return true;
            }
        }
        return false;
    }

    private void reload() {
        items.clear();
        if (currentDir == null) {
            showHint();
            return;
        }

        DocumentFile[] files;
        try {
            files = currentDir.listFiles();
        } catch (Exception e) {
            Toast.makeText(this, "读取文件夹失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            files = new DocumentFile[0];
        }

        for (DocumentFile f : files) {
            String n = f.getName();
            if (n != null && n.startsWith(".")) {
                continue;   // 跳过隐藏文件
            }
            items.add(f);
        }

        Collections.sort(items, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                boolean da = a.isDirectory();
                boolean db = b.isDirectory();
                if (da != db) {
                    return da ? -1 : 1;      // 目录排前面
                }
                String na = a.getName() == null ? "" : a.getName();
                String nb = b.getName() == null ? "" : b.getName();
                return na.compareToIgnoreCase(nb);
            }
        });

        adapter.setData(items);

        String dirName = currentDir.getName();
        if (dirName == null || dirName.trim().isEmpty()) {
            dirName = "文件夹";
        }
        pathView.setText(dirName + "   ·   " + items.size() + " 项");

        boolean empty = items.isEmpty();
        if (empty) {
            emptyView.setText(R.string.empty_folder);
        }
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showHint() {
        items.clear();
        adapter.setData(items);
        pathView.setText(R.string.no_folder);
        listView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(R.string.no_folder);
    }

    private boolean goUp() {
        if (currentDir == null) {
            return false;
        }
        DocumentFile parent = currentDir.getParentFile();
        if (parent == null) {
            Toast.makeText(this, "已经是根目录了", Toast.LENGTH_SHORT).show();
            return false;
        }
        currentDir = parent;
        reload();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (currentDir != null && currentDir.getParentFile() != null) {
            goUp();
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- 新建文件

    private void showNewFileDialog() {
        if (currentDir == null) {
            Toast.makeText(this, "先选一个文件夹，才知道建在哪里", Toast.LENGTH_LONG).show();
            return;
        }
        if (!currentDir.canWrite()) {
            Toast.makeText(this, "这个文件夹没有写入权限", Toast.LENGTH_LONG).show();
            return;
        }

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.new_file_name_hint);
        input.setText("未命名.md");
        input.setSelection(0, input.getText().length());

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.new_file)
                .setView(wrap)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (name.contains("/") || name.contains("\\") || name.contains(":")) {
                        Toast.makeText(this, "文件名里不能有 / \\ :", Toast.LENGTH_LONG).show();
                        return;
                    }
                    createFile(name);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String mime = "text/plain";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".mdown") || lower.endsWith(".mkd")) {
            mime = "text/markdown";
        }

        DocumentFile created = null;
        try {
            created = currentDir.createFile(mime, name);
        } catch (Exception ignored) {
            // 某些 provider 不认 text/markdown，下面用 text/plain 再试一次
        }
        if (created == null && !"text/plain".equals(mime)) {
            try {
                created = currentDir.createFile("text/plain", name);
            } catch (Exception ignored) {
                // 落到下面的失败提示
            }
        }

        if (created == null) {
            Toast.makeText(this, "创建失败：这个文件夹可能不允许新建文件", Toast.LENGTH_LONG).show();
            return;
        }
        reload();
        openEditor(created);
    }

    // ---------------------------------------------------------------- 打开编辑

    private void openEditor(DocumentFile file) {
        openEditorByUri(file.getUri(), file.getName());
    }

    private void openEditorByUri(Uri uri, String name) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.setData(uri);
        intent.putExtra(EditorActivity.EXTRA_NAME, name);
        startActivity(intent);
    }
}
