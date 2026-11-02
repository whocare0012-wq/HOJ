package top.hcode.hoj.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;

/**
 * 上传图片的等比缩小与重新编码（JPEG / PNG）。
 * <p>
 * 无法安全处理的情况一律返回 null，由调用方继续使用原图：
 * 尺寸已经足够小、格式不支持、像素过多（防止解压炸弹），
 * 以及带 EXIF 旋转标记的 JPEG（重新编码会丢失方向信息，导致图片横躺）。
 */
public final class ImageResizer {

    /** 允许解码的最大像素数，超过则不处理 */
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;

    private ImageResizer() {
    }

    /** 根据扩展名判断输出格式：png 保持 png（保留透明），jpg/jpeg 输出 jpg，其它返回 null */
    public static String outputFormat(String suffix) {
        if (suffix == null) {
            return null;
        }
        switch (suffix.toLowerCase(Locale.ROOT)) {
            case "png":
                return "png";
            case "jpg":
            case "jpeg":
                return "jpg";
            default:
                return null;
        }
    }

    /**
     * 将图片等比缩小到长边不超过 maxSide，返回编码后的字节；无需或无法缩小时返回 null。
     */
    public static byte[] shrink(File source, int maxSide, String format) throws IOException {
        if (format == null || !source.isFile()) {
            return null;
        }
        int[] size = dimensions(source);
        if (size == null || (long) size[0] * size[1] > MAX_PIXELS) {
            return null;
        }
        if (Math.max(size[0], size[1]) <= maxSide) {
            return null;
        }
        if ("jpg".equals(format) && exifOrientation(source) > 1) {
            return null;
        }
        BufferedImage image = ImageIO.read(source);
        if (image == null) {
            return null;
        }
        double scale = (double) maxSide / Math.max(image.getWidth(), image.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = resize(image, targetWidth, targetHeight, "png".equals(format));
        return encode(resized, format);
    }

    /**
     * 缩小后覆盖原文件（先写临时文件再替换）；返回是否发生了缩小。
     */
    public static boolean shrinkInPlace(File file, int maxSide, String format) throws IOException {
        byte[] bytes = shrink(file, maxSide, format);
        if (bytes == null) {
            return false;
        }
        File temp = new File(file.getParentFile(), file.getName() + ".resizing");
        Files.write(temp.toPath(), bytes);
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** 只读取头部获得宽高，不解码像素；无法识别时返回 null */
    static int[] dimensions(File file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } catch (IOException | RuntimeException e) {
                // 头部损坏或格式异常：按无法处理对待
                return null;
            } finally {
                reader.dispose();
            }
        }
    }

    /** 逐级减半缩小，避免一次大比例缩放产生明显锯齿 */
    private static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight, boolean keepAlpha) {
        int type = keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        do {
            width = Math.max(targetWidth, width / 2);
            height = Math.max(targetHeight, height / 2);
            BufferedImage next = new BufferedImage(width, height, type);
            Graphics2D g = next.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!keepAlpha) {
                    // 透明背景转 JPEG 时填白，避免变黑
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, width, height);
                }
                g.drawImage(current, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            current = next;
        } while (width != targetWidth || height != targetHeight);
        return current;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if ("png".equals(format)) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    /**
     * 读取 JPEG 的 EXIF Orientation（1~8），没有或无法解析时返回 1。
     */
    static int exifOrientation(File file) throws IOException {
        byte[] head = new byte[Math.min(131072, (int) Math.max(0, file.length()))];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int read = 0;
            while (read < head.length) {
                int n = input.read(head, read, head.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
        }
        return exifOrientation(head);
    }

    static int exifOrientation(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 <= data.length) {
            if ((data[offset] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = data[offset + 1] & 0xFF;
            int length = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            if (marker == 0xDA || length < 2) {
                return 1;
            }
            if (marker == 0xE1 && offset + 10 <= data.length
                    && data[offset + 4] == 'E' && data[offset + 5] == 'x' && data[offset + 6] == 'i'
                    && data[offset + 7] == 'f') {
                return parseTiffOrientation(data, offset + 10, Math.min(data.length, offset + 2 + length));
            }
            offset += 2 + length;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] data, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
        int ifd = tiff + readInt(data, tiff + 4, little);
        if (ifd < tiff || ifd + 2 > end) {
            return 1;
        }
        int entries = readShort(data, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (readShort(data, entry, little) == 0x0112) {
                int value = readShort(data, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int readShort(byte[] data, int offset, boolean little) {
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static int readInt(byte[] data, int offset, boolean little) {
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        int c = data[offset + 2] & 0xFF;
        int d = data[offset + 3] & 0xFF;
        return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
    }
}
