import re, unicodedata
_dia=re.compile("[ً-ٰٟـۖ-ۭ]")
def norm(s):
    s=unicodedata.normalize("NFKC",s).lower()
    s=_dia.sub("",s)
    for a,b in [("أ","ا"),("إ","ا"),("آ","ا"),("ى","ي"),("ة","ه"),("ؤ","و"),("ئ","ي"),("گ","ق"),("ڨ","ق")]: s=s.replace(a,b)
    s=re.sub(r"[٠-٩]",lambda m:str(ord(m.group())-0x660),s)
    s=re.sub(r"[^\w\s]"," ",s.replace("'"," ").replace("’"," "))   # drop punctuation, split l'usage → l usage
    s=re.sub(r"﻿|_"," ",s)
    return re.sub(r"\s+"," ",s).strip()
