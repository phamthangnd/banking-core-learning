package com.example.bankcore.file.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upload validation: every case here has an attack behind it. */
class FileValidationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    @Test
    void shouldAcceptAPngAvatar() {
        assertThatCode(() -> FileValidation.validate(FileCategory.AVATAR, "image/png", 1024, PNG))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectATypeThatIsNotOnTheCategoryAllowList() {
        // A PDF is fine as a KYC document and wrong as an avatar. The category decides.
        assertThatCode(() -> FileValidation.validate(FileCategory.KYC_DOCUMENT, "application/pdf", 1024, PDF))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> FileValidation.validate(FileCategory.AVATAR, "application/pdf", 1024, PDF))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void shouldRejectContentThatDoesNotMatchTheDeclaredType() {
        byte[] script = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);

        // The Content-Type header is written by the client, so it proves nothing on its own.
        assertThatThrownBy(() -> FileValidation.validate(FileCategory.AVATAR, "image/png", script.length, script))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void shouldRejectAFileOverTheCategoryLimit() {
        assertThatThrownBy(() -> FileValidation.validate(FileCategory.AVATAR, "image/png",
                FileCategory.AVATAR.maxSizeBytes() + 1, PNG))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void shouldRejectAnEmptyFile() {
        assertThatThrownBy(() -> FileValidation.validate(FileCategory.AVATAR, "image/png", 0, PNG))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void shouldRejectAMissingContentType() {
        assertThatThrownBy(() -> FileValidation.normalizeContentType(null))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void shouldNormalizeContentTypeParametersAndCase() {
        assertThat(FileValidation.normalizeContentType("image/PNG; charset=binary")).isEqualTo("image/png");
    }

    @Test
    void shouldAcceptTheOtherSupportedSignatures() {
        assertThat(FileValidation.matchesMagicBytes("image/jpeg", JPEG)).isTrue();
        assertThat(FileValidation.matchesMagicBytes("application/pdf", PDF)).isTrue();
        assertThat(FileValidation.matchesMagicBytes("image/png", JPEG)).isFalse();
    }

    @Test
    void shouldRejectTruncatedContent() {
        assertThat(FileValidation.matchesMagicBytes("image/png", new byte[]{1, 2})).isFalse();
        assertThat(FileValidation.matchesMagicBytes("image/png", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\windows\\system32\\config",
            "/absolute/path/file.png",
            "file with spaces.png"
    })
    void shouldStripPathsAndUnsafeCharactersFromTheName(String hostile) {
        String safe = FileValidation.sanitizeFileName(hostile);

        // The name is a label, never a location.
        assertThat(safe).doesNotContain("/").doesNotContain("\\").doesNotContain(" ");
        assertThat(safe).matches("[A-Za-z0-9._-]+");
    }

    @Test
    void shouldFallBackWhenTheNameIsUnusable() {
        assertThat(FileValidation.sanitizeFileName(null)).isEqualTo("upload");
        assertThat(FileValidation.sanitizeFileName("   ")).isEqualTo("upload");
        assertThat(FileValidation.sanitizeFileName("..")).isEqualTo("upload");
    }
}
