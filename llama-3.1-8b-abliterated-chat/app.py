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

MODEL_ID = "mlabonne/Meta-Llama-3.1-8B-Instruct-abliterated"

MAX_INPUT_TOKENS = int(os.getenv("MAX_INPUT_TOKENS", "8192"))
DEFAULT_MAX_NEW_TOKENS = 1024
MAX_MAX_NEW_TOKENS = 2048
DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model = AutoModelForCausalLM.from_pretrained(
    MODEL_ID,
    dtype=torch.bfloat16,
    attn_implementation="sdpa",
).to("cuda")
model.eval()


def _content_to_text(content: Any) -> str:
    """Flatten a Gradio message `content` (str, text part dict, or list of parts) to plain text."""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, dict):
        if "text" in content:
            return _content_to_text(content["text"])
        return ""  # files / components are not supported by this text-only model
    if isinstance(content, (list, tuple)):
        return "\n".join(t for t in (_content_to_text(part) for part in content) if t)
    return ""


def _build_conversation(message: Any, history: list[dict], system_prompt: str) -> list[dict]:
    turns = []
    for turn in history or []:
        role = turn.get("role")
        text = _content_to_text(turn.get("content"))
        if role in ("user", "assistant") and text:
            turns.append({"role": role, "content": text})
    turns.append({"role": "user", "content": _content_to_text(message)})

    system = [{"role": "system", "content": system_prompt.strip()}] if system_prompt and system_prompt.strip() else []
    return system + turns


def _tokenize(conversation: list[dict]) -> torch.Tensor:
    return tokenizer.apply_chat_template(
        conversation,
        add_generation_prompt=True,
        return_tensors="pt",
        return_dict=True,
    )["input_ids"]


class _StopOnEvent(StoppingCriteria):
    """Stops generation when the user presses Stop (the streaming generator is closed)."""

    def __init__(self, event: Event):
        self.event = event

    def __call__(self, input_ids: torch.LongTensor, scores: torch.FloatTensor, **kwargs) -> torch.BoolTensor:
        return torch.full((input_ids.shape[0],), self.event.is_set(), dtype=torch.bool, device=input_ids.device)


def _gpu_duration(message, history, system_prompt=None, max_new_tokens=DEFAULT_MAX_NEW_TOKENS, *args, **kwargs) -> int:
    # Reserve GPU time proportional to the requested output length (smaller = better queue priority).
    return int(min(120, 15 + int(max_new_tokens) / 25))


@spaces.GPU(duration=_gpu_duration)
def chat(
    message: str,
    history: list[dict],
    system_prompt: str = DEFAULT_SYSTEM_PROMPT,
    max_new_tokens: int = DEFAULT_MAX_NEW_TOKENS,
    temperature: float = 0.6,
    top_p: float = 0.9,
    top_k: int = 50,
    repetition_penalty: float = 1.0,
) -> Iterator[str]:
    """Chat with Meta-Llama-3.1-8B-Instruct-abliterated and stream the reply.

    Args:
        message: The new user message.
        history: Previous turns as a list of {"role", "content"} messages.
        system_prompt: Optional system prompt that steers the assistant.
        max_new_tokens: Maximum number of tokens to generate.
        temperature: Sampling temperature; 0 means greedy decoding.
        top_p: Nucleus sampling probability mass.
        top_k: Number of highest-probability tokens considered when sampling.
        repetition_penalty: Penalty applied to already generated tokens (1.0 = none).

    Returns:
        The assistant reply, streamed as it is generated.
    """
    conversation = _build_conversation(message, history, system_prompt)
    input_ids = _tokenize(conversation)

    # Drop the oldest turns (keeping the system prompt and the new message) until the prompt fits.
    first_turn = 1 if conversation[0]["role"] == "system" else 0
    trimmed = False
    while input_ids.shape[1] > MAX_INPUT_TOKENS and len(conversation) - first_turn > 1:
        del conversation[first_turn]
        input_ids = _tokenize(conversation)
        trimmed = True
    if input_ids.shape[1] > MAX_INPUT_TOKENS:
        raise gr.Error(f"Your message is too long ({input_ids.shape[1]} tokens, limit {MAX_INPUT_TOKENS}).")
    if trimmed:
        gr.Warning(f"Older messages were dropped to fit the {MAX_INPUT_TOKENS}-token context limit.")

    input_ids = input_ids.to(model.device)
    stop_event = Event()
    streamer = TextIteratorStreamer(tokenizer, skip_prompt=True, skip_special_tokens=True, timeout=60.0)
    do_sample = temperature > 0
    generate_kwargs = dict(
        input_ids=input_ids,
        attention_mask=torch.ones_like(input_ids),
        streamer=streamer,
        max_new_tokens=int(max_new_tokens),
        do_sample=do_sample,
        repetition_penalty=float(repetition_penalty),
        stopping_criteria=StoppingCriteriaList([_StopOnEvent(stop_event)]),
        pad_token_id=tokenizer.eos_token_id,
    )
    if do_sample:
        generate_kwargs.update(temperature=float(temperature), top_p=float(top_p), top_k=int(top_k))

    thread = Thread(target=model.generate, kwargs=generate_kwargs, daemon=True)
    thread.start()
    output = ""
    try:
        for new_text in streamer:
            output += new_text
            yield output
    finally:
        stop_event.set()  # stop generating if the user pressed Stop / closed the stream
        thread.join(timeout=10)


DESCRIPTION = f"""\
Chat with [`{MODEL_ID}`](https://huggingface.co/{MODEL_ID}), an *abliterated* version of
[Llama 3.1 8B Instruct](https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct): its refusal direction was removed,
so it is uncensored and may produce inaccurate or objectionable content. Running in bf16 on ZeroGPU.
Learn more about abliteration in [this blog post](https://huggingface.co/blog/mlabonne/abliteration).
"""

CSS = """
.dark .gradio-container { color: var(--body-text-color); }
"""

demo = gr.ChatInterface(
    fn=chat,
    chatbot=gr.Chatbot(
        height=560,
        placeholder="<strong>Llama 3.1 8B Instruct — abliterated</strong><br>Ask me anything.",
    ),
    textbox=gr.Textbox(placeholder="Type a message…", autofocus=True, scale=7),
    additional_inputs=[
        gr.Textbox(label="System prompt", value=DEFAULT_SYSTEM_PROMPT, lines=2),
        gr.Slider(label="Max new tokens", minimum=64, maximum=MAX_MAX_NEW_TOKENS, step=64, value=DEFAULT_MAX_NEW_TOKENS),
        gr.Slider(label="Temperature", minimum=0.0, maximum=2.0, step=0.05, value=0.6),
        gr.Slider(label="Top-p", minimum=0.05, maximum=1.0, step=0.05, value=0.9),
        gr.Slider(label="Top-k", minimum=1, maximum=200, step=1, value=50),
        gr.Slider(label="Repetition penalty", minimum=1.0, maximum=2.0, step=0.05, value=1.0),
    ],
    additional_inputs_accordion=gr.Accordion("Advanced settings", open=False),
    examples=[
        ["Write a short story about a lighthouse keeper who finds a message in a bottle."],
        ["Explain how self-attention works in transformers, using a simple analogy."],
        ["Write a Python function that checks whether a string is a palindrome, then explain it."],
        ["Plan a 3-day itinerary for a first-time visit to Istanbul."],
    ],
    cache_examples=True,
    cache_mode="lazy",
    title="Llama 3.1 8B Instruct (abliterated)",
    description=DESCRIPTION,
    api_name="chat",
    fill_height=True,
)

if __name__ == "__main__":
    demo.launch(theme=gr.themes.Citrus(), css=CSS, mcp_server=True)
