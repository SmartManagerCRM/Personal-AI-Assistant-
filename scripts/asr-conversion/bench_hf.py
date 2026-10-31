import sys, json, time, numpy as np, torch, jiwer
from transformers import WhisperForConditionalGeneration, WhisperProcessor
from norm import norm
name, path = sys.argv[1], sys.argv[2]
lang = sys.argv[3] if len(sys.argv)>3 else "ar"
torch.set_num_threads(4)
refs=[r for r in json.load(open("refs.json")) if r["dur"]<=30]
proc=WhisperProcessor.from_pretrained(path); m=WhisperForConditionalGeneration.from_pretrained(path).eval()
hyps=[];lat=[]
for r in refs:
    w=np.load(f"wav_{r['id']:03d}.npy")
    f=proc(w,sampling_rate=16000,return_tensors="pt").input_features
    t0=time.time()
    with torch.no_grad(): ids=m.generate(f,language=lang,task="transcribe",num_beams=1,do_sample=False,max_new_tokens=200)
    lat.append(time.time()-t0); hyps.append(proc.batch_decode(ids,skip_special_tokens=True)[0])
R=[norm(r["ref"]) for r in refs]; H=[norm(h) for h in hyps]
keep=[i for i,x in enumerate(R) if x]
wer=jiwer.wer([R[i] for i in keep],[H[i] for i in keep]); cer=jiwer.cer([R[i] for i in keep],[H[i] for i in keep])
dur=sum(r["dur"] for r in refs)
res={"name":name,"engine":"pytorch-fp32","n":len(keep),"wer":round(wer*100,1),"cer":round(cer*100,1),"rtf":round(sum(lat)/dur,3),"mean_lat_s":round(float(np.mean(lat)),2)}
print(json.dumps(res)); json.dump({"summary":res,"hyps":hyps},open(f"hyp_{name}_pt.json","w"),ensure_ascii=False)
