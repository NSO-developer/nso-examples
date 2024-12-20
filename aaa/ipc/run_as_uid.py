import os
import subprocess
import sys


os.setuid(int(sys.argv[1]))
sys.exit(subprocess.run(sys.argv[2:]).returncode)
