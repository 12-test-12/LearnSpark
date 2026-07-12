package com.learnspark.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 任务提交请求体。
 */
@Data
public class SubmissionRequest {

    @NotBlank(message = "提交内容不能为空")
    @Size(max = 10000, message = "提交内容最长 10000 字符")
    private String content;

    /** 附件 URL 列表（可选） */
    private List<String> attachmentUrls;
}
