import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
HERE=Path(__file__).resolve().parent

POWERSHELL = os.environ.get("BRAVIA_TEST_POWERSHELL", "pwsh")

def fixture_adb(folder):
    if os.name != "nt":
        target = folder / "adb"
        shutil.copy(HERE / "fake_adb.py", target)
        target.chmod(0o700)
        return target
    # A native entry point is needed to exercise ProcessStartInfo and binary stdout on 5.1.
    # The distlib console launcher passes argv to the embedded __main__ on Windows.
    from distlib.scripts import ScriptMaker
    dist = folder / "fixture"
    dist.mkdir()
    script = dist / "adb"
    script.write_bytes(b"#!python\n" + (HERE / "fake_adb.py").read_bytes())
    maker = ScriptMaker(str(dist), str(folder))
    maker.executable = sys.executable
    maker.make("adb")
    return folder / "adb.exe"

@unittest.skipUnless(shutil.which(POWERSHELL),"PowerShell required")
class WindowsScripts(unittest.TestCase):
    def trial(self,refuse):
        stock="stock.fixture/.Home"
        with tempfile.TemporaryDirectory() as temp:
            d=Path(temp);scripts=d/"windows";shutil.copytree(HERE.parent/"windows",scripts)
            fake=fixture_adb(d)
            apk=d/"apk";apk.mkdir()
            digest=hashlib.sha256(b"fixture").hexdigest()
            for n in ["home","panel"]:(apk/(n+".apk")).write_bytes(b"fixture")
            (apk/"SHA256SUMS").write_text("\n".join(f"{digest}  {n}.apk" for n in ["home","panel"]))
            state=d/"state"; state.write_text(json.dumps(dict(home=stock,enrolled=False,refuse=refuse,installed=[],commands=[])))
            env=dict(os.environ,BRAVIA_TV="192.0.2.40:5555",BRAVIA_ADB=str(fake),LOCALAPPDATA=str(d/"backups"),BRAVIA_FAKE_STATE=str(state))
            def run(code,ok=True):
                proc=subprocess.run([POWERSHELL,"-NoProfile","-NonInteractive","-Command","$ErrorActionPreference='Stop'\n"+code],
                                    cwd=scripts,env=env,capture_output=True,text=True)
                self.assertEqual(proc.returncode==0,ok,proc.stdout+proc.stderr)
            run("./install.ps1")
            s=json.loads(state.read_text())
            self.assertEqual(s["home"],stock);self.assertFalse(s["enrolled"])
            self.assertLess(s["commands"].index("shell settings list global"),next(i for i,v in enumerate(s["commands"]) if v.startswith("install ")))
            backup=next((d/"backups").rglob("home.json")).parent
            args=f"-Backup '{backup}'"
            run("function Read-Host { 'NO' }; ./install.ps1 -SetHome "+args)
            self.assertFalse(json.loads(state.read_text())["enrolled"])
            run("function Read-Host { 'SET HOME' }; ./install.ps1 -SetHome "+args,not refuse)
            self.assertEqual(json.loads(state.read_text())["enrolled"],not refuse)
            run("./restore.ps1 "+args)
            s=json.loads(state.read_text());self.assertEqual(s["home"],stock);self.assertFalse(s["enrolled"])
            self.assertFalse(any("disable-user" in cmd for cmd in s["commands"]))
    def test_update_preserves_home_and_retries(self):
        stock="com.google.android.leanbacklauncher/.MainActivity"
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); scripts=root/"windows"; shutil.copytree(HERE.parent/"windows",scripts)
            adb=fixture_adb(root)
            update=scripts/"update.ps1"
            old=hashlib.sha256(b"old").hexdigest()
            update.write_text(update.read_text().replace("41079d682bd58ec973b5bd46021a1743119ad4fa02e8b7f2b8932651a863559e",old)
                              .replace("8e06170620521d08c8eada130817168fae067c91adc5b180d4444e41ba8757d6",old))
            apk=root/"apk";apk.mkdir()
            for name in ("home","panel"):(apk/(name+".apk")).write_bytes(b"new")
            digest=hashlib.sha256(b"new").hexdigest()
            import re
            update.write_text(re.sub(r"(?m)^\$approved = .*", "$approved = @{home='"+digest+"';panel='"+digest+"'}", update.read_text()))
            (apk/"SHA256SUMS").write_text("\n".join(f"{digest}  {name}.apk" for name in ("home","panel")))
            state=root/"state";info=dict(home=stock,files={"home":b"foreign".hex(),"panel":b"old".hex()},commands=[])
            state.write_text(json.dumps(info))
            env=dict(os.environ,BRAVIA_TV="192.0.2.40:5555",BRAVIA_ADB=str(adb),LOCALAPPDATA=str(root/"backups"),BRAVIA_FAKE_STATE=str(state))
            def run():return subprocess.run([POWERSHELL,"-NoProfile","-NonInteractive","-File","update.ps1"],cwd=scripts,env=env,capture_output=True,text=True)
            self.assertNotEqual(run().returncode,0)
            self.assertFalse(any(c.startswith("install") for c in json.loads(state.read_text())["commands"]))
            info["files"]["home"]=b"old".hex();state.write_text(json.dumps(info))
            info["fail_install"]="home";state.write_text(json.dumps(info))
            failed=run();self.assertNotEqual(failed.returncode,0)
            partial=json.loads(state.read_text())
            self.assertEqual(partial["files"]["panel"],b"new".hex(),failed.stdout+failed.stderr)
            self.assertEqual(partial["files"]["home"],b"old".hex())
            self.assertEqual(partial["temp"],{})
            self.assertIn("panel.apk",failed.stderr)
            del partial["fail_install"];state.write_text(json.dumps(partial))
            for _ in range(2):
                p=run();self.assertEqual(p.returncode,0,p.stderr)
            result=json.loads(state.read_text());self.assertEqual(result["home"],stock)
            self.assertEqual(sum(c.startswith("install -r") for c in result["commands"]),2)
            self.assertFalse(any("set-home-activity" in c or "disable-user" in c or "uninstall" in c for c in result["commands"]))
    def test_confirm_then_restore(self):self.trial(False)
    def test_refused_home_rolls_back(self):self.trial(True)

if __name__=="__main__":unittest.main()
