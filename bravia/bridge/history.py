"""One five-minute sample per reported sensor, bounded to 24 hours on local disk."""
import math
import sqlite3

class History:
    def __init__(self, path):
        self.db = sqlite3.connect(path)
        self.db.execute("CREATE TABLE IF NOT EXISTS sample (key TEXT, unit TEXT, slot INTEGER, value REAL, PRIMARY KEY(key,unit,slot))")
        self.last = {}

    def record(self, rows, now):
        slot = int(now)//300
        if self.last.get("slot") != slot:
            self.db.execute("DELETE FROM sample WHERE slot < ?", (slot-287,))
            self.last = {"slot":slot}
        with self.db:
            for row in rows:
                if row["dc"] not in ("temperature","humidity","illuminance"):
                    continue
                identity = (row["key"], row["unit"])
                if identity in self.last:
                    continue
                try:
                    value = float(row["value"])
                except (ValueError, TypeError):
                    continue
                if math.isfinite(value):
                    self.db.execute("INSERT OR REPLACE INTO sample VALUES(?,?,?,?)",(*identity,slot,value))
                    self.last[identity] = True

    def series(self, key, unit, now):
        return self.db.execute("SELECT slot*300,value FROM sample WHERE key=? AND unit=? AND slot>=? ORDER BY slot",
                               (key,unit,int(now)//300-287)).fetchall()
