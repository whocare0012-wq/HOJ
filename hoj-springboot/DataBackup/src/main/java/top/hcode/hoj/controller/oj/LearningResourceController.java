package top.hcode.hoj.controller.oj;

import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.service.oj.LearningResourceService;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-resource")
@RequiresAuthentication
public class LearningResourceController {

    @Resource
    private LearningResourceService learningResourceService;

    @GetMapping("/folders")
    @RequiresAuthentication
    public CommonResult<List<Map<String, Object>>> getFolders() {
        return learningResourceService.getFolders();
    }

    @PostMapping("/folders")
    @RequiresAuthentication
    @RequiresRoles(
            value = {"root", "admin", "problem_admin"},
            logical = Logical.OR)
    public CommonResult<Map<String, Object>> createFolder(
            @RequestBody Map<String, String> body) {
        return learningResourceService.createFolder(body.get("name"));
    }

    @DeleteMapping("/folders/{folderId}")
    @RequiresAuthentication
    @RequiresRoles(
            value = {"root", "admin", "problem_admin"},
            logical = Logical.OR)
    public CommonResult<Void> deleteFolder(@PathVariable Long folderId) {
        return learningResourceService.deleteFolder(folderId);
    }

    @GetMapping("/folders/{folderId}/files")
    @RequiresAuthentication
    public CommonResult<Map<String, Object>> getFiles(
            @PathVariable Long folderId,
            @RequestParam(value = "currentPage", required = false) Integer currentPage,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return learningResourceService.getFiles(folderId, currentPage, limit);
    }

    @PostMapping(
            value = "/folders/{folderId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAuthentication
    @RequiresRoles(
            value = {"root", "admin", "problem_admin"},
            logical = Logical.OR)
    public CommonResult<Map<String, Object>> uploadFile(
            @PathVariable Long folderId,
            @RequestPart("file") MultipartFile file) {
        return learningResourceService.uploadFile(folderId, file);
    }

    @DeleteMapping("/files/{fileId}")
    @RequiresAuthentication
    @RequiresRoles(
            value = {"root", "admin", "problem_admin"},
            logical = Logical.OR)
    public CommonResult<Void> deleteFile(@PathVariable Long fileId) {
        return learningResourceService.deleteFile(fileId);
    }

    @GetMapping("/files/{fileId}/preview")
    @RequiresAuthentication
    public ResponseEntity<FileSystemResource> previewFile(@PathVariable Long fileId) {
        return resourceResponse(fileId, true);
    }

    @GetMapping("/files/{fileId}/download")
    @RequiresAuthentication
    public ResponseEntity<FileSystemResource> downloadFile(@PathVariable Long fileId) {
        return resourceResponse(fileId, false);
    }

    private ResponseEntity<FileSystemResource> resourceResponse(
            Long fileId,
            boolean preview) {
        LearningResourceService.ResourceFile resourceFile =
                learningResourceService.getResourceFile(fileId, preview);
        if (resourceFile == null) {
            return ResponseEntity.status(
                    preview ? HttpStatus.UNSUPPORTED_MEDIA_TYPE : HttpStatus.NOT_FOUND)
                    .build();
        }
        ContentDisposition disposition = (preview
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(resourceFile.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(MediaType.parseMediaType(resourceFile.getContentType()));
        headers.setContentLength(resourceFile.getFile().length());
        headers.setCacheControl("private, no-store, max-age=0");
        return ResponseEntity.ok()
                .headers(headers)
                .body(new FileSystemResource(resourceFile.getFile()));
    }
}
