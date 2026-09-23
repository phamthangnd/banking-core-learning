package com.example.bankcore.file.web;

import com.example.bankcore.file.domain.FileCategory;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.support.TestUsers;
import com.example.bankcore.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload and download against a real MinIO.
 *
 * <p>Mocking the object store would test the code's opinion of S3 rather than S3. The container
 * costs a few seconds and proves that the bucket, the keys and the byte round trip actually work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class FileUploadIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    // quay.io rather than Docker Hub: the MinIO image is not pullable from Hub without
    // authentication, and quay.io is the project's own registry.
    static final MinIOContainer MINIO = new MinIOContainer(
            org.testcontainers.utility.DockerImageName.parse("quay.io/minio/minio:latest")
                    .asCompatibleSubstituteFor("minio/minio"));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void objectStorage(DynamicPropertyRegistry registry) {
        registry.add("bankcore.file.endpoint", MINIO::getS3URL);
        registry.add("bankcore.file.access-key", MINIO::getUserName);
        registry.add("bankcore.file.secret-key", MINIO::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private User uploader;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();
        uploader = testUsers.create("file-uploader", "OFFICER");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUploader() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .jwt().jwt(jwt -> jwt.subject(uploader.id().toString()))
                .authorities(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("file:write"),
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("file:read"),
                        new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("audit:read"));
    }

    @Test
    void shouldStoreAndReturnTheSameBytes() throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", PNG))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalName").value("avatar.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(PNG.length))
                .andExpect(jsonPath("$.data.checksum").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).path("data").path("id").asText();

        byte[] downloaded = mockMvc.perform(get("/api/v1/files/{id}/content", id).with(asUploader()))
                .andExpect(status().isOk())
                // Never inline: user content served inline can run script in this origin.
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(PNG);
    }

    @Test
    void shouldRejectAFileWhoseContentBeliesItsType() throws Exception {
        byte[] script = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", script))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
    }

    @Test
    void shouldRejectATypeThatTheCategoryDoesNotAllow() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf",
                                new byte[]{'%', 'P', 'D', 'F'}))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE"));
    }

    @Test
    void shouldNotUseTheUploadedNameAsAPath() throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "../../etc/passwd.png", "image/png", PNG))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("originalName").asText())
                .doesNotContain("/").doesNotContain("..");
    }

    @Test
    void shouldSoftDeleteTheMetadataAndRemoveTheObject() throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", PNG))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/files/{id}", id).with(asUploader()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files/{id}", id).with(asUploader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void shouldRequireTheWritePermissionToUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", PNG))
                        .param("category", "AVATAR")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(uploader.id().toString()))
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("file:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAuditUploadsAndRejections() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", PNG))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "bad.png", "image/png",
                                "not a png".getBytes(StandardCharsets.UTF_8)))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/audit/events").param("resourceType", "FILE").with(asUploader()))
                .andExpect(status().isOk())
                // Both the success and the rejection are in the trail, with actor and trace id.
                .andExpect(jsonPath("$.metadata.totalElements").value(2))
                .andExpect(jsonPath("$.data[*].outcome",
                        org.hamcrest.Matchers.hasItems("SUCCESS", "FAILURE")))
                .andExpect(jsonPath("$.data[0].traceId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].actorName").value(uploader.id().toString()));
    }

    @Test
    void auditDetailShouldNeverCarryFileContent() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "secret.png", "image/png",
                                "TOP-SECRET-CONTENT".getBytes(StandardCharsets.UTF_8)))
                        .param("category", "AVATAR")
                        .with(asUploader()))
                .andExpect(status().isBadRequest());

        String trail = mockMvc.perform(get("/api/v1/audit/events").with(asUploader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(trail).doesNotContain("TOP-SECRET-CONTENT");
    }

    @Test
    void auditTrailShouldRequireItsOwnPermission() throws Exception {
        // Reading the trail is itself privileged: a user who may upload files has no business
        // seeing who else uploaded what.
        mockMvc.perform(get("/api/v1/audit/events")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("file:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
}
