import sys, json, time, numpy as np, jiwer, sherpa_onnx, resource
from norm import norm
name, d, prefix, lang, variant = sys.argv[1:6]; limit = int(sys.argv[6]) if len(sys.argv) > 6 else 10**9
suf = ".int8.onnx" if variant == "int8" else ".onnx"
t0 = time.time()
r = sherpa_onnx.OfflineRecognizer.from_whisper(encoder=f"{d}/{prefix}-encoder{suf}", decoder=f"{d}/{prefix}-decoder{suf}", tokens=f"{d}/{prefix}-tokens.txt",
        language="" if lang == "auto" else lang, task="transcribe", num_threads=4, decoding_method="greedy_search", provider="cpu")
load_s = time.time() - t0
refsfile = sys.argv[7] if len(sys.argv) > 7 else "refs.json"; pref = sys.argv[8] if len(sys.argv) > 8 else "wav"
refs = [x for x in json.load(open(refsfile)) if x["dur"] <= 30][:limit]
hyps, lat = [], []
for x in refs:
    w = np.load(f"{pref}_{x['id']:03d}.npy"); t = time.time()
    s = r.create_stream(); s.accept_waveform(16000, w); r.decode_stream(s)
    lat.append(time.time() - t); hyps.append(s.result.text)
R = [norm(x["ref"]) for x in refs]; H = [norm(h) for h in hyps]; keep = [i for i, x in enumerate(R) if x]
wer = jiwer.wer([R[i] for i in keep], [H[i] for i in keep]); cer = jiwer.cer([R[i] for i in keep], [H[i] for i in keep])
dur = sum(x["dur"] for x in refs)
res = {"set": pref, "name": name, "engine": f"sherpa-onnx-{variant}", "lang": lang, "n": len(keep), "wer": round(wer * 100, 1), "cer": round(cer * 100, 1), "rtf": round(sum(lat) / dur, 3), "mean_lat_s": round(float(np.mean(lat)), 2),
       "load_s": round(load_s, 2), "peak_rss_mb": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024}
print(json.dumps(res)); json.dump({"summary": res, "hyps": hyps}, open(f"hyp_{name}_{variant}_{pref}.json", "w"), ensure_ascii=False)
