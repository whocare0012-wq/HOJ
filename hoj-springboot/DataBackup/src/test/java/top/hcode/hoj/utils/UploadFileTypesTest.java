package top.hcode.hoj.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadFileTypesTest {

    @Test
    void extractsLowercaseSuffixFromLastSegment() {
        assertEquals("png", UploadFileTypes.getSuffix("Photo.PNG"));
        assertEquals("gz", UploadFileTypes.getSuffix("data.tar.gz"));
        assertEquals("jpg", UploadFileTypes.getSuffix("/upload/2024/a.b/c.jpg"));
        assertEquals("", UploadFileTypes.getSuffix("README"));
        assertEquals("", UploadFileTypes.getSuffix("trailing."));
        assertEquals("", UploadFileTypes.getSuffix("dir.png/"));
        assertEquals("", UploadFileTypes.getSuffix(null));
        // 含路径分隔符、空格等异常字符的扩展名不保留
        assertEquals("", UploadFileTypes.getSuffix("img./../../x"));
        assertEquals("", UploadFileTypes.getSuffix("a.png,webp"));
    }

    @Test
    void imageCheckRequiresExactSuffix() {
        assertTrue(UploadFileTypes.isAllowed("jpeg", UploadFileTypes.IMAGE_SUFFIXES));
        // 旧实现用 "jpg,jpeg,gif,png,webp".contains(suffix)，下面这些都会被误放行
        assertFalse(UploadFileTypes.isAllowed("", UploadFileTypes.IMAGE_SUFFIXES));
        assertFalse(UploadFileTypes.isAllowed("pg", UploadFileTypes.IMAGE_SUFFIXES));
        assertFalse(UploadFileTypes.isAllowed("g", UploadFileTypes.IMAGE_SUFFIXES));
        assertFalse(UploadFileTypes.isAllowed("svg", UploadFileTypes.IMAGE_SUFFIXES));
    }

    @Test
    void classifiesActiveAndInlineSafeContent() {
        assertTrue(UploadFileTypes.isActiveContent("html"));
        assertTrue(UploadFileTypes.isActiveContent("svg"));
        assertTrue(UploadFileTypes.isActiveContent("js"));
        assertFalse(UploadFileTypes.isActiveContent("pdf"));
        assertFalse(UploadFileTypes.isActiveContent("zip"));

        assertTrue(UploadFileTypes.isInlineSafe("png"));
        assertTrue(UploadFileTypes.isInlineSafe("pdf"));
        assertFalse(UploadFileTypes.isInlineSafe("svg"));
        assertFalse(UploadFileTypes.isInlineSafe("html"));
        assertFalse(UploadFileTypes.isInlineSafe(""));
    }
}
