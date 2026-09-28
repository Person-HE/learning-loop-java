/**
 * Know 同源本地嵌入 sidecar：Xenova/nomic-embed-text-v1.5 · 768 维 · L2
 * 启动：npm i && node server.js
 */
const http = require('http');
const path = require('path');
const os = require('os');

const PORT = Number(process.env.EMBED_PORT || 3003);
const MODEL_ID = process.env.EMBED_MODEL || 'Xenova/nomic-embed-text-v1.5';
const cacheDir =
  process.env.KNOW_MODELS_DIR ||
  path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'KnowledgeBase', 'models');

let pipePromise = null;

async function getPipe() {
  if (pipePromise) return pipePromise;
  pipePromise = (async () => {
    const transformers = await import('@huggingface/transformers');
    transformers.env.cacheDir = cacheDir;
    transformers.env.allowRemoteModels = true;
    transformers.env.backends = { onnx: { wasm: {} } };
    return await transformers.pipeline('feature-extraction', MODEL_ID, { quantized: true });
  })().catch((e) => {
    pipePromise = null;
    throw e;
  });
  return pipePromise;
}

function l2(vec) {
  let n = 0;
  for (const x of vec) n += x * x;
  n = Math.sqrt(n);
  if (n < 1e-12) return vec;
  return vec.map((x) => x / n);
}

async function embed(texts) {
  const pipe = await getPipe();
  const BATCH = 4;
  const all = [];
  for (let i = 0; i < texts.length; i += BATCH) {
    const batch = texts.slice(i, i + BATCH);
    const out = await pipe(batch, { pooling: 'mean', normalize: true, truncation: true, max_length: 512 });
    const dims = out.dims;
    const data = out.data;
    if (dims.length === 3) {
      const [b, seq, hidden] = dims;
      for (let bi = 0; bi < b; bi++) {
        const v = new Array(hidden).fill(0);
        for (let s = 0; s < seq; s++) {
          const off = (bi * seq + s) * hidden;
          for (let h = 0; h < hidden; h++) v[h] += data[off + h];
        }
        for (let h = 0; h < hidden; h++) v[h] /= seq;
        all.push(l2(v));
      }
    } else {
      const hidden = dims[dims.length - 1];
      for (let bi = 0; bi < dims[0]; bi++) {
        all.push(l2(Array.from(data.slice(bi * hidden, (bi + 1) * hidden))));
      }
    }
  }
  return all;
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, model: MODEL_ID, dim: 768, cacheDir }));
    return;
  }
  if (req.method === 'POST' && req.url === '/embed') {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', async () => {
      try {
        const { texts } = JSON.parse(body || '{}');
        if (!Array.isArray(texts) || !texts.length) {
          res.writeHead(400);
          res.end(JSON.stringify({ error: 'texts required' }));
          return;
        }
        const t0 = Date.now();
        const vectors = await embed(texts);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ model: MODEL_ID, dim: 768, ms: Date.now() - t0, vectors }));
      } catch (e) {
        res.writeHead(500);
        res.end(JSON.stringify({ error: String((e && e.message) || e) }));
      }
    });
    return;
  }
  res.writeHead(404);
  res.end();
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('embedding sidecar 127.0.0.1:' + PORT + ' model=' + MODEL_ID + ' cache=' + cacheDir);
});
