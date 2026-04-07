package com.example.docqa.web.dto;

import java.util.Map;

/**
 * 检索到的文档片段 DTO。
 *
 * @param id       片段在向量库中的唯一标识（UUID 格式）
 * @param text     片段文本内容：
 *                 <ul>
 *                   <li>传统模式（CHAR / SENTENCE）- 直接展示匹配到的片段文本</li>
 *                   <li>父子索引模式（PARENT_CHILD）- 展示父块文本（~1000字完整上下文）</li>
 *                 </ul>
 * @param metadata 片段的元数据，根据切分模式包含不同字段：
 *                 <p><b>通用字段：</b></p>
 *                 <ul>
 *                   <li><b>source</b> - 来源文件名（如 "产品手册.pdf"）</li>
 *                   <li><b>docId</b> - 所属文档的 UUID，同一次导入的所有片段共享此 ID</li>
 *                 </ul>
 *                 <p><b>传统模式额外字段：</b></p>
 *                 <ul>
 *                   <li><b>chunkIndex</b> - 当前片段在文档中的序号（从 0 开始）</li>
 *                   <li><b>totalChunks</b> - 该文档被切分的总片段数</li>
 *                 </ul>
 *                 <p><b>父子索引模式额外字段：</b></p>
 *                 <ul>
 *                   <li><b>childText</b> - 实际命中的子块文本（~100字，用于精准检索的内容）</li>
 *                   <li><b>parentId</b> - 父块 UUID，多个子块共享同一父块时此 ID 相同</li>
 *                   <li><b>parentIndex</b> - 父块在文档中的序号</li>
 *                   <li><b>childIndex</b> - 子块在父块中的序号</li>
 *                   <li><b>globalChildIndex</b> - 子块在文档中的全局序号</li>
 *                 </ul>
 */
public record RetrievedChunk(
        String id,
        String text,
        Map<String, Object> metadata
) {
}
