package top.hcode.hoj.service.oj;

import org.springframework.web.multipart.MultipartFile;
import top.hcode.hoj.common.result.CommonResult;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface LearningResourceService {

    CommonResult<List<Map<String, Object>>> getFolders();

    CommonResult<Map<String, Object>> createFolder(String name);

    CommonResult<Void> deleteFolder(Long folderId);

    CommonResult<Map<String, Object>> getFiles(Long folderId, Integer currentPage, Integer limit);

    CommonResult<Map<String, Object>> uploadFile(Long folderId, MultipartFile file);

    CommonResult<Void> deleteFile(Long fileId);

    ResourceFile getResourceFile(Long fileId, boolean preview);

    class ResourceFile {
        private final File file;
        private final String originalName;
        private final String contentType;

        public ResourceFile(File file, String originalName, String contentType) {
            this.file = file;
            this.originalName = originalName;
            this.contentType = contentType;
        }

        public File getFile() {
            return file;
        }

        public String getOriginalName() {
            return originalName;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
