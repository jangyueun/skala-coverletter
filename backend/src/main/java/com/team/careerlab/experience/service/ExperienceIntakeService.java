package com.team.careerlab.experience.service;

import com.team.careerlab.aitask.service.AiTaskService;
import com.team.careerlab.experience.exception.ExperienceException;
import com.team.careerlab.integration.storage.client.SupabaseStorageClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/**
 * 경험 인테이크 등록. 링크·첨부파일을 받아 EXPERIENCE_INTAKE 작업을 만든다.
 *
 * <p>파일은 DB 에 두지 않는다 — Supabase Storage 에 먼저 올리고 URL 만 작업 입력(request_payload)에
 * 남긴다(v6 회의 결정). Storage 경로가 {@code intake/{userId}/{taskId}/} 라 taskId 가 먼저 있어야 해서,
 * links 만으로 PENDING 행을 만든 뒤 업로드가 끝나면 fileUrls 를 채운 스냅샷으로 덮어쓴다.
 *
 * <p><b>이번 범위는 작업 생성까지다.</b> AI 서버로 실제 전송해 후보를 받아오는 MockAiClient·워커는
 * 별도 범위(AI 작업 섹션의 폴링 API)에서 붙인다.
 */
@Service
public class ExperienceIntakeService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "md", "txt");
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILES = 5;

    private final AiTaskService aiTasks;
    private final SupabaseStorageClient storage;
    private final ObjectMapper objectMapper;

    public ExperienceIntakeService(AiTaskService aiTasks, SupabaseStorageClient storage, ObjectMapper objectMapper) {
        this.aiTasks = aiTasks;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * @throws ExperienceException 링크·파일이 모두 없거나, 5개를 넘거나, 허용되지 않는 확장자면
     *         {@code VALIDATION_FAILED}. 파일당 10MB 를 넘으면 {@code FILE_TOO_LARGE}
     * @throws com.team.careerlab.aitask.exception.AiTaskException 다른 입력의 인테이크가 이미
     *         진행 중이면 {@code INTAKE_ALREADY_RUNNING}
     */
    public Result intake(Long userId, String linksText, List<MultipartFile> files) {
        List<String> links = parseLinks(linksText);
        List<MultipartFile> attachments = files.stream().filter(file -> !file.isEmpty()).toList();
        validate(links, attachments);

        List<FileContent> contents = readAll(attachments);
        String inputHash = computeInputHash(links, contents);

        AiTaskService.Reservation reservation = aiTasks.reserveIntakeTask(userId, inputHash,
                payload(links, List.of()));
        if (!reservation.created()) {
            // 같은 입력이 이미 진행 중 — 파일을 다시 올리지 않고 기존 작업을 그대로 돌려준다.
            return new Result(reservation.taskId(), false);
        }

        List<String> fileUrls = upload(userId, reservation.taskId(), contents);
        aiTasks.attachIntakePayload(reservation.taskId(), payload(links, fileUrls));

        return new Result(reservation.taskId(), true);
    }

    private void validate(List<String> links, List<MultipartFile> attachments) {
        if (links.isEmpty() && attachments.isEmpty()) {
            throw ExperienceException.validationFailed("링크나 첨부파일을 1개 이상 입력해 주세요.");
        }
        if (attachments.size() > MAX_FILES) {
            throw ExperienceException.validationFailed("첨부파일은 최대 5개까지 올릴 수 있습니다.");
        }
        for (MultipartFile file : attachments) {
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw ExperienceException.fileTooLarge();
            }
            if (!hasAllowedExtension(file.getOriginalFilename())) {
                throw ExperienceException.validationFailed("PDF·MD·TXT 파일만 올릴 수 있습니다.");
            }
        }
    }

    private List<FileContent> readAll(List<MultipartFile> files) {
        List<FileContent> contents = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                contents.add(new FileContent(file.getOriginalFilename(), file.getBytes(), file.getContentType()));
            } catch (IOException e) {
                throw new UncheckedIOException("첨부파일을 읽는 중 오류가 발생했습니다.", e);
            }
        }
        return contents;
    }

    private List<String> upload(Long userId, Long taskId, List<FileContent> contents) {
        List<String> urls = new ArrayList<>();
        for (FileContent content : contents) {
            String path = "intake/%d/%d/%s".formatted(userId, taskId, content.filename());
            urls.add(storage.upload(path, content.bytes(), content.contentType()));
        }
        return urls;
    }

    private List<String> parseLinks(String linksText) {
        if (linksText == null || linksText.isBlank()) {
            return List.of();
        }
        return linksText.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private boolean hasAllowedExtension(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /** Jackson 3의 {@code writeValueAsString} 은 unchecked {@code JacksonException} 을 던진다. */
    private String payload(List<String> links, List<String> fileUrls) {
        return objectMapper.writeValueAsString(new IntakePayload(links, fileUrls));
    }

    private String computeInputHash(List<String> links, List<FileContent> contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String link : links) {
                digest.update(link.getBytes(StandardCharsets.UTF_8));
            }
            for (FileContent content : contents) {
                digest.update(content.filename().getBytes(StandardCharsets.UTF_8));
                digest.update(content.bytes());
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 지원하지 않는 JVM 입니다.", e);
        }
    }

    private record FileContent(String filename, byte[] bytes, String contentType) {
    }

    private record IntakePayload(List<String> links, List<String> fileUrls) {
    }

    public record Result(Long taskId, boolean created) {
    }
}
