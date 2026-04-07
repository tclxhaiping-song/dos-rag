# 本地文档问答 API（Spring Boot 3 + Tika + Milvus + 百炼千问）

## 功能概览

- 上传 PDF / TXT 等文件：Apache Tika 抽取正文，按配置做**字符窗口分块**或**句子合并分块**，写入 **Milvus** 向量库。
- 问答：`/api/qa/query` 量检索 Top-K 片段；可选调用 **百炼千问** 模型生成答案。

## 环境要求

- JDK **17+**
- **Docker**（用于 Milvus）
- **百炼千问**（嵌入与可选生成）：请确保已配置相关服务地址与模型参数。

## 启动 Milvus

在 `doc-qa-api` 目录：

```bash
# 启动 Milvus 服务
docker compose up -d
```

默认 gRPC：`localhost:19530`（用户名/密码 `root` / `milvus`）。

## IntelliJ IDEA 运行

1. **File → Open**，选择 `doc-qa-api` 目录（含 `pom.xml`）。
2. 等待 Maven 导入完成。
3. 运行主类：`com.example.docqa.DocQaApplication`。

## 配置说明

编辑 `src/main/resources/application.yml`：

- `spring.ai.vectorstore.milvus`：Milvus 地址、集合名、`embedding-dimension`（须与嵌入模型一致）。
- `spring.ai.bailian`：百炼千问服务地址与模型名。
- `app.chunk`：`strategy` 为 `CHAR` 或 `SENTENCE`，以及分块参数。

## API 示例

### 上传文档

**接口描述**：上传文档（支持 PDF / TXT），解析并存储到向量数据库。

**请求方式**：`POST`

**URL**：`http://localhost:8080/api/documents`

**请求头**：
- `Content-Type: multipart/form-data`

**请求体**：
- 字段名：`file`
- 类型：文件

### 检索 / 问答

**接口描述**：根据问题检索文档片段，可选生成答案。

**请求方式**：`POST`

**URL**：`http://localhost:8080/api/qa/query`

**请求头**：
- `Content-Type: application/json`

**请求体**：
```json
{
  "question": "文档里主要讲了什么？",
  "topK": 5,
  "generateAnswer": true
}
```

**参数说明**：
- `question`：用户输入的问题。
- `topK`：返回的片段数量。
- `generateAnswer`：是否调用百炼千问模型生成答案。
- `similarityThreshold`（可选）：相似度阈值，范围 0~1，不传则不做相似度截断。

**响应示例**：
```json
{
  "chunks": [
    {
      "content": "...",
      "similarity": 0.95
    }
  ],
  "answer": "..."
}
```

## 更换嵌入模型

若更换百炼千问模型，请同步修改 `spring.ai.bailian.model` 与 `spring.ai.vectorstore.milvus.embedding-dimension`，并清空或重建 Milvus 集合（可删除数据卷后重启 `docker compose`）。

```bash
# 删除 Milvus 数据卷
# 重新启动服务
```
