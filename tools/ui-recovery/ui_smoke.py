#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Bounded real-UI acceptance on one dedicated synthetic-data API26 emulator.
Each tap uses a fresh observed hierarchy. App internals never substitute for UI.
Screenshots and hierarchy files survive failure. No external Python dependencies.
"""
import argparse, hashlib, json, os, re, struct, subprocess, time, traceback, xml.etree.ElementTree as ET, zlib
from pathlib import Path

class UI:
 def __init__(self,args):
  self.args=args;self.out=Path(args.out);self.out.mkdir(parents=True,exist_ok=True);self.end=time.monotonic()+args.budget_seconds;self.seq=0;self.checks=[];self.recording=None;self.videos=[];self.video_errors=[]
  self.adb=str(Path(os.environ['ANDROID_HOME'])/'platform-tools/adb')
  if args.serial!='emulator-5554' or args.expected_avd!='app-matrix-ci-api26':raise RuntimeError('Use the explicitly assigned disposable API26 emulator')
  if self.call('shell','-n','getprop','ro.kernel.qemu').strip()!=b'1':raise RuntimeError('Not an emulator')
  names=[self.call('shell','-n','getprop',p).decode().strip() for p in ['ro.boot.qemu.avd_name','ro.kernel.qemu.avd_name']]
  if not any(names) or any(n and n!=args.expected_avd for n in names):raise RuntimeError(f'Unexpected dedicated AVD: {names}')
  if self.call('shell','-n','getprop','sys.boot_completed').strip()!=b'1':raise RuntimeError('Boot incomplete')
  if self.call('shell','-n','getprop','ro.build.version.sdk').strip()!=b'26':raise RuntimeError('This evidence plan targets API26 only')
 def call(self,*args,timeout=40):
  remaining=self.end-time.monotonic()
  if remaining<=1:raise TimeoutError('UI budget exhausted')
  p=subprocess.run([self.adb,'-s',self.args.serial,*args],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=min(timeout,remaining))
  if p.returncode:raise RuntimeError(f'adb {args}: {p.returncode}: {p.stderr.decode(errors="replace")} {p.stdout[:300]!r}')
  return p.stdout
 def log(self,kind,**data):
  with (self.out/'events.jsonl').open('a') as f:f.write(json.dumps({'time':time.time(),'kind':kind,**data},ensure_ascii=False)+'\n')
 def screen(self,label='hierarchy',png=False):
  self.seq+=1;prefix=self.out/f'{self.seq:03}-{label}'
  if png:prefix.with_suffix('.png').write_bytes(self.call('exec-out','screencap','-p'))
  self.call('shell','-n','uiautomator','dump','/sdcard/app-matrix-ui-window.xml')
  raw=self.call('shell','-n','cat','/sdcard/app-matrix-ui-window.xml');prefix.with_suffix('.xml').write_bytes(raw)
  return ET.fromstring(raw)
 @staticmethod
 def norm(s):return ' '.join(s.split()).casefold()
 @staticmethod
 def bounds(n):
  b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
  return b if len(b)==4 and b[2]>b[0] and b[3]>b[1] else None
 def find(self,root,s):
  nodes=list(root.iter('node'));found=[]
  if 'after_label' in s:
   labels=[n for n in nodes if self.norm(n.get('text',''))==self.norm(s['after_label']) and self.bounds(n)]
   if len(labels)!=1:return []
   for n in nodes[nodes.index(labels[0])+1:]:
    if n.get('class')=='android.widget.EditText' and self.bounds(n):return [n]
   return []
  for n in nodes:
   if not self.bounds(n) or n.get('visible-to-user')=='false':continue
   if n.get('enabled')=='false' and not s.get('disabled_ok'):continue
   if 'text' in s and self.norm(n.get('text',''))!=self.norm(s['text']):continue
   if 'contains' in s and self.norm(s['contains']) not in self.norm(n.get('text','')):continue
   if 'desc' in s and n.get('content-desc')!=s['desc']:continue
   if 'class' in s and n.get('class')!=s['class']:continue
   found.append(n)
  # Prefer unique nearest clickable targets over a duplicate toolbar heading.
  if len(found)>1:
   parents={c:p for p in root.iter() for c in p};scored=[]
   for n in found:
    a=n
    for distance in range(4):
     if a.get('clickable')=='true':scored.append((distance,n));break
     a=parents.get(a)
     if a is None:break
   if scored:
    shortest=min(d for d,n in scored);found=[n for d,n in scored if d==shortest]
  return found
 def select(self,s,scrolls=0):
  for attempt in range(scrolls+3):
   root=self.screen();ns=self.find(root,s)
   if len(ns)==1:return ns[0]
   if len(ns)>1:raise AssertionError(f'Ambiguous selector {s}: {[n.attrib for n in ns]}')
   if attempt<2:time.sleep(.7);continue
   panes=[n for n in root.iter('node') if n.get('scrollable')=='true' and self.bounds(n)]
   if attempt-2<scrolls and panes:
    x1,y1,x2,y2=self.bounds(panes[-1]);self.call('shell','-n','input','swipe',str((x1+x2)//2),str(y2-30),str((x1+x2)//2),str(y1+30),'350');time.sleep(.5)
  raise AssertionError(f'Visible selector missing: {s}')
 def tap(self,s,scrolls=0):
  n=self.select(s,scrolls);x1,y1,x2,y2=self.bounds(n);self.log('tap',selector=s,node=n.attrib);self.call('shell','-n','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5);return n
 def text(self,s):
  if not re.fullmatch(r'[A-Za-z0-9 ,._:-]+',s):raise ValueError('Synthetic safe ASCII only')
  self.call('shell','-n','input','text',s.replace(' ','%s'));time.sleep(.5)
 def key(self,k):self.call('shell','-n','input','keyevent',k);time.sleep(.5)
 def hide_keyboard(self):
  state=self.call('shell','-n','dumpsys','input_method').decode(errors='replace');self.log('keyboard',shown='mInputShown=true' in state)
  if 'mInputShown=true' in state:self.key('KEYCODE_BACK')
 def record_start(self,name):
  self.record_stop()
  # Record only after this app is visibly open, never initial Android Home.
  try:
   prior=self.call('shell','-n','pidof','screenrecord').decode().strip()
  except RuntimeError:prior=''
  if prior:raise RuntimeError('Unexpected existing recorder; refusing to interfere')
  log=(self.out/(name+'.screenrecord.txt')).open('wb');path='/sdcard/'+name+'.mp4'
  process=subprocess.Popen([self.adb,'-s',self.args.serial,'shell','-n','screenrecord','--size','480x800','--bit-rate','750000','--time-limit','180',path],stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
  self.recording=(name,path,process,log);self.log('record_start',name=name,max_seconds=180)
  time.sleep(.5)
 def record_stop(self):
  if not self.recording:return
  name,path,process,log=self.recording;self.recording=None
  # Bounded cleanup remains possible after the UI deadline. It only stops our
  # recorder on this validated disposable AVD and reads its generated media.
  def cleanup_call(*args,timeout=12):
   p=subprocess.run([self.adb,'-s',self.args.serial,*args],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
   if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
   return p.stdout
  try:
   if process.poll() is None:
    try:
     pids=cleanup_call('shell','-n','pidof','screenrecord',timeout=5).decode().split()
     if pids and all(p.isdigit() for p in pids):cleanup_call('shell','-n','kill','-2',*pids,timeout=5)
    except RuntimeError:pass
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=2)
   log.close();cleanup_call('pull',path,str(self.out/(name+'.mp4')))
   data=(self.out/(name+'.mp4')).read_bytes()
   if len(data)<1024 or data[4:8]!=b'ftyp':raise RuntimeError('Recording did not produce an MP4 container')
   self.videos.append(name+'.mp4');self.log('record_stop',name=name,bytes=len(data))
  except Exception as e:
   if process.poll() is None:process.terminate()
   log.close();self.video_errors.append(name+': '+str(e));self.log('record_error',name=name,error=str(e))
 def check(self,ok,label):
  if not ok:raise AssertionError(label)
  self.checks.append(label);self.log('check',passed=True,label=label)
 def assert_text(self,s):self.select({'text':s});self.check(True,f'Visible text: {s}')
 def assert_field(self,label,expected,empty_hint=''):
  n=self.select({'after_label':label});actual=n.get('text','');self.check(actual==expected or not expected and actual==empty_hint,f'{label} actual visible value = {expected!r}')
 def start(self,pkg):
  if pkg not in ('dev.appmatrix.poster','dev.appmatrix.journal'):raise ValueError(pkg)
  self.call('shell','-n','am','start','-W','-n',pkg+'/.MainActivity',timeout=60);time.sleep(1)
 def stop(self,pkg):
  if pkg not in ('dev.appmatrix.poster','dev.appmatrix.journal'):raise ValueError(pkg)
  self.call('shell','-n','am','force-stop',pkg)
 def top(self):
  root=self.screen();panes=[n for n in root.iter('node') if n.get('scrollable')=='true' and self.bounds(n)]
  if not panes:return
  x1,y1,x2,y2=self.bounds(panes[-1])
  for _ in range(3):self.call('shell','-n','input','swipe',str((x1+x2)//2),str(y1+30),str((x1+x2)//2),str(y2-30),'250')
 def downloads(self):
  root=self.screen('documents-roots',png=True)
  buttons=[n for n in root.iter('node') if n.get('content-desc') in ('Show roots','Open navigation drawer','Show navigation drawer')]
  if buttons:self.tap({'desc':buttons[0].get('content-desc')});self.tap({'text':'Downloads'})
  elif not self.find(root,{'text':'Downloads'}):raise AssertionError('Cannot establish Downloads from actual picker')
 def pick(self,name):
  root=self.screen('system-picker',png=True)
  if not self.find(root,{'text':name}):self.downloads()
  self.tap({'text':name},3);time.sleep(1)
 def save_document(self,name):
  self.downloads();n=self.tap({'class':'android.widget.EditText'});self.key('KEYCODE_MOVE_END')
  length=len(n.get('text',''));self.call('shell','-n','input','keyevent',*(['KEYCODE_DEL']*min(length+4,100)))
  self.text(name);self.hide_keyboard();self.tap({'text':'Save'});time.sleep(1)
 def export_poster(self,name):
  self.top();self.tap({'text':'Export PNG'});self.save_document(name);self.select({'text':'Export PNG'})
  self.call('pull','/sdcard/Download/'+name,str(self.out/name));self.screen('poster-exported',png=True)
 def curves(self,apply):
  self.tap({'text':'Adjust tone curves'},4);n=self.select({'desc':'Midtones output level, 0 to 100'},3);x1,y1,x2,y2=self.bounds(n)
  self.log('slider',node=n.attrib,fraction=.78);self.call('shell','-n','input','tap',str(round(x1+(x2-x1)*.78)),str((y1+y2)//2));self.screen('poster-curves',png=True);self.tap({'text':'Apply' if apply else 'Cancel'});time.sleep(1)

def png_pixels(path):
 b=Path(path).read_bytes();assert b[:8]==b'\x89PNG\r\n\x1a\n';pos=8;idat=b'';types=[];w=h=depth=color=None
 while pos<len(b):
  n=struct.unpack('>I',b[pos:pos+4])[0];kind=b[pos+4:pos+8];data=b[pos+8:pos+8+n];assert zlib.crc32(kind+data)&0xffffffff==struct.unpack('>I',b[pos+8+n:pos+12+n])[0];types.append(kind.decode())
  if kind==b'IHDR':w,h,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',data);assert depth==8 and color in (2,6) and interlace==0
  if kind==b'IDAT':idat+=data
  pos+=12+n
 raw=zlib.decompress(idat);channels=3 if color==2 else 4;stride=w*channels;previous=bytearray(stride);pixels=bytearray();offset=0
 for y in range(h):
  filt=raw[offset];row=bytearray(raw[offset+1:offset+1+stride]);offset+=stride+1
  for x in range(stride):
   left=row[x-channels] if x>=channels else 0;up=previous[x];ul=previous[x-channels] if x>=channels else 0
   if filt==1:v=left
   elif filt==2:v=up
   elif filt==3:v=(left+up)//2
   elif filt==4:
    p=left+up-ul;aa,bb,cc=abs(p-left),abs(p-up),abs(p-ul);v=left if aa<=bb and aa<=cc else up if bb<=cc else ul
   elif filt==0:v=0
   else:raise AssertionError('Unsupported PNG filter')
   row[x]=(row[x]+v)&255
  for x in range(0,stride,channels):pixels.extend(row[x:x+3])
  previous=row
 return (w,h),bytes(pixels),types

def journal(u):
 pkg='dev.appmatrix.journal';u.start(pkg);u.assert_text('Field Journal');u.screen('journal-launch',png=True);u.record_start('journal-01-create-undo-save');u.tap({'contains':'New observation'});u.tap({'after_label':'TITLE'});u.text('CI River notes');u.hide_keyboard();u.tap({'after_label':'NOTES'});u.text('QA');u.screen('journal-keyboard',png=True);u.hide_keyboard();u.tap({'text':'Undo notes'},2);u.assert_field('NOTES','','What did you notice?');u.tap({'text':'Redo notes'});u.assert_field('NOTES','QA');u.screen('journal-undo-redo',png=True);u.tap({'text':'Save observation'});u.assert_text('CI River notes');u.record_stop();u.stop(pkg);u.start(pkg);u.assert_text('CI River notes');u.screen('journal-saved-reopen',png=True);u.record_start('journal-02-dirty-draft')
 u.tap({'contains':'New observation'});u.tap({'after_label':'TITLE'});u.text('CI Dirty recovery');u.hide_keyboard();u.tap({'after_label':'NOTES'});u.text('DirtyDraft');u.hide_keyboard();time.sleep(1);u.record_stop();u.stop(pkg);u.start(pkg);u.assert_text('Continue your draft?');u.record_start('journal-03-recovered-draft');u.screen('journal-dirty-recovery-prompt',png=True);u.tap({'text':'Continue editing'});u.assert_field('TITLE','CI Dirty recovery');u.assert_field('NOTES','DirtyDraft');u.screen('journal-dirty-recovered',png=True);u.tap({'text':'Back'});u.assert_text('Keep this draft?');u.screen('journal-back-dirty',png=True);u.tap({'text':'Keep draft & close'});u.tap({'text':'More'});u.tap({'text':'Settings'});u.screen('journal-settings',png=True);u.record_stop()

def poster(u):
 pkg='dev.appmatrix.poster';u.start(pkg);u.assert_text('Pocket Poster');u.screen('poster-launch',png=True);u.record_start('poster-01-import-curves-export');u.tap({'contains':'Import an image'});u.screen('poster-picker-cancel-before',png=True);u.key('KEYCODE_BACK');u.assert_text('Make something worth sharing');u.check(True,'System picker Back preserves empty library');u.tap({'contains':'Import an image'});u.pick('synthetic.png');u.select({'text':'Save project'});u.screen('poster-imported',png=True);u.tap({'text':'Save project'});u.export_poster('poster-baseline.png');u.curves(False);u.export_poster('poster-cancel-curves.png');u.check((u.out/'poster-baseline.png').read_bytes()==(u.out/'poster-cancel-curves.png').read_bytes(),'Cancelled curve edit leaves actual SAF PNG bytes unchanged');u.curves(True);u.top();u.tap({'text':'Save project'});u.record_stop();u.stop(pkg);u.start(pkg);u.select({'text':'Export PNG'});u.record_start('poster-02-reopen-export');u.screen('poster-saved-reopened',png=True);u.export_poster('poster-reopened-curved.png');u.tap({'text':'Settings'});u.screen('poster-settings',png=True);u.record_stop()
 size,baseline,chunks=png_pixels(u.out/'poster-baseline.png');fs,source,fc=png_pixels(Path(u.args.fixtures)/'synthetic.png');cs,curved,cc=png_pixels(u.out/'poster-reopened-curved.png');u.check(size==fs==cs==(640,480),'Actual SAF PNG dimensions stay640x480');u.check(baseline==source,'Unedited SAF PNG preserves independently decoded fixture pixels');u.check(curved!=baseline,'Applied curve edit survives save/force-stop/reopen and changes actual exported pixels');u.check(not set(chunks+cc)&{'eXIf','tEXt','zTXt','iTXt'},'Export contains no EXIF/text metadata chunks');u.log('pixel_evidence',changed_rgb_bytes=sum(a!=b for a,b in zip(baseline,curved)),source_sha256=hashlib.sha256(source).hexdigest(),baseline_sha256=hashlib.sha256(baseline).hexdigest(),curved_sha256=hashlib.sha256(curved).hexdigest())

def main():
 ap=argparse.ArgumentParser();ap.add_argument('app',choices=['journal','poster']);ap.add_argument('--out',required=True);ap.add_argument('--fixtures',required=True);ap.add_argument('--serial',default='emulator-5554');ap.add_argument('--expected-avd',default='app-matrix-ci-api26');ap.add_argument('--budget-seconds',type=int,default=360);args=ap.parse_args();u=None
 try:
  u=UI(args);globals()[args.app](u);result={'status':'failed' if u.video_errors else 'passed','ui_status':'passed','app':args.app,'runtime_api':26,'checks':u.checks,'videos':u.videos,'video_errors':u.video_errors};(u.out/'RESULT.json').write_text(json.dumps(result,indent=2));print(json.dumps(result));return 1 if u.video_errors else 0
 except Exception as e:
  result={'status':'failed','app':args.app,'runtime_api':26,'error':str(e),'checks':u.checks if u else []};Path(args.out).mkdir(parents=True,exist_ok=True);(Path(args.out)/'RESULT.json').write_text(json.dumps(result,indent=2));print(json.dumps(result));traceback.print_exc()
  if u:
   u.record_stop()
   u.log('failure',error=str(e))
   try:u.screen('failure',png=True)
   except Exception:pass
  return 1
if __name__=='__main__':raise SystemExit(main())
