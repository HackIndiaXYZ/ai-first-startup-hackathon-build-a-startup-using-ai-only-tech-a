package in.smarttech.dukaandost.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AvatarStorageServiceTest {

    @TempDir
    Path directory;

    private AvatarStorageService avatars;

    @BeforeEach
    void setup() {
        avatars = new AvatarStorageService(directory.toString());
    }

    @Test
    void validatesImageBytesAndStoresCenteredSquarePng() throws Exception {
        BufferedImage source = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        source.setRGB(160, 90, Color.GREEN.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        MockMultipartFile upload = new MockMultipartFile(
                "avatar", "misleading.webp", "image/webp", output.toByteArray());

        String key = avatars.store(upload);

        BufferedImage stored = ImageIO.read(directory.resolve(key).toFile());
        assertThat(key).matches("avatar-[0-9a-fA-F-]{36}\\.png");
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);
        assertThat(avatars.read(key)).isNotEmpty();
    }

    @Test
    void rejectsFilesWhoseRealContentIsNotAnImage() {
        MockMultipartFile upload = new MockMultipartFile(
                "avatar", "photo.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> avatars.store(upload))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void rejectsFilesOverTwoMegabytes() {
        MockMultipartFile upload = new MockMultipartFile(
                "avatar", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> avatars.store(upload))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }
}
