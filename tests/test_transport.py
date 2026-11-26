import importlib.util
import unittest
from pathlib import Path
from types import SimpleNamespace

path=Path(__file__).resolve().parents[1]/"custom_components/bravia_launcher/transport.py"
spec=importlib.util.spec_from_file_location("transport",path)
transport=importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)

class PolicyTests(unittest.TestCase):
    def test_scope(self):
        opts={"lan_http":True,"tv_ip":"10.1.2.3"}
        for peer,headers,ok in (("10.1.2.3",{},True),("10.1.2.4",{},False),
                                ("10.1.2.4",{"X-Forwarded-For":"10.1.2.3"},False),
                                ("203.0.113.1",{},False),(None,{},False)):
            req=SimpleNamespace(secure=False,headers=headers,transport=SimpleNamespace(get_extra_info=lambda _: (peer,1) if peer else None))
            self.assertEqual(transport.allowed(req,opts),ok)
            self.assertFalse(transport.allowed(req,{}))
            req.secure=True
            self.assertTrue(transport.allowed(req,{}))
            self.assertEqual(transport.allowed(req,opts),ok)
