#!/usr/bin/env python3
# Copyright 2026 Cisco Systems, Inc.
# SPDX-License-Identifier: MIT

import random

def main():
    colors = [
        "aqua",
        "blue",
        "buff",
        "cyan",
        "fawn",
        "gold",
        "gray",
        "jade",
        "lime",
        "mint",
        "navy",
        "noir",
        "onyx",
        "pink",
        "plum",
        "rose",
        "ruby",
        "sage",
        "sand",
        "teal",
    ]
    things = [
        "arch",
        "barn",
        "coin",
        "door",
        "edge",
        "farm",
        "gate",
        "hill",
        "icon",
        "jazz",
        "king",
        "lake",
        "moon",
        "nest",
        "orca",
        "path",
        "quiz",
        "road",
        "seed",
        "tree",
        "undo",
        "veil",
        "wind",
        "xray",
        "yarn",
        "zoom",
    ]
    return f"{random.choice(colors)}-{random.choice(things)}"


if __name__ == "__main__":
    print(main())
