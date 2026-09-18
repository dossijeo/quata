#!/usr/bin/env python3
import re
import sys


SECRET = re.compile(
    r"(?i)(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+"
)


for line in sys.stdin:
    print(SECRET.sub(lambda match: match.group(1) + "[REDACTED]", line), end="")
