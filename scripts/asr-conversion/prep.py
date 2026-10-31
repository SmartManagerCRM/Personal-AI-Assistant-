import pyarrow.parquet as pq, soundfile as sf, io, numpy as np, json, re, unicodedata
from scipy.signal import resample_poly
t=pq.read_table("tunswitch/data/validation-00000-of-00001.parquet").to_pylist()
out=[]
for i,r in enumerate(t):
    a=r["file_name"]; wav,sr=sf.read(io.BytesIO(a["bytes"]),dtype="float32")
    if wav.ndim>1: wav=wav.mean(1)
    if sr!=16000: g=np.gcd(sr,16000); wav=resample_poly(wav,16000//g,sr//g).astype("float32")
    np.save(f"wav_{i:03d}.npy",wav)
    out.append({"id":i,"dur":len(wav)/16000,"ref":r["original_transcription"]})
json.dump(out,open("refs.json","w"),ensure_ascii=False)
d=[o["dur"] for o in out]; print(len(out),"utts, total %.1f min, mean %.1fs, max %.1fs"%(sum(d)/60,np.mean(d),max(d)))
for o in out[:6]: print(round(o["dur"],1), o["ref"])
