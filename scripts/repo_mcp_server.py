#!/usr/bin/env python3
"""Minimal stdio MCP server for a PocketQA corpus. No arbitrary file access."""
import argparse, json, re, sys
from pathlib import Path

def response(req_id, result=None, error=None):
    payload = {"jsonrpc": "2.0", "id": req_id}
    if error: payload["error"] = {"code": -32602, "message": error}
    else: payload["result"] = result
    print(json.dumps(payload), flush=True)

def text(value): return {"content": [{"type": "text", "text": value}]}

parser = argparse.ArgumentParser()
parser.add_argument("--corpus", required=True)
args = parser.parse_args()
root = Path(args.corpus).resolve()
manifest_path = root / "manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
allowed = {item["sourceKey"].replace("\\", "/") for item in manifest}

def safe_file(key):
    normalized = key.replace("\\", "/")
    if normalized not in allowed: raise ValueError("sourceKey is not in the corpus manifest")
    target = (root / normalized).resolve()
    if root not in target.parents: raise ValueError("sourceKey escapes corpus")
    return target

tools = [
    {"name":"search_source","description":"Search allowlisted source files","inputSchema":{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query"]}},
    {"name":"read_source","description":"Read an allowlisted source line range","inputSchema":{"type":"object","properties":{"sourceKey":{"type":"string"},"startLine":{"type":"integer"},"endLine":{"type":"integer"}},"required":["sourceKey"]}},
    {"name":"validate_patch","description":"Validate unified diff paths against the corpus","inputSchema":{"type":"object","properties":{"diff":{"type":"string"}},"required":["diff"]}},
]

for line in sys.stdin:
    try:
        req = json.loads(line); req_id = req.get("id"); method = req.get("method")
        if method == "initialize": response(req_id, {"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"pocketqa-repo","version":"0.1.0"}})
        elif method == "notifications/initialized": continue
        elif method == "tools/list": response(req_id, {"tools": tools})
        elif method == "tools/call":
            name = req.get("params",{}).get("name"); a = req.get("params",{}).get("arguments",{})
            if name == "search_source":
                terms = {t for t in re.split(r"\W+", a.get("query","").lower()) if len(t)>2}
                scored=[]
                for key in allowed:
                    body=safe_file(key).read_text(encoding="utf-8",errors="replace")[:512000].lower()
                    score=sum(body.count(t)+key.lower().count(t)*3 for t in terms)
                    if score: scored.append((score,key))
                result=[{"sourceKey":k,"score":s} for s,k in sorted(scored,reverse=True)[:min(int(a.get("limit",6)),20)]]
                response(req_id,text(json.dumps(result)))
            elif name == "read_source":
                lines=safe_file(a["sourceKey"]).read_text(encoding="utf-8",errors="replace").splitlines()
                start=max(1,int(a.get("startLine",1))); end=min(len(lines),int(a.get("endLine",start+199)),start+399)
                response(req_id,text("\n".join(f"{i}: {lines[i-1]}" for i in range(start,end+1))))
            elif name == "validate_patch":
                paths=re.findall(r"^(?:---|\+\+\+) [ab]/(.+)$",a.get("diff",""),re.M)
                valid=bool(paths) and all(p in allowed and ".." not in p for p in paths)
                response(req_id,text(json.dumps({"valid":valid,"paths":paths})))
            else: response(req_id,error="Unknown tool")
        else: response(req_id,error="Unknown method")
    except Exception as exc:
        response(locals().get("req_id"), error=str(exc)[:300])
