# 本地文档问答 API（Spring Boot 3 + Spring AI + Tika + Milvus + 百炼千问）

## 功能概览

基于 **RAG（Retrieval-Augmented Generation，检索增强生成）** 架构的本地文档问答系统：

- 上传 PDF / TXT 等文件：**Apache Tika** 抽取正文，按配置策略切分为文档片段，写入 **Milvus** 向量库。
- 问答：`/api/qa/query` 向量检索 Top-K 片段；可选调用 **百炼千问大模型** 生成自然语言答案。
- 支持三种切分策略：**字符窗口分块**、**句子合并分块**、**父子索引分块**（推荐）。

## 技术栈

| 技术 | 说明 |
|---|---|
| **Spring Boot 3.4** | 后端框架 |
| **Spring AI 1.0** | AI 能力集成框架（Embedding / Chat / VectorStore） |
| **阿里云百炼平台** | 大模型服务，通过 OpenAI 兼容接口调用 |
| **qwen-plus** | Chat 大语言模型（百炼千问），用于生成回答 |
| **text-embedding-v3** | Embedding 模型（百炼），1024 维，用于文本向量化 |
| **Milvus 2.x** | 向量数据库，存储文档片段向量，支持 ANN 相似度检索 |
| **Apache Tika 3.1** | 文档解析引擎，支持 PDF / DOCX / TXT / HTML 等格式 |
| **Docker Compose** | 一键启动 Milvus 服务 |

## 架构说明

```
上传文档                                   用户提问
   │                                         │
   ▼                                         ▼
Tika 提取文本                      Embedding 模型（text-embedding-v3）
   │                                         │
   ▼                                         ▼
文本切分（CHAR/SENTENCE/PARENT_CHILD）  Milvus 向量相似度检索
   │                                         │
   ▼                                         ▼
Embedding → Milvus 入库              检索到 Top-K 片段
                                             │
                                             ▼
                                   Chat 模型（qwen-plus）生成回答
```

## 分块策略

### 1. CHAR — 固定字符窗口切分

按固定大小（默认 800 字符）滑动窗口切分文本，相邻片段保留重叠区（默认 120 字符），降低语义在边界被截断的风险。

- **优点**：实现简单，速度快
- **适用**：通用场景

### 2. SENTENCE — 句子边界切分

按句末标点（`.` `!` `?` `。` `？` `！` `…`）分割句子，再合并到不超过最大长度（默认 900 字符）的片段中。

- **优点**：保持语义完整性，不会在句子中间截断
- **适用**：中文/英文自然段落文本

### 3. PARENT_CHILD — 父子索引切分（推荐）✨

**两层切分策略**，兼顾检索精准度和上下文丰富度：

1. **第一层**：将全文按字符窗口切分为大的**父块**（默认 1000 字符，重叠 200 字符）
2. **第二层**：将每个父块再切分为小的**子块**（默认 100 字符，重叠 20 字符）
3. **入库**：仅子块存入 Milvus 向量库（子块文本做 Embedding），父块文本保存在子块的 metadata 中
4. **检索**：用小子块做相似度搜索（精准匹配用户问题）
5. **生成**：取出子块对应的父块文本（自动去重），喂给大模型作为完整上下文

- **优点**：小子块检索精准 + 大父块上下文丰富，显著提升回答质量
- **适用**：对回答质量要求较高的场景

## 环境要求

- JDK **17+**
- **Docker**（用于 Milvus）
- **百炼平台 API Key**：用于 Embedding（text-embedding-v3）和 Chat（qwen-plus）模型

## 快速启动

### 1. 启动 Milvus

在 `doc-qa-api` 目录：

```bash
docker compose up -d
```

默认 gRPC：`localhost:19530`（用户名/密码 `root` / `milvus`）。

### 2. 配置 API Key

编辑 `src/main/resources/application.yml`，设置百炼平台 API Key：

```yaml
spring:
  ai:
    openai:
      api-key: 你的百炼平台API-Key
      base-url: https://dashscope.aliyuncs.com/compatible-mode
```

> 建议通过环境变量 `SPRING_AI_OPENAI_API_KEY` 传入，避免泄漏。

### 3. 运行应用

**IntelliJ IDEA**：
1. **File → Open**，选择 `doc-qa-api` 目录（含 `pom.xml`）
2. 等待 Maven 导入完成
3. 运行主类：`com.example.docqa.DocQaApplication`

**命令行**：
```bash
./mvnw spring-boot:run
```

## 配置说明

编辑 `src/main/resources/application.yml`：

| 配置项 | 说明 |
|---|---|
| `spring.ai.openai.api-key` | 百炼平台 API Key |
| `spring.ai.openai.base-url` | 百炼 OpenAI 兼容端点 |
| `spring.ai.openai.embedding.options.model` | Embedding 模型名（text-embedding-v3） |
| `spring.ai.openai.chat.options.model` | Chat 模型名（qwen-plus / qwen-turbo / qwen-max） |
| `spring.ai.vectorstore.milvus.*` | Milvus 连接地址、集合名、向量维度等 |
| `app.chunk.strategy` | 分块策略：`CHAR` / `SENTENCE` / `PARENT_CHILD` |
| `app.chunk.parent-chunk-size` | 父块大小（PARENT_CHILD 模式），默认 1000 |
| `app.chunk.parent-overlap` | 父块重叠字符数，默认 200 |
| `app.chunk.child-chunk-size` | 子块大小（PARENT_CHILD 模式），默认 100 |
| `app.chunk.child-overlap` | 子块重叠字符数，默认 20 |

## API 示例

### 上传文档

**接口描述**：上传文档（支持 PDF / TXT 等），解析并存储到 Milvus 向量数据库。

**请求方式**：`POST`

**URL**：`http://localhost:8080/api/documents`

**请求头**：
- `Content-Type: multipart/form-data`

**请求体**：
- 字段名：`file`
- 类型：文件

**响应示例**：
```json
{
  "chunksStored": 42,
  "message": "成功"
}
```

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

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `question` | String | ✅ | 用户输入的问题，最大 4000 字符 |
| `topK` | Integer | ❌ | 返回的片段数量，默认 5 |
| `generateAnswer` | Boolean | ❌ | 是否调用大模型生成答案，默认 false |
| `similarityThreshold` | Double | ❌ | 相似度阈值（0~1），不传则不做截断 |

**响应示例**：
```json
{
  "answer": "根据文档内容，...",
  "chunks": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "text": "这是检索到的文档片段...",
      "metadata": {
        "source": "产品手册.pdf",
        "parentText": "这是更大的父块上下文..."
      }
    }
  ]
}
```

> **PARENT_CHILD 模式说明**：`chunks` 中返回的是小子块文本（用于检索的精准片段），`metadata.parentText` 是大父块文本（实际喂给大模型的上下文）。

## 更换模型

若更换百炼模型，请注意：

1. 修改 `spring.ai.openai.embedding.options.model` 和 `spring.ai.vectorstore.milvus.embedding-dimension`（须与模型输出维度一致）
2. 清空或重建 Milvus 集合：

```bash
docker compose down -v   # 删除数据卷
docker compose up -d     # 重新启动
```

## 项目结构

```
src/main/java/com/example/docqa/
├── DocQaApplication.java           # Spring Boot 启动类
├── chunk/
│   └── TextChunker.java            # 文本切分器（CHAR / SENTENCE / PARENT_CHILD）
├── config/
│   ├── AppConfig.java              # 配置类
│   └── ChunkProperties.java        # 分块配置属性
├── extract/
│   └── TikaDocumentExtractor.java  # Apache Tika 文档提取器
├── service/
│   ├── DocumentIngestionService.java  # 文档导入服务（提取→切分→入库）
│   └── QaService.java              # 问答服务（检索→生成回答）
└── web/
    ├── ApiExceptionHandler.java    # 全局异常处理
    ├── DocumentController.java     # 文档上传接口
    ├── QaController.java           # 问答查询接口
    └── dto/                        # 请求/响应 DTO
```
