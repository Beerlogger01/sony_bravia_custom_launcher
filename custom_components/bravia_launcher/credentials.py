"""Transient pairing challenges. Persistent config entries contain token digests only."""
import hashlib
import hmac
import secrets
import time
from collections import deque

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

class Pairing:
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.codes = {}
        self.attempts = deque()

    def issue(self, entry_id):
        code = f"{secrets.randbelow(100_000_000):08d}"
        self.codes[entry_id] = (digest(code), self.clock() + 180)
        return code

    def redeem(self, code):
        now = self.clock()
        while self.attempts and self.attempts[0] <= now - 180:
            self.attempts.popleft()
        if len(self.attempts) >= 8:
            raise PermissionError
        self.attempts.append(now)
        matches = [key for key, (expected, expiry) in self.codes.items()
                   if expiry > now and hmac.compare_digest(expected, digest(code))]
        if len(matches) != 1:
            return None
        del self.codes[matches[0]]
        return matches[0]
