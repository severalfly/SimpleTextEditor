package com.leon.simpleeditor;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileEntryAdapter extends BaseAdapter {

    private final Context context;
    private final List<DocumentFile> data = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public FileEntryAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<DocumentFile> items) {
        data.clear();
        if (items != null) {
            data.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public DocumentFile getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_entry, parent, false);
            holder = new ViewHolder();
            holder.title = convertView.findViewById(R.id.item_title);
            holder.subtitle = convertView.findViewById(R.id.item_subtitle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DocumentFile file = data.get(position);
        String name = file.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "(未命名)";
        }

        holder.title.setText(name);
        if (file.isDirectory()) {
            holder.title.setTextColor(context.getColor(R.color.folder_name));
            holder.subtitle.setText(context.getString(R.string.folder_label));
        } else {
            holder.title.setTextColor(context.getColor(R.color.text_primary));
            String size = Formatter.formatShortFileSize(context, file.length());
            long modified = file.lastModified();
            String date = modified > 0 ? dateFormat.format(new Date(modified)) : "";
            String detail = date.isEmpty() ? size : size + "    " + date;
            if (MarkdownRenderer.isMarkdownFile(name)) {
                detail = context.getString(R.string.markdown_badge) + "  ·  " + detail;
            }
            holder.subtitle.setText(detail);
        }
        return convertView;
    }

    private static class ViewHolder {
        TextView title;
        TextView subtitle;
    }
}
