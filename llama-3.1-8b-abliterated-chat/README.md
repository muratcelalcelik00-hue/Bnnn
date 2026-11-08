---
title: Llama 3.1 8B Instruct Abliterated Chat
emoji: 🦙
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 6.31.0
app_file: app.py
python_version: "3.12"
startup_duration_timeout: 1h
short_description: Chat with Llama 3.1 8B Instruct abliterated on ZeroGPU
license: llama3.1
models:
  - mlabonne/Meta-Llama-3.1-8B-Instruct-abliterated
tags:
  - llama
  - abliterated
  - chatbot
  - zerogpu
---

# Llama 3.1 8B Instruct (abliterated) — chat demo

A streaming chat demo for
[`mlabonne/Meta-Llama-3.1-8B-Instruct-abliterated`](https://huggingface.co/mlabonne/Meta-Llama-3.1-8B-Instruct-abliterated),
an uncensored version of Llama 3.1 8B Instruct created with
[abliteration](https://huggingface.co/blog/mlabonne/abliteration).

- Runs the full model in bf16 with 🤗 Transformers on ZeroGPU.
- Tokens stream as they are generated; the Stop button halts generation on the GPU.
- Adjustable system prompt, max new tokens, temperature, top-p, top-k and repetition penalty.
- Long conversations drop their oldest turns to stay within an 8K-token prompt.
- Also served as an MCP server (`/chat` endpoint).

The model has had its refusal behaviour removed, so it may produce inaccurate or objectionable content.
Use is subject to the [Llama 3.1 Community License](https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct/blob/main/LICENSE).
