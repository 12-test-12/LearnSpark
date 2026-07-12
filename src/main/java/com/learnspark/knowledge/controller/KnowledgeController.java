package com.learnspark.knowledge.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.result.PageResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.knowledge.dto.KnowledgeEntryResponse;
import com.learnspark.knowledge.dto.KnowledgeLinksResponse;
import com.learnspark.knowledge.dto.SearchResult;
import com.learnspark.knowledge.dto.UploadResponse;
import com.learnspark.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库控制器。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /knowledge/upload — 上传 .md/.txt 文件并解析入库</li>
 *   <li>GET    /knowledge — 列出当前用户的知识条目</li>
 *   <li>GET    /knowledge/search — 全文检索（支持高亮摘要、分页）</li>
 *   <li>GET    /knowledge/{id} — 查看条目详情</li>
 *   <li>DELETE /knowledge/{id} — 软删除条目</li>
 * </ul>
 */
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 上传文件到知识库（multipart）。
     * <p>完整路径：POST /api/v1/knowledge/upload
     */
    @PostMapping("/upload")
    public ApiResult<UploadResponse> upload(@CurrentUser String userId,
                                            @RequestParam("file") MultipartFile file) {
        return ApiResult.success(knowledgeService.upload(userId, file));
    }

    /**
     * 列出当前用户的知识条目。
     * <p>完整路径：GET /api/v1/knowledge
     */
    @GetMapping
    public ApiResult<List<KnowledgeEntryResponse>> list(@CurrentUser String userId) {
        return ApiResult.success(knowledgeService.listEntries(userId));
    }

    /**
     * 全文检索知识条目（MySQL FULLTEXT + ngram，支持中文）。
     * <p>完整路径：GET /api/v1/knowledge/search?q=关键字&page=0&size=10
     *
     * <p>注意：此端点必须在 {@code /{id}} 之前声明，否则 "search" 会被当作路径变量匹配。
     */
    @GetMapping("/search")
    public ApiResult<PageResult<SearchResult>> search(@CurrentUser String userId,
                                                       @RequestParam("q") String q,
                                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                                       @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResult.success(knowledgeService.search(userId, q, page, size));
    }

    /**
     * 查询知识条目的双向链接（出链 + 反链）。
     * <p>完整路径：GET /api/v1/knowledge/{id}/links
     *
     * <p>注意：此端点必须在 {@code /{id}} 之前声明，否则 "links" 路径段会被当作 {id}。
     */
    @GetMapping("/{id}/links")
    public ApiResult<KnowledgeLinksResponse> getLinks(@CurrentUser String userId,
                                                      @PathVariable String id) {
        return ApiResult.success(knowledgeService.getLinks(userId, id));
    }

    /**
     * 查看知识条目详情（含正文）。
     * <p>完整路径：GET /api/v1/knowledge/{id}
     */
    @GetMapping("/{id}")
    public ApiResult<KnowledgeEntryResponse> get(@CurrentUser String userId,
                                                 @PathVariable String id) {
        return ApiResult.success(knowledgeService.getEntry(userId, id));
    }

    /**
     * 软删除知识条目。
     * <p>完整路径：DELETE /api/v1/knowledge/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@CurrentUser String userId, @PathVariable String id) {
        knowledgeService.deleteEntry(userId, id);
        return ApiResult.success();
    }
}
