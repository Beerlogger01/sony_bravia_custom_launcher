#!/usr/bin/env python3
"""Fixture for the PowerShell transaction tests; models only their supported shell commands."""
import json
import os
import sys
from pathlib import Path
db=Path(os.environ["BRAVIA_FAKE_STATE"])
state=json.loads(db.read_text())
args=sys.argv[1:]
if args[:2]==["-s","192.0.2.40:5555"]:args=args[2:]
cmd=" ".join(args)
if cmd.startswith("connect "):print("connected")
elif cmd=="get-state": print("device")
elif cmd.startswith("install -r "):
    name=Path(args[-1]).stem
    if state.get("fail_install")==name:
        print("Failure [fixture]");sys.exit(1)
    state["files"][name]=Path(args[-1]).read_bytes().hex()
    print("Success")
elif cmd.startswith("exec-out cat "):
    name=args[-1].split("/")[-2].split(".")[-1]
    sys.stdout.buffer.write(bytes.fromhex(state["files"][name]))
elif cmd.startswith(("shell stat ","shell sha256sum ")):
    import hashlib
    data=bytes.fromhex(state["files"][args[-1].split("/")[-2].split(".")[-1]])
    print(len(data) if args[1]=="stat" else hashlib.sha256(data).hexdigest())
elif cmd.startswith("shell cp "):
    state["temp"]={args[-1]:args[-2]}
elif cmd.startswith("shell rm -f "):
    state["temp"].pop(args[-1])
elif cmd.startswith("pull "):
    if args[1].startswith("/data/app"):
        sys.exit(1)
    args[1]=state["temp"][args[1]]
    name=args[1].split("/")[-2].split(".")[-1]
    Path(args[2]).write_bytes(bytes.fromhex(state["files"][name]))
elif cmd.startswith("shell dumpsys package house.bravia."):
    print("versionCode="+("13" if args[-1].endswith("home") else "1"))
elif cmd.startswith("shell pm path "):
    print("package:/data/app/"+args[-1]+"/base.apk")
elif cmd.startswith("install "):
    name=Path(args[-1]).stem
    assert not state["installed"] or name=="home"
    state["installed"].append("house.bravia."+name)
    print("Success")
elif cmd=="shell getprop ro.build.version.sdk": print("24")
elif cmd=="shell getprop ro.product.manufacturer":print("Sony")
elif cmd=="shell am get-current-user":print(0)
elif cmd.startswith("shell cmd package resolve-activity"):print(state["home"])
elif cmd=="shell settings get secure android_id":print("0000000000000001")
elif cmd.startswith("shell pm list packages house."):
    if args[-1] in state["installed"]: print("package:"+args[-1])
elif cmd.startswith("shell am start -W -n "):print("Status: ok")
elif cmd=="shell cmd package help":print("set-home-activity")
elif cmd.startswith("shell am broadcast "):
    state["enrolled"]=args[-1]=="house.bravia.ENROLL"
    print("Broadcast completed: result=-1")
elif cmd.startswith("shell cmd package set-home-activity "):
    if not state["refuse"] or "house." not in args[-1]:state["home"]=args[-1]
elif cmd=="shell dumpsys activity activities":print("mFocusedActivity "+state["home"])
elif cmd=="shell input keyevent 3":pass
elif cmd.startswith(("shell settings list ","shell dumpsys ","shell pm list packages","shell getprop")):print("fixture")
else:raise ValueError(cmd)
state["commands"].append(cmd)
db.write_text(json.dumps(state))
