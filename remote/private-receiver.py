#!/usr/bin/env python3
"""Restricted CleanYourTV receiver. No arbitrary command execution."""
import base64, json, os, pathlib, subprocess, time, urllib.request, urllib.error, datetime
TOKEN_FILE = pathlib.Path.home()/".config/cleanyourtv/github_token"
STATE_FILE = pathlib.Path.home()/".cleanyourtv-last-id"
REPO = "Serega7920/cleanyourtv"
HOST = "192.168.0.15:5555"
API = "https://api.github.com/repos/"+REPO+"/contents/"
ALLOWED = {
 "ram_status": ["shell","free","-m"],
 "storage_status": ["shell","df","-h","/data"],
 "launcher_status": ["shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME"],
 "start_clean_launcher": ["shell","am","start","-n","com.gathin.tvhome/.MainActivity"]
}
def api(method,path,body=None):
    token=TOKEN_FILE.read_text().strip()
    data=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(API+path,data=data,method=method,headers={
      "Authorization":"Bearer "+token,
      "Accept":"application/vnd.github+json",
      "X-GitHub-Api-Version":"2022-11-28",
      "User-Agent":"CleanYourTV-Termux",
      **({"Content-Type":"application/json"} if body is not None else {})
    })
    with urllib.request.urlopen(req,timeout=20) as response:
        return json.load(response)
def get_file(path):
    obj=api("GET",path+"?ref=main")
    return base64.b64decode(obj["content"]).decode(),obj["sha"]
def put_status(status):
    path="remote/status.json"
    sha=None
    try: _,sha=get_file(path)
    except urllib.error.HTTPError as e:
        if e.code != 404: raise
    data={"message":"Update CleanYourTV diagnostic result","content":base64.b64encode((json.dumps(status,ensure_ascii=False,indent=2)+"\n").encode()).decode(),"branch":"main"}
    if sha: data["sha"]=sha
    api("PUT",path,data)
def main():
    if not TOKEN_FILE.is_file(): raise SystemExit("Missing local GitHub token")
    subprocess.run(["adb","connect",HOST],capture_output=True,text=True,timeout=15)
    print("CleanYourTV private GitHub receiver started",flush=True)
    while True:
        try:
            raw,_=get_file("remote/command.json")
            cmd=json.loads(raw)
            ident=cmd.get("id")
            action=cmd.get("action")
            if type(ident) is not int or ident<1 or action not in (set(ALLOWED)|{"noop"}):
                raise ValueError("Invalid command")
            last=int(STATE_FILE.read_text()) if STATE_FILE.exists() else 0
            if ident>last:
                print(f"Action {action} id={ident}",flush=True)
                if action=="noop":
                    output="No operation"; code=0
                else:
                    p=subprocess.run(["adb","-s",HOST]+ALLOWED[action],capture_output=True,text=True,timeout=25)
                    output=(p.stdout+"\n"+p.stderr).strip()[:5000]; code=p.returncode
                status={"id":ident,"action":action,"exit_code":code,"output":output,"timestamp_utc":datetime.datetime.now(datetime.timezone.utc).isoformat()}
                put_status(status)
                STATE_FILE.write_text(str(ident))
                print("Result uploaded to private GitHub",flush=True)
        except Exception as exc:
            print("Receiver error:",str(exc)[:250],flush=True)
        time.sleep(30)
if __name__=="__main__": main()
