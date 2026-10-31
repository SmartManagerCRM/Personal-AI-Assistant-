"""Convert a HuggingFace Whisper checkpoint to the sherpa-onnx Whisper layout (encoder.onnx + decoder.onnx + tokens.txt), fp32 and INT8.
Usage: export_sherpa.py <hf_dir> <out_dir> <model_type_name> <official_sherpa_dir>"""
import sys, os, json, shutil, torch, torch.nn as nn, torch.nn.functional as F, onnx
from safetensors.torch import load_file
hf, out, name, official = sys.argv[1:5]
os.makedirs(out, exist_ok=True)
cfg = json.load(open(f"{hf}/config.json"))
sd = {k.replace("model.", "", 1) if k.startswith("model.") else k: v for k, v in load_file(f"{hf}/model.safetensors").items()}
D, H = cfg["d_model"], cfg["encoder_attention_heads"]; L_E, L_D = cfg["encoder_layers"], cfg["decoder_layers"]; V = cfg["vocab_size"]; N_CTX = cfg["max_target_positions"]; N_AUDIO = cfg["max_source_positions"]; N_MELS = cfg["num_mel_bins"]
assert cfg["encoder_attention_heads"] == cfg["decoder_attention_heads"]
CUR = None
def make_params(): return nn.ParameterDict({k.replace(".", "__"): nn.Parameter(v.float(), requires_grad=False) for k, v in sd.items()})
def W(k): return CUR.p[k.replace(".", "__")]
def lin(x, p, bias=True): return F.linear(x, W(p + ".weight"), W(p + ".bias") if bias and (p + ".bias") in sd else None)
def ln(x, p): return F.layer_norm(x, (D,), W(p + ".weight"), W(p + ".bias"), 1e-5)
def split(x): b, n, _ = x.shape; return x.view(b, n, H, D // H).transpose(1, 2)
def attend(q, k, v, mask=None):
    q = split(q) * (D // H) ** -0.5; k = split(k); v = split(v)
    s = q @ k.transpose(-1, -2)
    if mask is not None: s = s + mask
    return (F.softmax(s, dim=-1) @ v).transpose(1, 2).reshape(q.shape[0], q.shape[2], D)

class Encoder(nn.Module):
    def __init__(self): super().__init__(); self.p = make_params()
    def forward(self, mel):
        global CUR; CUR = self
        x = F.gelu(F.conv1d(mel, W("encoder.conv1.weight"), W("encoder.conv1.bias"), padding=1))
        x = F.gelu(F.conv1d(x, W("encoder.conv2.weight"), W("encoder.conv2.bias"), stride=2, padding=1)).permute(0, 2, 1)
        x = x + W("encoder.embed_positions.weight")[: x.shape[1]]
        for i in range(L_E):
            p = f"encoder.layers.{i}"; h = ln(x, p + ".self_attn_layer_norm")
            x = x + lin(attend(lin(h, p + ".self_attn.q_proj"), lin(h, p + ".self_attn.k_proj", False), lin(h, p + ".self_attn.v_proj")), p + ".self_attn.out_proj")
            x = x + lin(F.gelu(lin(ln(x, p + ".final_layer_norm"), p + ".fc1")), p + ".fc2")
        x = ln(x, "encoder.layer_norm")
        ks = [lin(x, f"decoder.layers.{i}.encoder_attn.k_proj", False) for i in range(L_D)]
        vs = [lin(x, f"decoder.layers.{i}.encoder_attn.v_proj") for i in range(L_D)]
        return torch.stack(ks), torch.stack(vs)

class Decoder(nn.Module):
    def __init__(self): super().__init__(); self.p = make_params()
    def forward(self, tokens, k_cache, v_cache, cross_k, cross_v, offset):
        global CUR; CUR = self
        n = tokens.shape[1]; o = offset[0]
        pos = o + torch.arange(n, device=tokens.device)
        x = F.embedding(tokens, W("decoder.embed_tokens.weight")) + W("decoder.embed_positions.weight")[pos]
        kpos = torch.arange(N_CTX, device=tokens.device)
        mask = torch.where(kpos[None, :] <= pos[:, None], 0.0, float("-inf"))[None, None]   # causal over the fixed-size cache
        new_k, new_v = [], []
        for i in range(L_D):
            p = f"decoder.layers.{i}"; h = ln(x, p + ".self_attn_layer_norm")
            k = k_cache[i].index_copy(1, pos, lin(h, p + ".self_attn.k_proj", False)); v = v_cache[i].index_copy(1, pos, lin(h, p + ".self_attn.v_proj"))
            new_k.append(k); new_v.append(v)
            x = x + lin(attend(lin(h, p + ".self_attn.q_proj"), k, v, mask), p + ".self_attn.out_proj")
            h = ln(x, p + ".encoder_attn_layer_norm")
            x = x + lin(attend(lin(h, p + ".encoder_attn.q_proj"), cross_k[i], cross_v[i]), p + ".encoder_attn.out_proj")
            x = x + lin(F.gelu(lin(ln(x, p + ".final_layer_norm"), p + ".fc1")), p + ".fc2")
        x = ln(x, "decoder.layer_norm")
        return x @ W("decoder.embed_tokens.weight").T, torch.stack(new_k), torch.stack(new_v)

enc_path, dec_path = f"{out}/{name}-encoder.onnx", f"{out}/{name}-decoder.onnx"
T = N_AUDIO * 2
mel = torch.randn(1, N_MELS, T)
torch.onnx.export(Encoder(), (mel,), enc_path, input_names=["mel"], output_names=["n_layer_cross_k", "n_layer_cross_v"], dynamic_axes={"mel": {0: "n_audio", 2: "T"}, "n_layer_cross_k": {1: "n_audio", 2: "T"}, "n_layer_cross_v": {1: "n_audio", 2: "T"}}, opset_version=17, dynamo=False)
with torch.no_grad(): ck, cv = Encoder()(mel)
toks = torch.tensor([[50258, 50272, 50359]]); kc = torch.zeros(L_D, 1, N_CTX, D); vc = torch.zeros(L_D, 1, N_CTX, D)
torch.onnx.export(Decoder(), (toks, kc, vc, ck, cv, torch.tensor([0])), dec_path, input_names=["tokens", "in_n_layer_self_k_cache", "in_n_layer_self_v_cache", "n_layer_cross_k", "n_layer_cross_v", "offset"],
    output_names=["logits", "out_n_layer_self_k_cache", "out_n_layer_self_v_cache"], dynamic_axes={"tokens": {0: "n_audio", 1: "n_tokens"}, "in_n_layer_self_k_cache": {1: "n_audio"}, "in_n_layer_self_v_cache": {1: "n_audio"},
    "n_layer_cross_k": {1: "n_audio", 2: "T"}, "n_layer_cross_v": {1: "n_audio", 2: "T"}, "logits": {0: "n_audio", 1: "n_tokens"}, "out_n_layer_self_k_cache": {1: "n_audio"}, "out_n_layer_self_v_cache": {1: "n_audio"}}, opset_version=17, dynamo=False)

# metadata: copy the official sherpa model's multilingual tokenizer metadata, replace the dimensions
off = onnx.load(f"{official}/base-encoder.int8.onnx", load_external_data=False)
meta = {kv.key: kv.value for kv in off.metadata_props if kv.key != "onnx.infer"}
meta.update(model_type=f"whisper-{name}", maintainer="smartmanager-conversion", n_mels=str(N_MELS), n_audio_ctx=str(N_AUDIO), n_audio_state=str(D), n_audio_head=str(H), n_audio_layer=str(L_E), n_vocab=str(V),
            n_text_ctx=str(N_CTX), n_text_state=str(D), n_text_head=str(cfg["decoder_attention_heads"]), n_text_layer=str(L_D))
for p in (enc_path, dec_path):
    m = onnx.load(p)
    for k, v in meta.items(): e = m.metadata_props.add(); e.key = k; e.value = v
    onnx.save(m, p)
shutil.copy(f"{official}/base-tokens.txt", f"{out}/{name}-tokens.txt")

from onnxruntime.quantization import quantize_dynamic, QuantType
for p in (enc_path, dec_path):
    quantize_dynamic(p, p.replace(".onnx", ".int8.onnx"), op_types_to_quantize=["MatMul"], weight_type=QuantType.QUInt8)
for f in sorted(os.listdir(out)): print(f, os.path.getsize(f"{out}/{f}") // 1000, "KB")
