package top.hcode.hoj.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 上传文件扩展名的统一解析与校验。
 */
public final class UploadFileTypes {

    /** 头像、Markdown 图片允许的扩展名 */
    public static final Set<String> IMAGE_SUFFIXES = immutableSet("jpg", "jpeg", "gif", "png", "webp");

    /** 首页轮播图允许的扩展名（仅管理员可上传） */
    public static final Set<String> CAROUSEL_SUFFIXES = immutableSet("jpg", "jpeg", "gif", "png", "webp", "jfif", "svg");

    /** 浏览器会当作页面或脚本执行的类型，禁止作为附件上传 */
    public static final Set<String> ACTIVE_CONTENT_SUFFIXES = immutableSet(
            "html", "htm", "xhtml", "xht", "shtml", "mht", "mhtml", "svg", "svgz",
            "xml", "xsl", "xslt", "js", "mjs", "swf", "hta", "jsp", "jspx", "php", "asp", "aspx");

    /** 可在浏览器内直接打开的安全类型；其余类型访问时一律按附件下载 */
    public static final Set<String> INLINE_SAFE_SUFFIXES = immutableSet(
            "jpg", "jpeg", "gif", "png", "webp", "bmp", "ico", "jfif", "pdf");

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("[a-z0-9]{1,16}");

    private UploadFileTypes() {
    }

    /**
     * 取文件名最后一个点之后的扩展名并转为小写；没有扩展名或扩展名含异常字符时返回空串。
     */
    public static String getSuffix(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = filename.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String suffix = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SUFFIX_PATTERN.matcher(suffix).matches() ? suffix : "";
    }

    public static boolean isAllowed(String suffix, Set<String> allowedSuffixes) {
        return suffix != null && !suffix.isEmpty() && allowedSuffixes.contains(suffix);
    }

    public static boolean isActiveContent(String suffix) {
        return ACTIVE_CONTENT_SUFFIXES.contains(suffix);
    }

    public static boolean isInlineSafe(String suffix) {
        return INLINE_SAFE_SUFFIXES.contains(suffix);
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
