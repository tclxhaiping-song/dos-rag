package com.example.docqa.web.dto;

import java.util.List;

/**
 * 文档问答查询响应 DTO。
 *
 * @param answer AI 生成的自然语言回答；仅当请求中 generateAnswer=true 且检索到片段时有值，否则为 null
 * @param chunks 检索到的相关文档片段列表，按相似度从高到低排序
 */
public record QaQueryResponse(
        String answer,
        List<RetrievedChunk> chunks
) {
}
