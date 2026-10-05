package br.com.nuvemcustomfields.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

@Component
public class OptionImageProcessor {
    public record Images(byte[] preview, byte[] thumbnail) {}
    public Images process(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw invalid();
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("PNG"))) throw invalid();
                reader.setInput(stream);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 20000000) throw invalid();
                var image = reader.read(0);
                return new Images(resize(image, 1200), resize(image, 240));
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) { throw invalid(); }
    }
    private byte[] resize(BufferedImage source, int size) throws IOException {
        double ratio = Math.min(1, (double) size / Math.max(source.getWidth(), source.getHeight()));
        var image = new BufferedImage(Math.max(1, (int) (source.getWidth() * ratio)),
                Math.max(1, (int) (source.getHeight() * ratio)), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
        } finally { graphics.dispose(); }
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("image.file.invalid"); }
}
