# 本地文档问答 API（Spring Boot 3 + Tika + Milvus + Ollama）

## 功能概览

- 上传 PDF / TXT 等文件：Apache Tika 抽取正文，按配置做**字符窗口分块**或**句子合并分块**，写入 **Milvus** 向量库。
- 问答：`/api/qa/query` 量检索 Top-K 片段；可选调用本地 **Ollama** 对话模型生成答案。

## 环境要求

- JDK **17+**
- **Docker**（用于 Milvus）
- **Ollama**（嵌入与可选生成）：安装后执行  
  `ollama pull nomic-embed-text`  
  `ollama pull llama3.2`（或其它你在 `application.yml` 里配置的 chat 模型）

## 启动 Milvus

在 `doc-qa-api` 目录：

```bash
docker compose up -d
```

默认 gRPC：`localhost:19530`（用户名/密码 `root` / `milvus`）。

## IntelliJ IDEA 运行

1. **File → Open**，选择 `doc-qa-api` 目录（含 `pom.xml`）。
2. 等待 Maven 导入完成。
3. 运行主类：`com.example.docqa.DocQaApplication`。

## 配置说明

编辑 `src/main/resources/application.yml`：

- `spring.ai.vectorstore.milvus`：Milvus 地址、集合名、`embedding-dimension`（须与嵌入模型一致；`nomic-embed-text` 为 **768**）。
- `spring.ai.ollama`：Ollama 地址与模型名。
- `app.chunk`：`strategy` 为 `CHAR` 或 `SENTENCE`，以及分块参数。

## API 示例

**上传文档**（multipart，字段名 `file`）：

```http
POST http://localhost:8080/api/documents
Content-Type: multipart/form-data
```

**检索 / 问答**：

```http
POST http://localhost:8080/api/qa/query
Content-Type: application/json

{
  "question": "文档里主要讲了什么？",
  "topK": 5,
  "generateAnswer": true
}
```

- `generateAnswer`: `false` 时仅返回检索到的 `chunks`，不调用对话模型。
- `similarityThreshold`: 可选，0~1，不传则不做相似度截断（Milvus 路径下使用 `similarityThresholdAll()`）。

## 更换嵌入模型

若更换 Ollama 嵌入模型，请同步修改 `spring.ai.ollama.embedding.options.model` 与 `spring.ai.vectorstore.milvus.embedding-dimension`，并清空或重建 Milvus 集合（可删除数据卷后重启 `docker compose`）。
