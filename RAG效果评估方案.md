# RAG 系统效果评估分析解决方案

## 📋 目录

1. [评估背景](#评估背景)
2. [评估框架](#评估框架)
3. [核心评估维度](#核心评估维度)
4. [评估指标体系](#评估指标体系)
5. [评估方法与工具](#评估方法与工具)
6. [本项目评估实施方案](#本项目评估实施方案)
7. [优化建议](#优化建议)
8. [附录：评估工具代码示例](#附录评估工具代码示例)

---

## 评估背景

### RAG 系统的核心价值

RAG（Retrieval-Augmented Generation，检索增强生成）通过结合**信息检索**和**文本生成**，解决了纯大模型的以下问题：
- ❌ 知识截止日期限制
- ❌ 幻觉（Hallucination）问题
- ❌ 无法访问私有/实时数据

### 为什么需要评估

RAG 系统涉及多个环节（文档切分、向量化、检索、生成），任何环节的问题都会影响最终效果：

```
文档 → 切分 → Embedding → 向量库 → 检索 → 生成回答
  ↓      ↓        ↓         ↓       ↓       ↓
质量    策略    模型质量    索引    召回    准确性
```

**没有评估，就无法：**
- 📊 量化系统性能
- 🔍 定位瓶颈环节
- 📈 验证优化效果
- ⚖️ 对比不同方案（如切分策略、Embedding 模型、LLM 选择）

---

## 评估框架

### 三层评估模型

```
┌─────────────────────────────────────────────────────┐
│          端到端评估（End-to-End Evaluation）         │
│  评估整体问答质量：答案准确性、完整性、用户满意度    │
└─────────────────────────────────────────────────────┘
                         ▲
                         │
        ┌────────────────┴────────────────┐
        │                                  │
┌───────┴────────┐              ┌─────────┴─────────┐
│  检索评估       │              │   生成评估         │
│  Retrieval     │              │   Generation      │
│  - 召回率       │              │   - 准确性         │
│  - 精确率       │              │   - 流畅性         │
│  - MRR         │              │   - 相关性         │
│  - NDCG        │              │   - 忠实度         │
└────────────────┘              └───────────────────┘
```

### 评估原则

1. **离线评估 + 在线评估**：离线构建测试集快速迭代，在线收集用户反馈持续优化
2. **自动化 + 人工评估**：自动化指标快速量化，人工评估保证质量
3. **多维度综合评估**：单一指标容易误导，需要多维度交叉验证

---

## 核心评估维度

### 1️⃣ 检索质量评估（Retrieval Evaluation）

检索是 RAG 的基础，**检索不准，生成再好也没用**。

#### 关键问题
- ✅ 检索到的片段是否包含答案？（召回率）
- ✅ Top-K 结果中有多少是相关的？（精确率）
- ✅ 最相关的片段排在前面吗？（排序质量）

#### 核心指标

| 指标 | 公式 | 说明 | 适用场景 |
|------|------|------|----------|
| **召回率@K (Recall@K)** | `相关文档数 / 总相关文档数` | 衡量是否找到了所有相关文档 | 多文档答案场景 |
| **精确率@K (Precision@K)** | `相关文档数 / K` | Top-K 中相关文档的比例 | 控制噪声 |
| **命中率@K (Hit Rate@K)** | `至少有1个相关文档的查询比例` | 至少检索到一个相关片段 | 单文档答案场景 |
| **MRR (Mean Reciprocal Rank)** | `平均(1/首个相关文档排名)` | 第一个相关结果的平均位置 | 排序质量 |
| **NDCG@K** | 考虑位置和相关性的折扣累计增益 | 综合排序质量 | 精细化评估 |

#### 示例

假设检索结果（✅相关 ❌不相关）：

**查询1**: `[✅, ✅, ❌, ❌, ✅]` (Top-5)
**查询2**: `[❌, ✅, ✅, ❌, ❌]` (Top-5)

计算（K=5）：
- **Recall@5**: 需要知道总共有多少个相关文档（假设各3个）
  - 查询1: 3/3 = 100%
  - 查询2: 2/3 = 67%
- **Precision@5**: 
  - 查询1: 3/5 = 60%
  - 查询2: 2/5 = 40%
- **MRR**: 
  - 查询1: 1/1 = 1.0
  - 查询2: 1/2 = 0.5
  - 平均: 0.75

---

### 2️⃣ 生成质量评估（Generation Evaluation）

评估大模型生成的答案质量。

#### 关键问题
- ✅ 答案是否准确回答了问题？（准确性）
- ✅ 答案是否基于检索内容，没有幻觉？（忠实度）
- ✅ 答案是否完整且有用？（完整性、有用性）

#### 核心指标

| 维度 | 指标 | 说明 | 评估方法 |
|------|------|------|----------|
| **准确性** | Correctness | 答案是否正确 | 人工标注 / LLM-as-Judge |
| **忠实度** | Faithfulness | 答案是否基于检索内容，无幻觉 | 检查引用 / 自动检测 |
| **相关性** | Relevance | 答案是否回答了问题 | BLEU, ROUGE, BERTScore |
| **完整性** | Completeness | 答案是否涵盖关键信息 | 人工评分 |
| **流畅性** | Fluency | 语言是否自然流畅 | 困惑度 / 人工评分 |
| **简洁性** | Conciseness | 答案是否简洁不冗余 | 长度 + 人工评分 |

#### LLM-as-Judge 方法

使用强大的 LLM（如 GPT-4、Claude）作为评估器，评估答案质量：

```python
评估提示词模板：
"""
你是一个专业的问答系统评估员。

【问题】{question}

【参考文档】{retrieved_context}

【系统生成的答案】{generated_answer}

【标准答案（可选）】{ground_truth}

请从以下维度评分（1-5分）：
1. 准确性：答案是否正确回答了问题？
2. 忠实度：答案是否基于参考文档，没有编造信息？
3. 完整性：答案是否完整，没有遗漏关键信息？
4. 相关性：答案是否切题？

请给出评分和理由。
"""
```

---

### 3️⃣ 端到端评估（End-to-End Evaluation）

从用户角度评估整体体验。

#### 核心指标

| 指标 | 说明 | 评估方法 |
|------|------|----------|
| **答案准确率** | 正确答案数 / 总问题数 | 人工标注或 LLM 评估 |
| **用户满意度** | 用户评分（1-5星） | 用户反馈 / A/B测试 |
| **响应时间** | 从提问到返回答案的时间 | 系统日志 |
| **拒答率** | 系统主动拒绝回答的比例 | 统计"无法回答"响应 |
| **幻觉率** | 包含虚假信息的答案比例 | 人工检查或自动检测 |

---

## 评估指标体系

### 推荐的最小评估指标集

#### 📊 必选指标（核心）

1. **Hit Rate@5**：至少检索到一个相关片段的比例
2. **MRR**：首个相关片段的平均排名倒数
3. **答案准确率**：答案正确的比例（人工或LLM评估）
4. **忠实度得分**：答案基于检索内容的程度

#### 📈 可选指标（深入分析）

- Recall@K、Precision@K、NDCG@K（检索细节）
- BLEU、ROUGE、BERTScore（生成质量）
- 响应时间、成本（工程指标）

---

## 评估方法与工具

### 方法一：构建测试数据集（黄金标准）

#### 1. 测试集构建步骤

```
步骤1：收集真实问题
  ├─ 历史用户问题
  ├─ 领域专家设计的问题
  └─ 文档自动生成问题（用 LLM）

步骤2：人工标注
  ├─ 标注相关文档片段（用于检索评估）
  └─ 标注标准答案（用于生成评估）

步骤3：定期更新
  └─ 随着文档和业务变化更新测试集
```

#### 2. 测试集格式示例

```json
[
  {
    "question_id": "q001",
    "question": "本系统支持哪些文档格式？",
    "relevant_chunk_ids": ["chunk_123", "chunk_456"],
    "ground_truth_answer": "支持 PDF、TXT、DOCX、HTML 等格式",
    "category": "功能咨询",
    "difficulty": "easy"
  },
  {
    "question_id": "q002",
    "question": "PARENT_CHILD 切分策略的优势是什么？",
    "relevant_chunk_ids": ["chunk_789"],
    "ground_truth_answer": "小子块检索精准 + 大父块上下文丰富",
    "category": "技术细节",
    "difficulty": "medium"
  }
]
```

### 方法二：自动化评估框架

#### 开源工具推荐

| 工具 | 功能 | 链接 |
|------|------|------|
| **RAGAS** | RAG专用评估框架（忠实度、答案相关性等） | https://github.com/explodinggradients/ragas |
| **TruLens** | LLM 应用评估与监控 | https://github.com/truera/trulens |
| **LangSmith** | LangChain 官方评估平台 | https://smith.langchain.com/ |
| **Phoenix** | Arize AI 的可观测性工具 | https://github.com/Arize-ai/phoenix |
| **DeepEval** | 端到端 LLM 评估库 | https://github.com/confident-ai/deepeval |

#### RAGAS 核心指标

```python
from ragas import evaluate
from ragas.metrics import (
    faithfulness,        # 忠实度：答案基于上下文程度
    answer_relevancy,    # 答案相关性
    context_precision,   # 上下文精确度（检索质量）
    context_recall       # 上下文召回率
)

metrics = [
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall
]

results = evaluate(dataset, metrics=metrics)
```

### 方法三：在线评估与反馈收集

#### 1. 用户反馈机制

```json
响应中添加反馈接口：
{
  "answer": "...",
  "chunks": [...],
  "feedback_url": "/api/feedback",
  "session_id": "uuid"
}

用户反馈格式：
{
  "session_id": "uuid",
  "rating": 4,                    // 1-5 星
  "is_helpful": true,             // 是否有帮助
  "issues": ["incomplete"],       // 问题标签
  "comment": "答案不够详细"        // 可选文字反馈
}
```

#### 2. A/B 测试

对比不同配置的效果：
- **实验组A**：CHAR 切分策略
- **实验组B**：PARENT_CHILD 切分策略
- **指标**：用户满意度、答案采纳率

---

## 本项目评估实施方案

### 当前架构回顾

```
本项目 = Spring Boot + Spring AI + Milvus + 百炼千问
├─ Embedding: text-embedding-v3 (1024维)
├─ LLM: qwen-plus
├─ VectorDB: Milvus
└─ 切分策略: CHAR / SENTENCE / PARENT_CHILD（可配置）
```

### 第一阶段：基础评估（2周）

#### 目标
快速建立评估能力，验证当前系统效果。

#### 任务清单

##### ✅ 任务1：构建小规模测试集（30-50 个问题）

1. **问题来源**：
   - 根据现有文档手动设计 20 个问题
   - 用 LLM 自动生成 10-30 个问题（prompt见附录）
   
2. **标注内容**：
   - 标注每个问题对应的相关文档片段 ID
   - 编写标准答案（可选）

3. **存储格式**：JSON 文件 `test_dataset.json`

##### ✅ 任务2：实现检索评估 API

在 `QaService` 中添加检索评估方法：

```java
/**
 * 评估检索质量
 * @param question 问题
 * @param relevantChunkIds 人工标注的相关片段ID集合
 * @param topK 检索数量
 * @return 评估指标（Hit Rate, MRR, Precision, Recall）
 */
public RetrievalMetrics evaluateRetrieval(
    String question, 
    Set<String> relevantChunkIds, 
    int topK
)
```

##### ✅ 任务3：添加评估日志

在 `QaController` 中记录每次查询的详细信息：

```java
// 日志格式
{
  "timestamp": "2026-04-09T10:30:00Z",
  "question": "...",
  "retrieved_chunk_ids": [...],
  "answer": "...",
  "response_time_ms": 850,
  "top_k": 5,
  "similarity_scores": [0.89, 0.85, 0.82, 0.78, 0.75]
}
```

##### ✅ 任务4：运行基准测试

使用测试集运行检索评估，对比三种切分策略：

```bash
# 配置文件切换策略
app.chunk.strategy: CHAR
app.chunk.strategy: SENTENCE
app.chunk.strategy: PARENT_CHILD
```

记录每种策略的：
- Hit Rate@5
- MRR
- 平均响应时间

### 第二阶段：深度评估（3-4周）

#### 目标
引入自动化评估工具，建立持续监控能力。

##### ✅ 任务1：集成 RAGAS 评估

创建 Python 评估脚本（或通过 Java 调用）：

```python
# evaluate_rag.py
import requests
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy

def query_api(question):
    """调用本项目的问答 API"""
    response = requests.post(
        "http://localhost:8080/api/qa/query",
        json={"question": question, "generateAnswer": True, "topK": 5}
    )
    return response.json()

def run_evaluation(test_dataset):
    """批量评估"""
    results = []
    for item in test_dataset:
        resp = query_api(item["question"])
        results.append({
            "question": item["question"],
            "contexts": [c["text"] for c in resp["chunks"]],
            "answer": resp["answer"],
            "ground_truth": item.get("ground_truth_answer")
        })
    
    # 使用 RAGAS 评估
    scores = evaluate(results, metrics=[faithfulness, answer_relevancy])
    return scores
```

##### ✅ 任务2：实现 LLM-as-Judge

调用百炼千问对答案质量评分：

```java
public AnswerQualityScore evaluateAnswer(
    String question,
    String context,
    String generatedAnswer,
    String groundTruth
) {
    String prompt = """
        评估以下问答系统的回答质量（1-5分）：
        
        问题：%s
        检索上下文：%s
        生成答案：%s
        标准答案：%s
        
        评分维度：
        1. 准确性（是否正确）
        2. 忠实度（是否基于上下文，无幻觉）
        3. 完整性（是否完整）
        
        请以JSON格式返回：
        {"accuracy": 4, "faithfulness": 5, "completeness": 3, "reasoning": "..."}
        """.formatted(question, context, generatedAnswer, groundTruth);
    
    String response = chatModel.call(prompt);
    return parseScore(response);
}
```

##### ✅ 任务3：搭建评估仪表板

使用 Grafana + InfluxDB 或简单的 Web 页面展示：
- 📈 实时评估指标趋势
- 📊 不同策略对比
- 🐛 低分样本分析

### 第三阶段：持续优化（长期）

#### 建立评估-优化闭环

```
1. 定期评估（每周/每月）
   ↓
2. 识别问题
   - 检索召回率低？→ 优化切分策略、调整 topK
   - 答案忠实度低？→ 优化 prompt、调整温度参数
   - 响应慢？→ 优化索引、减少检索量
   ↓
3. 实施优化
   ↓
4. A/B 测试验证
   ↓
5. 回到步骤1
```

#### 持续监控指标

| 指标 | 阈值 | 告警 |
|------|------|------|
| Hit Rate@5 | < 70% | ⚠️ 检索质量下降 |
| 平均忠实度 | < 4.0 | ⚠️ 幻觉风险 |
| P95 响应时间 | > 3s | ⚠️ 性能问题 |
| 拒答率 | > 20% | ⚠️ 文档覆盖不足 |

---

## 优化建议

### 🎯 检索优化方向

#### 1. 切分策略调优

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 召回率低 | 切块太小，上下文分散 | 增大块大小或使用 PARENT_CHILD |
| 精确率低 | 切块太大，噪声多 | 减小块大小或增加重叠 |
| 边界截断 | 固定长度切分 | 使用 SENTENCE 策略 |

**推荐配置**（PARENT_CHILD）：
```yaml
app:
  chunk:
    strategy: PARENT_CHILD
    parent-chunk-size: 1200  # 增大父块
    parent-overlap: 200
    child-chunk-size: 150    # 调整子块
    child-overlap: 30
```

#### 2. 检索参数调优

```yaml
# 增加 topK，提高召回
topK: 10  # 从 5 → 10

# 调整相似度阈值
similarityThreshold: 0.7  # 过滤低质量片段

# Milvus 搜索参数
nprobe: 64  # 从 32 → 64（提高精度，牺牲速度）
```

#### 3. 混合检索（Hybrid Search）

结合向量检索 + 关键词检索（BM25）：

```java
// 伪代码
List<Document> vectorResults = milvusVectorStore.search(query, 10);
List<Document> bm25Results = bm25Search(query, 10);
List<Document> merged = rerank(vectorResults, bm25Results);
```

**优势**：向量捕捉语义，BM25 捕捉精确匹配。

#### 4. 查询重写（Query Rewriting）

用 LLM 改写用户问题，提高检索效果：

```java
String rewrittenQuery = chatModel.call(
    "将以下问题改写为适合搜索的查询：" + originalQuery
);
```

### 🚀 生成优化方向

#### 1. Prompt 工程

**现有 Prompt**：
```
你是文档问答助手。请仅根据用户提供的参考片段回答问题；
若片段不足以回答，请明确说明无法从文档中得出答案，不要编造。
```

**改进方向**：
- ✅ 要求引用来源：`请在答案中引用相关片段编号，如 [1][2]`
- ✅ 强调简洁性：`请用简洁的语言回答，避免冗余`
- ✅ 结构化输出：`如果问题涉及多个要点，请分点列出`

**进阶：思维链（Chain-of-Thought）**：
```
请按以下步骤思考：
1. 分析用户问题的核心需求
2. 在参考片段中寻找相关信息
3. 综合信息给出答案
4. 如果信息不足，说明原因
```

#### 2. 模型选择

| 模型 | 特点 | 适用场景 |
|------|------|----------|
| qwen-turbo | 速度快、成本低 | 简单问答 |
| qwen-plus | 平衡性能与成本 | 通用场景（当前） |
| qwen-max | 质量最高 | 复杂推理、关键业务 |

**建议**：用测试集对比不同模型的评分，选择性价比最高的。

#### 3. 温度与采样参数

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          temperature: 0.1  # 降低随机性，提高稳定性
          top-p: 0.9
          max-tokens: 800
```

### 📊 工程优化

#### 1. 缓存机制

```java
@Cacheable(value = "qaCache", key = "#question")
public QaQueryResponse query(QaQueryRequest req) {
    // 相同问题直接返回缓存结果
}
```

#### 2. 批量处理

支持批量问答，提高吞吐量：

```java
@PostMapping("/batch-query")
public List<QaQueryResponse> batchQuery(@RequestBody List<String> questions) {
    return questions.parallelStream()
        .map(q -> qaService.query(new QaQueryRequest(q, 5, false, null)))
        .toList();
}
```

#### 3. 异步生成

检索和生成分离，检索结果先返回：

```java
// 先返回检索结果
QaQueryResponse response = new QaQueryResponse(null, chunks);

// 异步生成答案
if (generateAnswer) {
    CompletableFuture.runAsync(() -> {
        String answer = generateAnswer(question, chunks);
        // 通过 WebSocket 或 SSE 推送答案
    });
}
```

---

## 附录：评估工具代码示例

### 附录A：Java 检索评估实现

```java
package com.example.docqa.evaluation;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class RetrievalEvaluator {

    /**
     * 计算检索指标
     */
    public RetrievalMetrics evaluate(
            List<String> retrievedIds,
            Set<String> relevantIds
    ) {
        int k = retrievedIds.size();
        Set<String> retrieved = new HashSet<>(retrievedIds);
        
        // 1. 命中率（Hit Rate）
        boolean hasHit = retrieved.stream().anyMatch(relevantIds::contains);
        double hitRate = hasHit ? 1.0 : 0.0;
        
        // 2. 精确率（Precision）
        long relevantCount = retrieved.stream()
                .filter(relevantIds::contains)
                .count();
        double precision = (double) relevantCount / k;
        
        // 3. 召回率（Recall）
        double recall = relevantIds.isEmpty() ? 0.0 : 
                (double) relevantCount / relevantIds.size();
        
        // 4. MRR（Mean Reciprocal Rank）
        double mrr = 0.0;
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (relevantIds.contains(retrievedIds.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }
        
        // 5. NDCG@K（简化版：相关文档得分为1，不相关为0）
        double dcg = 0.0;
        double idcg = 0.0;
        for (int i = 0; i < k; i++) {
            if (i < retrievedIds.size() && relevantIds.contains(retrievedIds.get(i))) {
                dcg += 1.0 / Math.log(i + 2);  // log2(i+1) = log(i+1)/log(2)
            }
            if (i < relevantIds.size()) {
                idcg += 1.0 / Math.log(i + 2);
            }
        }
        double ndcg = idcg == 0 ? 0.0 : dcg / idcg;
        
        return new RetrievalMetrics(hitRate, precision, recall, mrr, ndcg);
    }
    
    /**
     * 批量评估
     */
    public AggregatedMetrics evaluateBatch(List<TestCase> testCases) {
        List<RetrievalMetrics> results = testCases.stream()
                .map(tc -> evaluate(tc.retrievedIds(), tc.relevantIds()))
                .toList();
        
        return new AggregatedMetrics(
                results.stream().mapToDouble(RetrievalMetrics::hitRate).average().orElse(0.0),
                results.stream().mapToDouble(RetrievalMetrics::precision).average().orElse(0.0),
                results.stream().mapToDouble(RetrievalMetrics::recall).average().orElse(0.0),
                results.stream().mapToDouble(RetrievalMetrics::mrr).average().orElse(0.0),
                results.stream().mapToDouble(RetrievalMetrics::ndcg).average().orElse(0.0)
        );
    }
    
    public record TestCase(List<String> retrievedIds, Set<String> relevantIds) {}
    
    public record RetrievalMetrics(
            double hitRate,
            double precision,
            double recall,
            double mrr,
            double ndcg
    ) {}
    
    public record AggregatedMetrics(
            double avgHitRate,
            double avgPrecision,
            double avgRecall,
            double avgMRR,
            double avgNDCG
    ) {}
}
```

### 附录B：评估 REST API

```java
package com.example.docqa.web;

import com.example.docqa.evaluation.RetrievalEvaluator;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final RetrievalEvaluator evaluator;
    
    public EvaluationController(RetrievalEvaluator evaluator) {
        this.evaluator = evaluator;
    }
    
    /**
     * 评估单个查询的检索质量
     */
    @PostMapping("/retrieval")
    public RetrievalEvaluator.RetrievalMetrics evaluateRetrieval(
            @RequestBody EvaluationRequest request
    ) {
        return evaluator.evaluate(
                request.retrievedIds(),
                request.relevantIds()
        );
    }
    
    /**
     * 批量评估测试集
     */
    @PostMapping("/batch")
    public RetrievalEvaluator.AggregatedMetrics evaluateBatch(
            @RequestBody List<EvaluationRequest> requests
    ) {
        List<RetrievalEvaluator.TestCase> testCases = requests.stream()
                .map(r -> new RetrievalEvaluator.TestCase(
                        r.retrievedIds(),
                        r.relevantIds()
                ))
                .toList();
        return evaluator.evaluateBatch(testCases);
    }
    
    public record EvaluationRequest(
            String question,
            List<String> retrievedIds,
            Set<String> relevantIds
    ) {}
}
```

### 附录C：测试集生成 Prompt

```
你是一个文档分析助手。请基于以下文档内容生成问答对：

【文档内容】
{document_text}

【要求】
1. 生成 10 个问题，涵盖：
   - 简单事实查询（5个）：直接从文档找答案
   - 推理问题（3个）：需要综合多处信息
   - 复杂问题（2个）：需要深入理解
   
2. 每个问题标注：
   - 问题文本
   - 答案
   - 包含答案的文档片段位置（如第3段、第5段）
   - 难度级别（easy/medium/hard）

3. 输出格式：JSON 数组

【输出示例】
[
  {
    "question": "系统支持哪些文档格式？",
    "answer": "支持 PDF、TXT、DOCX、HTML 等格式",
    "relevant_sections": ["第1段", "表格1"],
    "difficulty": "easy"
  }
]
```

### 附录D：监控 SQL（如果使用数据库存储日志）

```sql
-- 每日问答量趋势
SELECT DATE(timestamp) AS date, COUNT(*) AS query_count
FROM qa_logs
GROUP BY DATE(timestamp)
ORDER BY date DESC;

-- 平均响应时间
SELECT AVG(response_time_ms) AS avg_response_time
FROM qa_logs
WHERE timestamp > NOW() - INTERVAL 7 DAY;

-- 低分答案分析（假设有用户评分）
SELECT question, answer, rating
FROM qa_logs
WHERE rating IS NOT NULL AND rating <= 2
ORDER BY timestamp DESC
LIMIT 20;

-- Top-K 覆盖率（至少检索到1个结果的比例）
SELECT 
  COUNT(*) AS total_queries,
  SUM(CASE WHEN retrieved_count > 0 THEN 1 ELSE 0 END) AS successful_queries,
  ROUND(SUM(CASE WHEN retrieved_count > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS coverage_rate
FROM qa_logs;
```

---

## 总结

### 🎯 核心要点

1. **评估是 RAG 系统优化的基础**：没有量化就无法改进
2. **分层评估**：检索、生成、端到端三个层次
3. **黄金指标**：Hit Rate、MRR、忠实度、答案准确率
4. **持续迭代**：评估 → 发现问题 → 优化 → 再评估

### 📅 实施路线图

```
第1周：构建测试集（30-50个问题）+ 实现基础评估代码
第2周：运行基准测试，对比三种切分策略
第3-4周：集成自动化评估工具（RAGAS 或 LLM-as-Judge）
第5周+：建立持续监控，定期评估和优化
```

### 🔗 参考资源

- [RAGAS 官方文档](https://docs.ragas.io/)
- [RAG 评估最佳实践（Anthropic）](https://www.anthropic.com/research/evaluating-retrieval-augmented-generation)
- [LangChain RAG 评估指南](https://python.langchain.com/docs/guides/evaluation/)
- [Milvus 性能调优](https://milvus.io/docs/tune.md)

---

**文档版本**: v1.0  
**最后更新**: 2026-04-09  
**适用项目**: doc-qa-api (Spring AI + Milvus + 百炼千问)

