package top.hcode.hoj.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.common.exception.StatusFailException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeZipServiceTest {

    @TempDir
    Path temporaryDirectory;

    private SafeZipService service;

    @BeforeEach
    void setUp() {
        service = new SafeZipService();
        ReflectionTestUtils.setField(service, "maxEntries", 10);
        ReflectionTestUtils.setField(service, "maxUncompressedBytes", 1024L);
    }

    @Test
    void extractsEntriesInsideDestination() throws Exception {
        byte[] content = "answer".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("nested/case.out", content);
        Path destination = temporaryDirectory.resolve("valid");

        service.transferAndUnzip(upload(entries), destination.toString());

        assertArrayEquals(content, Files.readAllBytes(destination.resolve("nested/case.out")));
    }

    @Test
    void rejectsPathTraversalAndCleansPartialExtraction() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("valid.txt", new byte[]{1});
        entries.put("../escaped.txt", new byte[]{2});
        Path destination = temporaryDirectory.resolve("traversal");

        assertThrows(StatusFailException.class,
                () -> service.transferAndUnzip(upload(entries), destination.toString()));

        assertFalse(Files.exists(destination));
        assertFalse(Files.exists(temporaryDirectory.resolve("escaped.txt")));
    }

    @Test
    void rejectsArchivesThatExpandBeyondConfiguredLimit() throws Exception {
        ReflectionTestUtils.setField(service, "maxUncompressedBytes", 4L);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("large.txt", new byte[]{1, 2, 3, 4, 5});

        assertThrows(StatusFailException.class, () -> service.transferAndUnzip(
                upload(entries), temporaryDirectory.resolve("oversized").toString()));
    }

    @Test
    void rejectsArchivesWithTooManyEntries() throws Exception {
        ReflectionTestUtils.setField(service, "maxEntries", 1);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.txt", new byte[]{1});
        entries.put("two.txt", new byte[]{2});

        assertThrows(StatusFailException.class, () -> service.transferAndUnzip(
                upload(entries), temporaryDirectory.resolve("too-many-entries").toString()));
    }

    private MockMultipartFile upload(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "archive.zip", "application/zip", bytes.toByteArray());
    }
}
