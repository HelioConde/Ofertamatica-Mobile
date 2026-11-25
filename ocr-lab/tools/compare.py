"""CPU-only comparison on the exact images exported by Android instrumentation.
Expected dates are used only to score results, never to select candidates.
This is an experiment, not an Android dependency or release gate for real photos.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import cv2
import numpy as np
from rapidocr_onnxruntime import RapidOCR

ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "benchmark-images"
EXPECTED = {f"synthetic-{i+1}": d for i,d in enumerate([
    "2027-06-30","2027-04-24","2027-01-21","2027-05-15",
    "2027-09-28","2027-08-20","2027-06-08","2027-05-12"])}
EXPECTED.update({f"negative-{i}": None for i in range(1,5)})
BUILD = ROOT / "parser-cli"
BUILD.mkdir(exist_ok=True)
subprocess.run(["javac","-d",str(BUILD),str(ROOT/"app/src/main/java/br/primor/ocrlab/Expiry.java"),str(ROOT/"tools/ExpiryCli.java")],check=True)

def parse(text):
    return subprocess.run(["java","-cp",str(BUILD),"ExpiryCli"],input=text,text=True,capture_output=True,check=True).stdout.strip() or None

def normalize(img):
    gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
    if np.median(gray) < 100:
        gray = 255-gray
    _,ink = cv2.threshold(gray,0,255,cv2.THRESH_BINARY_INV+cv2.THRESH_OTSU)
    return ink

def variants(img):
    yield "raw",img
    ink=normalize(img)
    for n in (5,7):
        kernel=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(n,n))
        restored=cv2.morphologyEx(ink,cv2.MORPH_CLOSE,kernel)
        yield f"close-{n}",cv2.resize(255-restored,None,fx=1.5,fy=1.5,interpolation=cv2.INTER_CUBIC)
    smooth=cv2.GaussianBlur(ink,(5,5),0)
    _,smooth=cv2.threshold(smooth,40,255,cv2.THRESH_BINARY)
    yield "soft-ink",255-smooth

def outcome(actual,expected):
    return "OK" if actual==expected else "NO_READ" if actual is None else "WRONG_DATE"

def safe_consensus(values):
    if "BLOCK" in values:return None
    dates=[v for v in values if v]
    return dates[0] if len(dates)>=2 and len(set(dates))==1 else None

def main():
    cv2.setNumThreads(2)
    engine=RapidOCR()
    ml={}
    for line in (ROOT/"ocr-log.txt").read_text().splitlines():
        m=re.search(r"(synthetic-\d+|negative-\d+) FINAL expected=\S+ actual=(\S+) status=\S+ blocked=(true|false) total_ms=(\d+)",line)
        if m:
            ml[m[1]]={"actual":None if m[2]=="null" else m[2],"blocked":m[3]=="true","ms":int(m[4])}
    if set(ml)!=set(EXPECTED):
        raise RuntimeError(f"Missing ML Kit benchmark results: {set(EXPECTED)-set(ml)}")
    report={}
    for name,expected in EXPECTED.items():
        image=cv2.imread(str(IMAGES/f"{name}.png"))
        if image is None:raise RuntimeError(f"Missing image {name}")
        if name=="synthetic-3":image=cv2.rotate(image,cv2.ROTATE_90_COUNTERCLOCKWISE)
        attempts=[]
        for variant,img in variants(image):
            start=time.perf_counter()
            if img.ndim==2:img=cv2.cvtColor(img,cv2.COLOR_GRAY2BGR)
            result,_=engine(img)
            ms=round((time.perf_counter()-start)*1000)
            text="\n".join(str(row[1]) for row in (result or []))
            parsed=parse(text)
            attempts.append({"variant":variant,"text":text,"date":parsed,"ms":ms})
        secondary=safe_consensus([a["date"] for a in attempts])
        # Never replace an existing ML Kit date or override explicit ambiguity.
        fused=None if ml[name]["blocked"] else (ml[name]["actual"] or secondary)
        report[name]={"expected":expected,"mlkit":ml[name],"rapidocr":secondary,"combined":fused,"combined_status":outcome(fused,expected),"attempts":attempts}
        print(name,json.dumps(report[name],ensure_ascii=False),flush=True)
    totals={}
    for method in ("mlkit","rapidocr","combined"):
        counts={"correct":0,"no_read":0,"wrong":0,"negative_false_positive":0}
        for name,row in report.items():
            actual=row[method]["actual"] if method=="mlkit" else row[method]
            if name.startswith("negative-"):
                counts["negative_false_positive"]+=int(actual is not None)
            elif actual==row["expected"]:counts["correct"]+=1
            elif actual is None:counts["no_read"]+=1
            else:counts["wrong"]+=1
        totals[method]=counts
    print("COMPARISON",json.dumps(totals),flush=True)
    (ROOT/"comparison.json").write_text(json.dumps({"samples":report,"totals":totals},indent=2,ensure_ascii=False))
    combined=totals["combined"]
    # Experimental engine may fail; keep the validated primary baseline and safety requirements.
    if combined["correct"]<7 or combined["wrong"] or combined["negative_false_positive"]:
        raise SystemExit("Combined regression or false positive")

if __name__=="__main__":
    main()
