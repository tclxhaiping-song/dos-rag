package com.example.docqa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档问答查询请求 DTO。
 *
 * @param question            （必填）用户提出的问题，不能为空，最大长度 4000 字符
 * @param topK                （可选）返回最相似的前 K 个文档片段，默认为 5
 * @param similarityThreshold （可选）相似度阈值（0~1），低于该值的片段将被过滤，不设置则返回所有匹配结果
 * @param generateAnswer      （可选）是否调用大语言模型生成回答，默认 false；
 *                            设为 true 时，会将检索到的片段作为上下文发送给 Chat 模型生成答案
 */
public record QaQueryRequest(
        @NotBlank @Size(max = 4000) String question,
        Integer topK,
        Double similarityThreshold,
        Boolean generateAnswer
) {
}
