package top.hcode.hoj.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageResizerTest {

    @TempDir
    Path dir;

    private File image(String name, int width, int height, boolean alpha, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(alpha ? new Color(255, 0, 0, 120) : Color.BLUE);
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        File file = dir.resolve(name).toFile();
        ImageIO.write(image, format, file);
        return file;
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void shrinksPngKeepingAspectRatioAndAlpha() throws IOException {
        byte[] bytes = ImageResizer.shrink(image("a.png", 1000, 600, true, "png"), 256, "png");
        assertNotNull(bytes);
        BufferedImage result = decode(bytes);
        assertEquals(256, result.getWidth());
        assertEquals(154, result.getHeight());
        assertTrue(result.getColorModel().hasAlpha());
    }

    @Test
    void shrinksJpegAndMakesFileSmaller() throws IOException {
        File source = image("b.jpg", 1600, 1600, false, "jpg");
        byte[] bytes = ImageResizer.shrink(source, 128, "jpg");
        assertNotNull(bytes);
        assertEquals(128, decode(bytes).getWidth());
        assertTrue(bytes.length < source.length());
    }

    @Test
    void leavesSmallOrUnsupportedImagesAlone() throws IOException {
        assertNull(ImageResizer.shrink(image("c.png", 100, 80, false, "png"), 256, "png"));
        assertNull(ImageResizer.shrink(image("d.png", 1000, 1000, false, "png"), 256, null));
        File text = dir.resolve("e.png").toFile();
        Files.write(text.toPath(), "not an image".getBytes());
        assertNull(ImageResizer.shrink(text, 256, "png"));
        assertEquals("jpg", ImageResizer.outputFormat("JPEG"));
        assertNull(ImageResizer.outputFormat("gif"));
        assertNull(ImageResizer.outputFormat("webp"));
    }

    @Test
    void shrinkInPlaceReplacesFile() throws IOException {
        File file = image("f.png", 2000, 1000, false, "png");
        assertTrue(ImageResizer.shrinkInPlace(file, 512, "png"));
        assertEquals(512, ImageIO.read(file).getWidth());
        assertFalse(ImageResizer.shrinkInPlace(file, 512, "png"));
        assertFalse(new File(file.getPath() + ".resizing").exists());
    }

    @Test
    void readsExifOrientationAndSkipsRotatedJpegs() throws IOException {
        // 最小的 JPEG 头 + APP1 Exif（大端），IFD0 中只有 Orientation=6
        byte[] rotated = {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDA
        };
        assertEquals(6, ImageResizer.exifOrientation(rotated));
        assertEquals(1, ImageResizer.exifOrientation(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA}));
        assertEquals(1, ImageResizer.exifOrientation("not a jpeg".getBytes()));

        // 真实的大 JPEG 前面拼上带旋转标记的 APP1 段后，不应被重新编码
        File plain = image("g.jpg", 1200, 800, false, "jpg");
        byte[] original = Files.readAllBytes(plain.toPath());
        byte[] app1 = new byte[rotated.length - 4];
        System.arraycopy(rotated, 2, app1, 0, app1.length);
        byte[] withExif = new byte[original.length + app1.length];
        withExif[0] = original[0];
        withExif[1] = original[1];
        System.arraycopy(app1, 0, withExif, 2, app1.length);
        System.arraycopy(original, 2, withExif, 2 + app1.length, original.length - 2);
        File rotatedFile = dir.resolve("h.jpg").toFile();
        Files.write(rotatedFile.toPath(), withExif);
        assertEquals(6, ImageResizer.exifOrientation(rotatedFile));
        assertNull(ImageResizer.shrink(rotatedFile, 256, "jpg"));
        assertNotNull(ImageResizer.shrink(plain, 256, "jpg"));
    }

    @Test
    void refusesDecompressionBombs() throws IOException {
        // 只写入 PNG 头部声明的超大尺寸即可，读取尺寸时不会解码像素
        File huge = image("i.png", 10, 10, false, "png");
        byte[] bytes = Files.readAllBytes(huge.toPath());
        // IHDR 宽高位于偏移 16~23
        writeInt(bytes, 16, 100_000);
        writeInt(bytes, 20, 100_000);
        Files.write(huge.toPath(), bytes);
        assertNull(ImageResizer.shrink(huge, 256, "png"));
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
