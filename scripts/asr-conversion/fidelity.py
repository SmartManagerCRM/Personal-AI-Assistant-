import sys, json, numpy as np, onnxruntime as ort, torch
from transformers import WhisperProcessor, WhisperForConditionalGeneration
hf, d, prefix, variant, N = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5])
suf = ".int8.onnx" if variant == "int8" else ".onnx"
proc = WhisperProcessor.from_pretrained(hf); m = WhisperForConditionalGeneration.from_pretrained(hf).eval()
gc = json.load(open(f"{hf}/generation_config.json")); sup = np.array(gc.get("suppress_tokens", []), dtype=np.int64); begin_sup = gc.get("begin_suppress_tokens") or []
enc = ort.InferenceSession(f"{d}/{prefix}-encoder{suf}", providers=["CPUExecutionProvider"]); dec = ort.InferenceSession(f"{d}/{prefix}-decoder{suf}", providers=["CPUExecutionProvider"])
L = m.config.decoder_layers; D = m.config.d_model; NCTX = m.config.max_target_positions
ar = proc.tokenizer.convert_tokens_to_ids("<|ar|>"); prompt = [50258, ar, 50359, 50363]
refs = [r for r in json.load(open("refs.json")) if r["dur"] <= 30][:N]
same = 0; maxdiff = 0
for r in refs:
    w = np.load(f"wav_{r['id']:03d}.npy"); f = proc(w, sampling_rate=16000, return_tensors="pt").input_features
    with torch.no_grad(): pt = m.generate(f, language="ar", task="transcribe", num_beams=1, do_sample=False, max_new_tokens=200)[0].tolist()
    ck, cv = enc.run(None, {"mel": f.numpy()})
    kc = np.zeros((L, 1, NCTX, D), np.float32); vc = kc.copy(); toks = np.array([prompt], np.int64); off = np.array([0], np.int64); out = []
    for step in range(200):
        logits, kc, vc = dec.run(None, {"tokens": toks, "in_n_layer_self_k_cache": kc, "in_n_layer_self_v_cache": vc, "n_layer_cross_k": ck, "n_layer_cross_v": cv, "offset": off})
        lg = logits[0, -1].copy(); lg[sup] = -1e9
        if step == 0: lg[begin_sup] = -1e9
        nxt = int(lg.argmax()); 
        if nxt == 50257: break
        out.append(nxt); off = off + toks.shape[1]; toks = np.array([[nxt]], np.int64)
    ptt = [t for t in pt if t not in prompt and t != 50257 and t < 50257]
    same += (out == ptt)
print(f"{prefix} {variant}: token-identical to PyTorch generate on {same}/{len(refs)} utterances")
