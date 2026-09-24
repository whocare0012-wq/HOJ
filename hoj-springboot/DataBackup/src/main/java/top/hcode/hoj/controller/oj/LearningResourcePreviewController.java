package top.hcode.hoj.controller.oj;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.annotation.AnonApi;
import top.hcode.hoj.service.oj.LearningResourceService;
import top.hcode.hoj.utils.RedisUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

@AnonApi
@RestController
@RequestMapping("/api/learning-resource-preview")
public class LearningResourcePreviewController {

    private static final String PREVIEW_TICKET_PREFIX = "learning_resource:preview_ticket:";

    @Resource
    private LearningResourceService learningResourceService;

    @Resource
    private RedisUtils redisUtils;

    @GetMapping("/files/{fileId}")
    public ResponseEntity<FileSystemResource> previewPdf(
            @PathVariable Long fileId,
            @RequestParam("ticket") String ticket) {
        if (ticket == null || !ticket.matches("[0-9a-f]{32}")) {
            return ResponseEntity.notFound().build();
        }

        Object ticketFileId = redisUtils.get(PREVIEW_TICKET_PREFIX + ticket);
        if (ticketFileId == null
                || !String.valueOf(fileId).equals(String.valueOf(ticketFileId))) {
            return ResponseEntity.notFound().build();
        }

        LearningResourceService.ResourceFile resourceFile =
                learningResourceService.getResourceFile(fileId, true);
        if (resourceFile == null
                || !MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(
                        resourceFile.getContentType())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(resourceFile.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(resourceFile.getFile().length());
        headers.setCacheControl("private, no-store, max-age=0");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        return ResponseEntity.ok()
                .headers(headers)
                .body(new FileSystemResource(resourceFile.getFile()));
    }
}
