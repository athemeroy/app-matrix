#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Deterministic original synthetic PNG; no Pillow or network dependency."""
import struct,zlib,sys
from pathlib import Path
p=Path(sys.argv[1]);p.mkdir(parents=True,exist_ok=True);w,h=640,480;raw=bytearray()
for y in range(h):
 raw.append(0)
 for x in range(w):
  c=(x*255//639,y*255//479,(x+y)*255//1118)
  if x<80 and y<80:c=(255,0,0)
  elif x>=560 and y<80:c=(0,255,0)
  elif x<80 and y>=400:c=(0,0,255)
  elif x>=560 and y>=400:c=(255,255,0)
  raw.extend(c)
def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(raw),9))+chunk(b'IEND',b'');(p/'synthetic.png').write_bytes(png)
print('Generated synthetic.png640x480',len(png),'bytes')
