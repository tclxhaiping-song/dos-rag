package com.example.docqa.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 应用全局配置。
 * <p>
 * 注册自定义的 DashScope Embedding 模型（当 app.dashscope.embedding.enabled=true 时生效），
 * 替代 OpenAI 兼容模式的 Embedding，支持 qwen3-vl-embedding 等原生 API 模型。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(ChunkProperties.class)
public class AppConfig {

    /**
     * 注册 DashScope 原生 API 的 EmbeddingModel。
     * <p>
     * 通过 {@code @Primary} 确保 MilvusVectorStore 优先使用此 Bean（而非 OpenAI 自动配置的）。
     * 通过 {@code @ConditionalOnProperty} 支持开关切换：
     * <ul>
     *   <li>{@code app.dashscope.embedding.enabled=true} → 使用 DashScope 原生 API（支持 qwen3-vl-embedding）</li>
     *   <li>{@code app.dashscope.embedding.enabled=false} 或不配置 → 使用 OpenAI 兼容模式（text-embedding-v3）</li>
     * </ul>
     *
     * @param apiKey      百炼平台 API Key
     * @param apiUrl      DashScope Embedding API 端点
     * @param model       模型名称
     * @param dimension   向量维度
     * @param batchSize   每批最大文本数
     * @param inputFormat 输入格式：TEXTS（纯文本模型）或 CONTENTS（多模态模型）
     * @return 自定义的 EmbeddingModel 实例
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.dashscope.embedding.enabled", havingValue = "true")
    public EmbeddingModel dashScopeEmbeddingModel(
            @Value("${app.dashscope.embedding.api-key}") String apiKey,
            @Value("${app.dashscope.embedding.api-url}") String apiUrl,
            @Value("${app.dashscope.embedding.model}") String model,
            @Value("${app.dashscope.embedding.dimension:1024}") int dimension,
            @Value("${app.dashscope.embedding.batch-size:10}") int batchSize,
            @Value("${app.dashscope.embedding.input-format:TEXTS}") DashScopeEmbeddingModel.InputFormat inputFormat) {
        return new DashScopeEmbeddingModel(apiKey, apiUrl, model, dimension, batchSize, inputFormat);
    }
}
