---
title: Huihui Qwen3.8 27B Abliterated Chat
emoji: 🧠
colorFrom: purple
colorTo: blue
sdk: gradio
sdk_version: 6.31.0
app_file: app.py
python_version: "3.12"
startup_duration_timeout: 1h
short_description: Chat with Huihui Qwen3.8 27B abliterated on ZeroGPU
license: apache-2.0
models:
  - huihui-ai/Huihui-Qwen3.8-27B-abliterated
  - huihui-ai/Huihui-Qwen3.8-27B-abliterated-GGUF
tags:
  - qwen
  - abliterated
  - chatbot
  - zerogpu
---

# Huihui Qwen3.8 27B (abliterated) — chat demo

A streaming chat demo for the abliterated (uncensored) Qwen3.8 27B from huihui-ai.

The GGUF files in
[`huihui-ai/Huihui-Qwen3.8-27B-abliterated-GGUF`](https://huggingface.co/huihui-ai/Huihui-Qwen3.8-27B-abliterated-GGUF)
need llama.cpp, which does not run on ZeroGPU, so this Space serves the same model's original weights,
[`huihui-ai/Huihui-Qwen3.8-27B-abliterated`](https://huggingface.co/huihui-ai/Huihui-Qwen3.8-27B-abliterated),
with 🤗 Transformers in bf16 on ZeroGPU (xlarge).

- Tokens stream as they are generated; the Stop button halts generation on the GPU.
- Optional thinking mode, shown in a collapsible "Thinking" bubble.
- Adjustable system prompt, max new tokens, temperature, top-p and top-k.
- Also served as an MCP server (`/chat` endpoint).

Refusals were removed from this model, so it may produce inaccurate or objectionable content.
