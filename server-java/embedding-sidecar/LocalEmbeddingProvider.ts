// src/server/ai-engine/providers/LocalEmbeddingProvider.ts
import path from 'node:path';
import { childLogger, KB_DATA_DIR } from '../../common/index';
import type { IEmbeddingProvider, EmbeddingOptions } from './IEmbeddingProvider';
import type { EmbeddingResult } from '../types';
import { l2Normalize } from '../services/vectorMath';

const log = childLogger('ai-engine:LocalEmbeddingProvider');

/**
 * 本地 embedding provider：在 Node 进程内用 transformers.js 加载一个下载到本地的
 * ONNX 向量模型，无需 Ollama、无需外部服务。首次调用会按需从模型仓库下载模型到
 * KB_DATA_DIR/models 缓存目录，之后离线可用。
 *
 * 默认模型 Xenova/nomic-embed-text-v1.5（768 维），与历史 Ollama nomic-embed-text
 * 维度一致，因此既有的 vec_chunks / vec_personal（768 维）无需重建即可继续复用。
 */
export class LocalEmbeddingProvider implements IEmbeddingProvider {
  public readonly id = 'local';
  public readonly displayName = '本地模型（transformers.js）';
  public readonly isLocal = true;

  private pipeline: unknown = null;
  private readonly modelId: string;
  private readonly dimension: number;
  private loadingPromise: Promise<unknown> | null = null;

  constructor(modelId = 'Xenova/nomic-embed-text-v1.5', dimension = 768) {
    this.modelId = modelId;
    this.dimension = dimension;
  }

  private async getPipeline(): Promise<unknown> {
    if (this.pipeline) return this.pipeline;
    if (this.loadingPromise) return this.loadingPromise;

    this.loadingPromise = (async () => {
      let transformers: typeof import('transformers');
      try {
        transformers = await import('transformers');
      } catch (err) {
        throw new Error(
          '本地 embedding 依赖 transformers 未安装，请在后端依赖中执行 `npm install transformers` 后重启服务。',
        );
      }

      // 模型缓存目录置于数据目录，避免污染项目目录；WASM 后端纯 JS/wasm，无原生编译。
      try {
        const env = (transformers as unknown as { env: Record<string, unknown> }).env;
        if (KB_DATA_DIR) {
          env.cacheDir = path.join(KB_DATA_DIR, 'models');
        }
        env.allowRemoteModels = true;
        env.backends = { ...(typeof env.backends === 'object' && env.backends ? env.backends : {}), onnx: { wasm: {} } };
      } catch {
        /* env 形态差异不影响核心推理 */
      }

      log.info({ modelId: this.modelId }, '加载本地 embedding 模型（首次会下载到本地缓存）');
      const pipe = await (transformers as unknown as {
        pipeline: (task: string, model: string, opts?: Record<string, unknown>) => Promise<unknown>;
      }).pipeline('feature-extraction', this.modelId, { quantized: true });

      this.pipeline = pipe;
      return pipe;
    })();

    try {
      return await this.loadingPromise;
    } finally {
      // 失败时不缓存 Promise，允许下次重试
      this.loadingPromise = null;
    }
  }

  public async embed(texts: string[], _opts?: EmbeddingOptions): Promise<EmbeddingResult> {
    const t0 = Date.now();
    const pipe = (await this.getPipeline()) as (input: string[], opts?: Record<string, unknown>) => Promise<{
      data: Float32Array | number[];
      dims: number[];
      tolist?: () => number[][] | number[][][];
    }>;

    const inputs = Array.isArray(texts) ? texts : [texts];
    // 分批处理：每批最多 4 条，避免超长输入导致中间张量 OOM
    const BATCH = 4;
    const allVectors: number[][] = [];
    for (let i = 0; i < inputs.length; i += BATCH) {
      const batch = inputs.slice(i, i + BATCH);
      const output = await pipe(batch, {
        pooling: 'mean',
        normalize: true,
        truncation: true,
        max_length: 512,
      });
      allVectors.push(...this.extractVectors(output));
    }

    const dim = allVectors[0]?.length ?? this.dimension;

    log.info({ count: inputs.length, dim, ms: Date.now() - t0 }, '本地向量化完成');
    return {
      vectors: allVectors,
      model: this.modelId,
      dim,
      usage: { promptTokens: 0, totalTokens: 0 },
      usageEstimated: true,
    };
  }

  /**
   * 从 transformers.js 返回的 Tensor 中抽取 batch×dim 向量数组。
   * 兼容两种形态：
   * - 已 pooling：dims = [batch, dim]
   * - 未 pooling：dims = [batch, seq, hidden]（兜底做 mean pool）
   * 并对每个向量做 L2 归一化（保证「L2 距离 ⇄ 余弦相似度」换算成立）。
   */
  private extractVectors(output: {
    data: Float32Array | number[];
    dims: number[];
    tolist?: () => number[][] | number[][][];
  }): number[][] {
    const data = output.data;
    const dims = output.dims;
    const toArr = (buf: Float32Array | number[]): number[] =>
      buf instanceof Float32Array ? Array.from(buf) : (buf as number[]);

    if (dims.length === 3) {
      const [batch, seq, hidden] = dims;
      const flat = toArr(data);
      const vectors: number[][] = [];
      for (let b = 0; b < batch; b++) {
        const v = new Array<number>(hidden).fill(0);
        for (let s = 0; s < seq; s++) {
          const off = (b * seq + s) * hidden;
          for (let h = 0; h < hidden; h++) v[h] += flat[off + h];
        }
        for (let h = 0; h < hidden; h++) v[h] /= seq;
        vectors.push(l2Normalize(v));
      }
      return vectors;
    }

    // 2D: [batch, hidden]
    const hidden = dims[dims.length - 1];
    const flat = toArr(data);
    const batch = dims[0];
    const vectors: number[][] = [];
    for (let b = 0; b < batch; b++) {
      vectors.push(l2Normalize(flat.slice(b * hidden, (b + 1) * hidden)));
    }
    return vectors;
  }

  public getDimension(): number {
    return this.dimension;
  }

  public async healthCheck(): Promise<boolean> {
    try {
      await this.getPipeline();
      return true;
    } catch {
      return false;
    }
  }
}
