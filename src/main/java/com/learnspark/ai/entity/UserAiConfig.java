package com.learnspark.ai.entity;

import com.learnspark.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户 AI 配置实体，对应 user_ai_config 表。
 *
 * <p>1:1 关联 users 表，主键即 user_id。
 * API Key 以 AES-256-GCM 密文存储在 {@code *_encrypted} 字段。
 */
@Entity
@Table(name = "user_ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAiConfig extends BaseEntity {

    /** 用户 ID（主键 + 外键 → users.id） */
    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    private String userId;

    /** DeepSeek API Key（AES 加密密文） */
    @Column(name = "deepseek_api_key_encrypted", columnDefinition = "TEXT")
    private String deepseekApiKeyEncrypted;

    /** DeepSeek API 基础 URL */
    @Column(name = "deepseek_base_url", length = 255)
    private String deepseekBaseUrl;

    /** DeepSeek 模型名称 */
    @Column(name = "deepseek_model", length = 100)
    private String deepseekModel;

    /** 搜索 API Key（AES 加密密文） */
    @Column(name = "search_api_key_encrypted", columnDefinition = "TEXT")
    private String searchApiKeyEncrypted;

    /** 搜索引擎提供商 */
    @Column(name = "search_provider", length = 50)
    private String searchProvider;

    /** 本地模式：true 时不存储 Key，仅在前端使用 */
    @Column(name = "local_mode")
    private Boolean localMode;

    /** 向量模型名称 */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /** 持久化前填充默认值 */
    @PrePersist
    void fillDefaults() {
        if (this.deepseekBaseUrl == null) {
            this.deepseekBaseUrl = "https://api.deepseek.com/v1";
        }
        if (this.deepseekModel == null) {
            this.deepseekModel = "deepseek-chat";
        }
        if (this.searchProvider == null) {
            this.searchProvider = "bing";
        }
        if (this.localMode == null) {
            this.localMode = false;
        }
        if (this.embeddingModel == null) {
            this.embeddingModel = "bge-large-zh";
        }
    }
}
