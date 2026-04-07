package com.example.docqa.web.dto;

import java.util.Map;

/**
 * 检索到的文档片段 DTO。
 *
 * @param id       片段在向量库中的唯一标识（UUID 格式）
 * @param text     片段的原始文本内容
 * @param metadata 片段的元数据，包含以下字段：
 *                 <ul>
 *                   <li><b>source</b> - 来源文件名（如 "产品手册.pdf"）</li>
 *                   <li><b>docId</b> - 所属文档的 UUID，同一次导入的所有片段共享此 ID</li>
 *                   <li><b>chunkIndex</b> - 当前片段在文档中的序号（从 0 开始）</li>
 *                   <li><b>totalChunks</b> - 该文档被切分的总片段数</li>
 *                 </ul>
 */
public record RetrievedChunk(
        String id,
        String text,
        Map<String, Object> metadata
) {
}
