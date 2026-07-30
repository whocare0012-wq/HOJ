package top.hcode.hoj.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.common.exception.StatusSystemErrorException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Safely stores and extracts uploaded ZIP files into a new temporary directory. */
@Component
@Slf4j(topic = "hoj")
public class SafeZipService {

    private static final int BUFFER_SIZE = 8192;

    @Value("${archive-max-entries:20000}")
    private int maxEntries;

    @Value("${archive-max-uncompressed-bytes:2147483648}")
    private long maxUncompressedBytes;

    public void transferAndUnzip(MultipartFile file, String destinationDir)
            throws StatusFailException, StatusSystemErrorException {
        validateUpload(file);

        Path destination = Paths.get(destinationDir).toAbsolutePath().normalize();
        Path archive = null;
        try {
            Path parent = destination.getParent();
            if (parent == null) {
                throw new IOException("ZIP destination has no parent directory");
            }
            Files.createDirectories(parent);
            Files.createDirectory(destination);
            archive = Files.createTempFile(parent, "hoj-upload-", ".zip");

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            extract(archive, destination);
        } catch (UnsafeArchiveException | ZipException exception) {
            deleteRecursively(destination);
            throw new StatusFailException("压缩包无效、包含不安全路径或超过资源限制！");
        } catch (IOException exception) {
            deleteRecursively(destination);
            log.error("Failed to store or extract an uploaded ZIP archive", exception);
            throw new StatusSystemErrorException("服务器异常：压缩包上传或解压失败！");
        } finally {
            deleteIfExists(archive);
        }
    }

    private void validateUpload(MultipartFile file) throws StatusFailException {
        if (file == null || file.isEmpty()) {
            throw new StatusFailException("上传的压缩包不能为空！");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new StatusFailException("请上传zip格式的压缩包！");
        }
    }

    private void extract(Path archive, Path destination) throws IOException {
        int allowedEntries = Math.max(1, maxEntries);
        long allowedBytes = Math.max(1L, maxUncompressedBytes);
        int entryCount = 0;
        long extractedBytes = 0L;

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > allowedEntries) {
                    throw new UnsafeArchiveException("ZIP contains too many entries");
                }

                long declaredSize = entry.getSize();
                if (declaredSize > allowedBytes - extractedBytes) {
                    throw new UnsafeArchiveException("ZIP expands beyond the configured limit");
                }

                Path target = resolveEntry(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Path targetParent = target.getParent();
                if (targetParent == null) {
                    throw new UnsafeArchiveException("ZIP entry has no parent directory");
                }
                Files.createDirectories(targetParent);
                try (InputStream input = zipFile.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(target,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                             StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > allowedBytes - extractedBytes) {
                            throw new UnsafeArchiveException("ZIP expands beyond the configured limit");
                        }
                        output.write(buffer, 0, read);
                        extractedBytes += read;
                    }
                }
            }
        }
    }

    private Path resolveEntry(Path destination, String entryName) throws UnsafeArchiveException {
        if (entryName == null || entryName.isEmpty() || entryName.indexOf('\0') >= 0) {
            throw new UnsafeArchiveException("ZIP entry has an invalid name");
        }
        try {
            Path target = destination.resolve(entryName.replace('\\', '/')).normalize();
            if (target.equals(destination) || !target.startsWith(destination)) {
                throw new UnsafeArchiveException("ZIP entry escapes the destination directory");
            }
            return target;
        } catch (RuntimeException exception) {
            throw new UnsafeArchiveException("ZIP entry has an invalid path");
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException cleanupException) {
            log.warn("Unable to completely remove a failed ZIP extraction directory");
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupException) {
            log.warn("Unable to remove a temporary uploaded ZIP file");
        }
    }

    private static class UnsafeArchiveException extends IOException {
        private UnsafeArchiveException(String message) {
            super(message);
        }
    }
}
