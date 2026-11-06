package com.leon.simpleeditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 Markdown → HTML 渲染器（不依赖第三方库，离线渲染）。
 * 支持：标题、粗体/斜体/删除线、行内代码、围栏代码块、引用、有序/无序/任务列表、
 *       表格、分隔线、链接、图片占位。
 */
final class MarkdownRenderer {

    private static final String CSS =
            "body{margin:0;padding:14px 16px 48px;font-family:-apple-system,'Noto Sans CJK SC',Roboto,sans-serif;"
                    + "font-size:15px;line-height:1.72;color:#1a1a1a;word-wrap:break-word;-webkit-text-size-adjust:100%;}"
                    + "h1,h2,h3,h4,h5,h6{line-height:1.35;margin:1.15em 0 .5em;font-weight:600;}"
                    + "h1{font-size:1.6em;border-bottom:1px solid #e6e6e6;padding-bottom:.28em;}"
                    + "h2{font-size:1.35em;border-bottom:1px solid #eee;padding-bottom:.24em;}"
                    + "h3{font-size:1.17em;}h4{font-size:1.04em;}h5,h6{font-size:1em;color:#444;}"
                    + "p{margin:.7em 0;}"
                    + "a{color:#1565c0;text-decoration:none;word-break:break-all;}"
                    + "code{font-family:monospace;background:#f2f4f7;padding:1px 5px;border-radius:4px;font-size:.92em;}"
                    + "pre{background:#f6f8fa;border-radius:8px;padding:12px;overflow-x:auto;margin:.85em 0;}"
                    + "pre code{background:none;padding:0;font-size:.9em;line-height:1.5;}"
                    + "blockquote{margin:.85em 0;padding:.1em 0 .1em 14px;border-left:3px solid #d0d7de;color:#57606a;}"
                    + "ul,ol{padding-left:1.5em;margin:.6em 0;}"
                    + "li{margin:.26em 0;}"
                    + "table{border-collapse:collapse;width:100%;margin:.9em 0;font-size:.94em;display:block;overflow-x:auto;}"
                    + "th,td{border:1px solid #dfe2e5;padding:7px 10px;text-align:left;}"
                    + "th{background:#f6f8fa;font-weight:600;}"
                    + "hr{border:none;border-top:1px solid #e6e6e6;margin:1.4em 0;}"
                    + "del{color:#8a8a8a;}"
                    + "img{max-width:100%;}";

    private MarkdownRenderer() {
    }

    /** 按文件名判断是不是 Markdown。 */
    static boolean isMarkdownFile(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md")
                || lower.endsWith(".markdown")
                || lower.endsWith(".mdown")
                || lower.endsWith(".mkd");
    }

    static String toHtml(String markdown) {
        return toHtml(markdown, 15);
    }

    /** fontPx 取编辑区的 sp 数值，WebView 的 CSS px 约等于 dp，视觉上基本对齐。 */
    static String toHtml(String markdown, int fontPx) {
        String body = render(markdown == null ? "" : markdown);
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>" + CSS + "body{font-size:" + fontPx + "px;}</style></head><body>"
                + body + "</body></html>";
    }

    // ------------------------------------------------------------------ 块级

    private static String render(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;

        while (i < lines.length) {
            String raw = lines[i];
            String line = raw.trim();

            if (line.isEmpty()) {
                i++;
                continue;
            }

            // 围栏代码块
            if (line.startsWith("```")) {
                String lang = line.substring(3).trim();
                i++;
                StringBuilder code = new StringBuilder();
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                if (i < lines.length) {
                    i++;
                }
                out.append("<pre><code");
                if (!lang.isEmpty()) {
                    out.append(" class=\"lang-").append(escape(lang)).append("\"");
                }
                out.append('>').append(escape(code.toString())).append("</code></pre>\n");
                continue;
            }

            // 分隔线
            if (line.matches("^([-*_])\\1{2,}$")) {
                out.append("<hr>\n");
                i++;
                continue;
            }

            // 标题
            if (line.matches("^#{1,6}\\s+.*")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                out.append("<h").append(level).append('>')
                        .append(inline(line.substring(level).trim()))
                        .append("</h").append(level).append(">\n");
                i++;
                continue;
            }

            // 表格
            if (line.contains("|") && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
                List<String> header = splitRow(line);
                i += 2;
                StringBuilder table = new StringBuilder("<table><thead><tr>");
                for (String cell : header) {
                    table.append("<th>").append(inline(cell)).append("</th>");
                }
                table.append("</tr></thead><tbody>");
                while (i < lines.length && lines[i].trim().contains("|") && !lines[i].trim().isEmpty()) {
                    List<String> cells = splitRow(lines[i].trim());
                    table.append("<tr>");
                    for (int c = 0; c < header.size(); c++) {
                        table.append("<td>").append(inline(c < cells.size() ? cells.get(c) : "")).append("</td>");
                    }
                    table.append("</tr>");
                    i++;
                }
                table.append("</tbody></table>\n");
                out.append(table);
                continue;
            }

            // 引用
            if (line.startsWith(">")) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    String content = lines[i].trim().substring(1).trim();
                    quote.append(content.isEmpty() ? "<br>" : inline(content) + "<br>");
                    i++;
                }
                out.append("<blockquote>").append(quote).append("</blockquote>\n");
                continue;
            }

            // 列表
            if (isListLine(line)) {
                boolean ordered = line.matches("^\\d+[.)]\\s+.*");
                out.append(ordered ? "<ol>\n" : "<ul>\n");
                while (i < lines.length && isListLine(lines[i].trim())) {
                    String content = lines[i].trim()
                            .replaceFirst("^(?:[-*+]|\\d+[.)])\\s+", "");
                    content = content.replaceFirst("^\\[ \\]\\s*", "☐ ")
                            .replaceFirst("^\\[[xX]\\]\\s*", "☑ ");
                    out.append("<li>").append(inline(content)).append("</li>\n");
                    i++;
                }
                out.append(ordered ? "</ol>\n" : "</ul>\n");
                continue;
            }

            // 段落（至少吃掉一行，避免死循环）
            StringBuilder para = new StringBuilder(line);
            i++;
            while (i < lines.length && !lines[i].trim().isEmpty() && !startsBlock(lines[i])) {
                para.append(' ').append(lines[i].trim());
                i++;
            }
            out.append("<p>").append(inline(para.toString())).append("</p>\n");
        }
        return out.toString();
    }

    private static boolean startsBlock(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return true;
        }
        if (line.startsWith("```") || line.startsWith(">")) {
            return true;
        }
        if (line.matches("^([-*_])\\1{2,}$") || line.matches("^#{1,6}\\s+.*")) {
            return true;
        }
        return isListLine(line);
    }

    private static boolean isListLine(String line) {
        return line.matches("^(?:[-*+]|\\d+[.)])\\s+.*");
    }

    private static boolean isTableSeparator(String line) {
        String t = line.trim();
        if (!t.contains("|") || !t.contains("-")) {
            return false;
        }
        return t.replace("|", "").replace(":", "").replace("-", "").trim().isEmpty();
    }

    private static List<String> splitRow(String line) {
        String t = line.trim();
        if (t.startsWith("|")) {
            t = t.substring(1);
        }
        if (t.endsWith("|")) {
            t = t.substring(0, t.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : t.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    // ------------------------------------------------------------------ 行内

    private static String inline(String text) {
        String s = escape(text);

        // 先把行内代码抽出来，避免里面的符号被当作格式
        List<String> codes = new ArrayList<>();
        Matcher m = Pattern.compile("`([^`]+)`").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            codes.add(m.group(1));
            m.appendReplacement(sb, "\u0000" + (codes.size() - 1) + "\u0000");
        }
        m.appendTail(sb);
        s = sb.toString();

        // 图片 -> 占位文字（WebView 里没有图片访问权限）
        s = s.replaceAll("!\\[([^\\]]*)\\]\\(([^)]*)\\)", "［图片：$1］");
        // 链接
        s = s.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", "<a href=\"$2\">$1</a>");
        // 删除线 / 粗体 / 斜体
        s = s.replaceAll("~~(.+?)~~", "<del>$1</del>");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        s = s.replaceAll("__(.+?)__", "<strong>$1</strong>");
        s = s.replaceAll("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)", "<em>$1</em>");
        s = s.replaceAll("(?<!_)_([^_\\n]+?)_(?!_)", "<em>$1</em>");

        for (int i = 0; i < codes.size(); i++) {
            s = s.replace("\u0000" + i + "\u0000", "<code>" + codes.get(i) + "</code>");
        }
        return s;
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
