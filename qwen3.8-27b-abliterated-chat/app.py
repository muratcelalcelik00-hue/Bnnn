import spaces  # must be imported before torch / transformers so ZeroGPU can patch CUDA

import os
from threading import Event, Thread
from typing import Any, Iterator

import gradio as gr
import torch
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    StoppingCriteria,
    StoppingCriteriaList,
    TextIteratorStreamer,
)

# Original (non-GGUF) weights of huihui-ai/Huihui-Qwen3.8-27B-abliterated-GGUF, so the model can run on ZeroGPU.
MODEL_ID = "huihui-ai/Huihui-Qwen3.8-27B-abliterated"
GGUF_ID = "huihui-ai/Huihui-Qwen3.8-27B-abliterated-GGUF"

MAX_INPUT_TOKENS = int(os.getenv("MAX_INPUT_TOKENS", "16384"))
DEFAULT_MAX_NEW_TOKENS = 2048
MAX_MAX_NEW_TOKENS = 4096
THINK_END = "</think>"

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
# Text-only model loaded from the vision-language checkpoint (bf16 ≈ 54 GB → ZeroGPU "xlarge").
model = AutoModelForCausalLM.from_pretrained(MODEL_ID, dtype=torch.bfloat16).to("cuda")
model.eval()


def _content_to_text(content: Any) -> str:
    """Flatten a Gradio message `content` (str, text part dict, or list of parts) to plain text."""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, dict):
        return _content_to_text(content["text"]) if "text" in content else ""
    if isinstance(content, (list, tuple)):
        return "\n".join(t for t in (_content_to_text(part) for part in content) if t)
    return ""


def _build_conversation(message: Any, history: list[dict], system_prompt: str) -> list[dict]:
    turns = []
    for turn in history or []:
        if (turn.get("metadata") or {}).get("title"):
            continue  # skip the collapsible "thinking" bubbles; past reasoning is not fed back
        role = turn.get("role")
        text = _content_to_text(turn.get("content"))
        if role in ("user", "assistant") and text:
            if role == "assistant" and turns and turns[-1]["role"] == "assistant":
                turns[-1]["content"] += "\n" + text
            else:
                turns.append({"role": role, "content": text})
    turns.append({"role": "user", "content": _content_to_text(message)})
    system = [{"role": "system", "content": system_prompt.strip()}] if system_prompt and system_prompt.strip() else []
    return system + turns


def _tokenize(conversation: list[dict], enable_thinking: bool) -> torch.Tensor:
    return tokenizer.apply_chat_template(
        conversation,
        add_generation_prompt=True,
        enable_thinking=enable_thinking,
        return_tensors="pt",
        return_dict=True,
    )["input_ids"]


class _StopOnEvent(StoppingCriteria):
    """Stops generation when the user presses Stop (the streaming generator is closed)."""

    def __init__(self, event: Event):
        self.event = event

    def __call__(self, input_ids: torch.LongTensor, scores: torch.FloatTensor, **kwargs) -> torch.BoolTensor:
        return torch.full((input_ids.shape[0],), self.event.is_set(), dtype=torch.bool, device=input_ids.device)


def _gpu_duration(message, history, system_prompt=None, enable_thinking=True,
                  max_new_tokens=DEFAULT_MAX_NEW_TOKENS, *args, **kwargs) -> int:
    # Reserve GPU time proportional to the requested output length (smaller = better queue priority).
    return int(min(240, 30 + int(max_new_tokens) / 15))


def _render(text: str, thinking: bool) -> list[gr.ChatMessage] | str:
    if not thinking:
        return text.lstrip()
    if THINK_END in text:
        thought, answer = text.split(THINK_END, 1)
        status = "done"
    else:
        thought, answer, status = text, "", "pending"
    thought = thought.replace("<think>", "").strip()
    messages = [gr.ChatMessage(role="assistant", content=thought or "…",
                               metadata={"title": "💭 Thinking", "status": status})]
    if answer.strip():
        messages.append(gr.ChatMessage(role="assistant", content=answer.strip()))
    return messages


@spaces.GPU(duration=_gpu_duration, size="xlarge")
def chat(
    message: str,
    history: list[dict],
    system_prompt: str = "",
    enable_thinking: bool = True,
    max_new_tokens: int = DEFAULT_MAX_NEW_TOKENS,
    temperature: float = 0.6,
    top_p: float = 0.95,
    top_k: int = 20,
) -> Iterator[list[gr.ChatMessage] | str]:
    """Chat with Huihui-Qwen3.8-27B-abliterated and stream the reply.

    Args:
        message: The new user message.
        history: Previous turns as a list of {"role", "content"} messages.
        system_prompt: Optional system prompt that steers the assistant.
        enable_thinking: Let the model reason step by step before answering.
        max_new_tokens: Maximum number of tokens to generate (reasoning included).
        temperature: Sampling temperature; 0 means greedy decoding.
        top_p: Nucleus sampling probability mass.
        top_k: Number of highest-probability tokens considered when sampling.

    Returns:
        The assistant reply (and its reasoning when thinking is on), streamed as it is generated.
    """
    conversation = _build_conversation(message, history, system_prompt)
    input_ids = _tokenize(conversation, enable_thinking)

    # Drop the oldest turns (keeping the system prompt and the new message) until the prompt fits.
    first_turn = 1 if conversation[0]["role"] == "system" else 0
    trimmed = False
    while input_ids.shape[1] > MAX_INPUT_TOKENS and len(conversation) - first_turn > 1:
        del conversation[first_turn]
        input_ids = _tokenize(conversation, enable_thinking)
        trimmed = True
    if input_ids.shape[1] > MAX_INPUT_TOKENS:
        raise gr.Error(f"Your message is too long ({input_ids.shape[1]} tokens, limit {MAX_INPUT_TOKENS}).")
    if trimmed:
        gr.Warning(f"Older messages were dropped to fit the {MAX_INPUT_TOKENS}-token context limit.")

    input_ids = input_ids.to(model.device)
    stop_event = Event()
    streamer = TextIteratorStreamer(tokenizer, skip_prompt=True, skip_special_tokens=True, timeout=120.0)
    do_sample = temperature > 0
    generate_kwargs = dict(
        input_ids=input_ids,
        attention_mask=torch.ones_like(input_ids),
        streamer=streamer,
        max_new_tokens=int(max_new_tokens),
        do_sample=do_sample,
        stopping_criteria=StoppingCriteriaList([_StopOnEvent(stop_event)]),
    )
    if do_sample:
        generate_kwargs.update(temperature=float(temperature), top_p=float(top_p), top_k=int(top_k))

    thread = Thread(target=model.generate, kwargs=generate_kwargs, daemon=True)
    thread.start()
    output = ""
    try:
        for new_text in streamer:
            output += new_text
            yield _render(output, enable_thinking)
    finally:
        stop_event.set()  # stop generating if the user pressed Stop / closed the stream
        thread.join(timeout=10)


DESCRIPTION = f"""\
Chat with [`{MODEL_ID}`](https://huggingface.co/{MODEL_ID}), an *abliterated* (uncensored) version of
[Qwen3.8-27B](https://huggingface.co/Qwen/Qwen3.8-27B) — the original weights behind
[`{GGUF_ID}`](https://huggingface.co/{GGUF_ID}). Refusals were removed, so it may produce inaccurate or objectionable
content. Runs in bf16 on ZeroGPU; toggle **Thinking** in *Advanced settings*.
"""

CSS = """
.dark .gradio-container { color: var(--body-text-color); }
"""

demo = gr.ChatInterface(
    fn=chat,
    chatbot=gr.Chatbot(
        height=600,
        placeholder="<strong>Huihui Qwen3.8 27B — abliterated</strong><br>Ask me anything.",
    ),
    textbox=gr.Textbox(placeholder="Type a message…", autofocus=True, scale=7),
    additional_inputs=[
        gr.Textbox(label="System prompt", value="", lines=2),
        gr.Checkbox(label="Thinking (reason before answering)", value=True),
        gr.Slider(label="Max new tokens", minimum=128, maximum=MAX_MAX_NEW_TOKENS, step=128, value=DEFAULT_MAX_NEW_TOKENS),
        gr.Slider(label="Temperature", minimum=0.0, maximum=2.0, step=0.05, value=0.6),
        gr.Slider(label="Top-p", minimum=0.05, maximum=1.0, step=0.05, value=0.95),
        gr.Slider(label="Top-k", minimum=1, maximum=100, step=1, value=20),
    ],
    additional_inputs_accordion=gr.Accordion("Advanced settings", open=False),
    examples=[
        ["How many r's are in the word 'strawberry'? Explain your counting."],
        ["Write a short story about a lighthouse keeper who finds a message in a bottle."],
        ["Write a Python function that merges overlapping intervals, with a short explanation."],
        ["İstanbul'da ilk kez gelen biri için 3 günlük bir gezi planı hazırla."],
    ],
    cache_examples=True,
    cache_mode="lazy",
    title="Huihui Qwen3.8 27B (abliterated)",
    description=DESCRIPTION,
    api_name="chat",
    fill_height=True,
)

if __name__ == "__main__":
    demo.launch(theme=gr.themes.Citrus(), css=CSS, mcp_server=True)
