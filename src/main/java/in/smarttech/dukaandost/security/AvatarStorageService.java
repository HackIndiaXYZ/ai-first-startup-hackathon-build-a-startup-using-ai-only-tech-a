package in.smarttech.dukaandost.security;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvatarStorageService {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int OUTPUT_SIZE = 512;
    private static final long MAX_PIXELS = 20_000_000L;
    private final Path directory;

    public AvatarStorageService(@Value("${app.profile.avatar-directory:./data/avatars}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        Path webRoot = Path.of("src/main/resources/static").toAbsolutePath().normalize();
        if (this.directory.startsWith(webRoot)) {
            throw new IllegalArgumentException("Avatar storage must be outside the static web root.");
        }
    }

    public String store(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_BYTES) {
            throw invalidImage();
        }
        try {
            byte[] bytes = upload.getBytes();
            if (bytes.length > MAX_BYTES) {
                throw invalidImage();
            }
            detectFormat(bytes);
            BufferedImage image = decode(bytes);
            String key = "avatar-" + UUID.randomUUID() + ".png";
            Files.createDirectories(directory);
            if (!ImageIO.write(squareCrop(image), "png", pathFor(key).toFile())) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Avatar image could not be saved.");
            }
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Avatar must be a valid JPG, PNG, or WebP image.");
        }
    }

    public byte[] read(String key) {
        try {
            Path path = pathFor(key);
            if (!Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found.");
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Profile picture could not be read.");
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(pathFor(key));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Profile picture could not be removed.");
        }
    }

    private Path pathFor(String key) {
        if (key == null || !key.matches("avatar-[0-9a-fA-F-]{36}\\.png")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found.");
        }
        Path resolved = directory.resolve(key).normalize();
        if (!resolved.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found.");
        }
        return resolved;
    }

    private static String detectFormat(byte[] bytes) {
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (png) return "png";
        if (jpeg) return "jpeg";
        if (webp) return "webp";
        throw invalidImage();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw invalidImage();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw invalidImage();
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw invalidImage();
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage squareCrop(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        BufferedImage square = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = square.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE,
                    x, y, x + side, y + side, null);
        } finally {
            graphics.dispose();
        }
        return square;
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose a valid JPG, PNG, or WebP image no larger than 2 MB.");
    }
}
