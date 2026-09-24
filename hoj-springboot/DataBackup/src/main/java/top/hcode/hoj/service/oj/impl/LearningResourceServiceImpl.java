package top.hcode.hoj.service.oj.impl;

import org.apache.shiro.SecurityUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.service.oj.LearningResourceService;
import top.hcode.hoj.shiro.AccountProfile;
import top.hcode.hoj.utils.Constants;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LearningResourceServiceImpl implements LearningResourceService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final List<String> FOLDER_COLORS = Arrays.asList(
            "#409EFF", "#F2BE22", "#22B573", "#8B3FD9", "#FF7A35");
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp"));
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(
            Arrays.asList("txt", "md", "csv", "json", "xml", "log", "c", "cpp",
                    "cc", "java", "py", "js", "ts", "css", "yml", "yaml"));
    private static final Set<String> OFFICE_PREVIEW_EXTENSIONS = new HashSet<>(
            Arrays.asList("docx", "xls", "xlsx", "pptx"));

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public CommonResult<List<Map<String, Object>>> getFolders() {
        List<Map<String, Object>> folders = jdbcTemplate.query(
                "SELECT f.id, f.name, f.color, f.creator_username, f.gmt_create, " +
                        "COUNT(r.id) AS file_count " +
                        "FROM learning_resource_folder f " +
                        "LEFT JOIN learning_resource_file r ON r.folder_id = f.id " +
                        "GROUP BY f.id, f.name, f.color, f.creator_username, f.gmt_create, f.sort_order " +
                        "ORDER BY f.sort_order ASC, f.id ASC",
                (rs, rowNum) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", rs.getLong("id"));
                    result.put("name", rs.getString("name"));
                    result.put("color", rs.getString("color"));
                    result.put("creatorUsername", rs.getString("creator_username"));
                    result.put("fileCount", rs.getLong("file_count"));
                    result.put("gmtCreate", rs.getTimestamp("gmt_create"));
                    return result;
                });
        return CommonResult.successResponse(folders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Object>> createFolder(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (!StringUtils.hasText(normalizedName)) {
            return CommonResult.errorResponse("资料库名称不能为空！");
        }
        if (normalizedName.length() > 60) {
            return CommonResult.errorResponse("资料库名称不能超过 60 个字符！");
        }

        AccountProfile profile = currentProfile();
        Integer folderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_resource_folder", Integer.class);
        int count = folderCount == null ? 0 : folderCount;
        int maxSort = nullableInt(jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) FROM learning_resource_folder",
                Integer.class));
        String color = FOLDER_COLORS.get(count % FOLDER_COLORS.size());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO learning_resource_folder " +
                                "(name, color, creator_uid, creator_username, sort_order, " +
                                "gmt_create, gmt_modified) VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, normalizedName);
                statement.setString(2, color);
                statement.setString(3, profile.getUid());
                statement.setString(4, profile.getUsername());
                statement.setInt(5, maxSort + 1);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            return CommonResult.errorResponse("已存在同名资料库！");
        }

        Number key = keyHolder.getKey();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", key == null ? null : key.longValue());
        result.put("name", normalizedName);
        result.put("color", color);
        result.put("creatorUsername", profile.getUsername());
        result.put("fileCount", 0);
        return CommonResult.successResponse(result, "资料库创建成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> deleteFolder(Long folderId) {
        if (!folderExists(folderId)) {
            return CommonResult.errorResponse("资料库不存在！");
        }
        Long fileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_resource_file WHERE folder_id = ?",
                Long.class,
                folderId);
        if (fileCount != null && fileCount > 0) {
            return CommonResult.errorResponse("该资料库中仍有文件，请先删除文件后再删除资料库！");
        }
        jdbcTemplate.update("DELETE FROM learning_resource_folder WHERE id = ?", folderId);
        return CommonResult.successResponse("资料库删除成功");
    }

    @Override
    public CommonResult<Map<String, Object>> getFiles(
            Long folderId,
            Integer currentPage,
            Integer limit) {
        if (!folderExists(folderId)) {
            return CommonResult.errorResponse("资料库不存在！");
        }
        int page = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int size = limit == null || limit < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(limit, MAX_PAGE_SIZE);
        long total = nullableLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_resource_file WHERE folder_id = ?",
                Long.class,
                folderId));
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = jdbcTemplate.query(
                "SELECT id, folder_id, original_name, content_type, file_size, " +
                        "uploader_username, gmt_create " +
                        "FROM learning_resource_file WHERE folder_id = ? " +
                        "ORDER BY gmt_create DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    String originalName = rs.getString("original_name");
                    String contentType = rs.getString("content_type");
                    result.put("id", rs.getLong("id"));
                    result.put("folderId", rs.getLong("folder_id"));
                    result.put("name", originalName);
                    result.put("contentType", contentType);
                    result.put("size", rs.getLong("file_size"));
                    result.put("uploaderUsername", rs.getString("uploader_username"));
                    result.put("gmtCreate", rs.getTimestamp("gmt_create"));
                    result.put("extension", extension(originalName));
                    result.put("previewable", isPreviewable(originalName, contentType));
                    result.put("category", category(originalName, contentType));
                    return result;
                },
                folderId,
                size,
                offset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("currentPage", page);
        result.put("limit", size);
        return CommonResult.successResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Object>> uploadFile(Long folderId, MultipartFile file) {
        if (!folderExists(folderId)) {
            return CommonResult.errorResponse("资料库不存在！");
        }
        if (file == null || file.isEmpty()) {
            return CommonResult.errorResponse("请选择需要上传的文件！");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return CommonResult.errorResponse("单个文件不能超过 50 MB！");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        if (!StringUtils.hasText(originalName)) {
            return CommonResult.errorResponse("文件名无效！");
        }
        String ext = extension(originalName);
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + (StringUtils.hasText(ext) ? "." + ext : "");
        Path folderPath = resourceRoot().resolve(String.valueOf(folderId)).normalize();
        Path target = folderPath.resolve(storedName).normalize();
        if (!target.startsWith(folderPath)) {
            return CommonResult.errorResponse("文件路径无效！");
        }

        try {
            Files.createDirectories(folderPath);
            file.transferTo(target.toFile());
        } catch (IOException exception) {
            return CommonResult.errorResponse("文件保存失败，请稍后重试！");
        }

        String contentType = normalizedContentType(originalName, file.getContentType());
        AccountProfile profile = currentProfile();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO learning_resource_file " +
                                "(folder_id, original_name, stored_name, content_type, file_size, " +
                                "uploader_uid, uploader_username, gmt_create, gmt_modified) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, folderId);
                statement.setString(2, originalName);
                statement.setString(3, storedName);
                statement.setString(4, contentType);
                statement.setLong(5, file.getSize());
                statement.setString(6, profile.getUid());
                statement.setString(7, profile.getUsername());
                return statement;
            }, keyHolder);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }

        Number key = keyHolder.getKey();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", key == null ? null : key.longValue());
        result.put("folderId", folderId);
        result.put("name", originalName);
        result.put("contentType", contentType);
        result.put("size", file.getSize());
        result.put("uploaderUsername", profile.getUsername());
        result.put("extension", ext);
        result.put("previewable", isPreviewable(originalName, contentType));
        result.put("category", category(originalName, contentType));
        return CommonResult.successResponse(result, "文件上传成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> deleteFile(Long fileId) {
        Map<String, Object> record = getFileRecord(fileId);
        if (record == null) {
            return CommonResult.errorResponse("文件不存在！");
        }
        Path filePath = resolveStoredFile(record);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException exception) {
            return CommonResult.errorResponse("文件删除失败，请稍后重试！");
        }
        jdbcTemplate.update("DELETE FROM learning_resource_file WHERE id = ?", fileId);
        return CommonResult.successResponse("文件删除成功");
    }

    @Override
    public ResourceFile getResourceFile(Long fileId, boolean preview) {
        Map<String, Object> record = getFileRecord(fileId);
        if (record == null) {
            return null;
        }
        String originalName = String.valueOf(record.get("originalName"));
        String contentType = String.valueOf(record.get("contentType"));
        if (preview && !isPreviewable(originalName, contentType)) {
            return null;
        }
        Path filePath = resolveStoredFile(record);
        if (!Files.isRegularFile(filePath)) {
            return null;
        }
        return new ResourceFile(
                filePath.toFile(),
                originalName,
                preview ? previewContentType(originalName, contentType)
                        : "application/octet-stream");
    }

    private boolean folderExists(Long folderId) {
        if (folderId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_resource_folder WHERE id = ?",
                Integer.class,
                folderId);
        return count != null && count > 0;
    }

    private Map<String, Object> getFileRecord(Long fileId) {
        if (fileId == null) {
            return null;
        }
        List<Map<String, Object>> records = jdbcTemplate.query(
                "SELECT id, folder_id, original_name, stored_name, content_type " +
                        "FROM learning_resource_file WHERE id = ? LIMIT 1",
                (rs, rowNum) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", rs.getLong("id"));
                    result.put("folderId", rs.getLong("folder_id"));
                    result.put("originalName", rs.getString("original_name"));
                    result.put("storedName", rs.getString("stored_name"));
                    result.put("contentType", rs.getString("content_type"));
                    return result;
                },
                fileId);
        return records.isEmpty() ? null : records.get(0);
    }

    private Path resolveStoredFile(Map<String, Object> record) {
        Path root = resourceRoot();
        Path folderPath = root.resolve(String.valueOf(record.get("folderId"))).normalize();
        Path target = folderPath.resolve(String.valueOf(record.get("storedName"))).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid resource path");
        }
        return target;
    }

    private Path resourceRoot() {
        return Paths.get(Constants.File.LEARNING_RESOURCE_FOLDER.getPath())
                .toAbsolutePath()
                .normalize();
    }

    private AccountProfile currentProfile() {
        return (AccountProfile) SecurityUtils.getSubject().getPrincipal();
    }

    private int nullableInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long nullableLong(Long value) {
        return value == null ? 0L : value;
    }

    private String safeOriginalName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        name = name.replaceAll("[\\r\\n\\t]", "_").trim();
        if (name.length() > 255) {
            String ext = extension(name);
            int suffixLength = StringUtils.hasText(ext) ? ext.length() + 1 : 0;
            name = name.substring(0, Math.max(1, 255 - suffixLength))
                    + (suffixLength > 0 ? "." + ext : "");
        }
        return name;
    }

    private String extension(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String name, String suppliedType) {
        String ext = extension(name);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                return "image/jpeg";
            }
            if ("png".equals(ext)) {
                return "image/png";
            }
            if ("gif".equals(ext)) {
                return "image/gif";
            }
            if ("webp".equals(ext)) {
                return "image/webp";
            }
            if ("bmp".equals(ext)) {
                return "image/bmp";
            }
        }
        if ("pdf".equals(ext)) {
            return "application/pdf";
        }
        if ("docx".equals(ext)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ("xls".equals(ext)) {
            return "application/vnd.ms-excel";
        }
        if ("xlsx".equals(ext)) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if ("pptx".equals(ext)) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return "text/plain;charset=UTF-8";
        }
        return StringUtils.hasText(suppliedType)
                ? suppliedType
                : "application/octet-stream";
    }

    private boolean isPreviewable(String name, String contentType) {
        String ext = extension(name);
        return IMAGE_EXTENSIONS.contains(ext)
                || "pdf".equals(ext)
                || TEXT_EXTENSIONS.contains(ext)
                || OFFICE_PREVIEW_EXTENSIONS.contains(ext);
    }

    private String previewContentType(String name, String contentType) {
        String ext = extension(name);
        if (TEXT_EXTENSIONS.contains(ext)) {
            return "text/plain;charset=UTF-8";
        }
        return normalizedContentType(name, contentType);
    }

    private String category(String name, String contentType) {
        String ext = extension(name);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return "image";
        }
        if ("pdf".equals(ext)) {
            return "pdf";
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return "text";
        }
        if (Arrays.asList("doc", "docx").contains(ext)) {
            return "word";
        }
        if (Arrays.asList("xls", "xlsx").contains(ext)) {
            return "excel";
        }
        if (Arrays.asList("ppt", "pptx").contains(ext)) {
            return "powerpoint";
        }
        if (Arrays.asList("zip", "rar", "7z", "tar", "gz").contains(ext)) {
            return "archive";
        }
        return "file";
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best-effort cleanup after a failed database insert.
        }
    }
}
