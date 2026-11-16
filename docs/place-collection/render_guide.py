#!/usr/bin/env python3
"""편집한 GUIDE.md를 PDF로 렌더링한다. 의존성: reportlab. 한글 TrueType 글꼴 필요."""
from pathlib import Path
import argparse, html, json, re
parser=argparse.ArgumentParser()
parser.add_argument('--source',type=Path,default=Path(__file__).with_name('GUIDE.md'))
parser.add_argument('--output',type=Path,default=Path('output/pdf/place-collection-guide.pdf'))
parser.add_argument('--font',type=Path,default=Path('/Library/Fonts/Arial Unicode.ttf'))
args=parser.parse_args()
args.output.parent.mkdir(parents=True,exist_ok=True)
text=args.source.read_text()
counts=json.loads(args.source.with_name('verification-summary.json').read_text())
# PDF renderer. Guide MD is the editable source; this parser intentionally supports only its headings/tables/code/list syntax.
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.platypus import BaseDocTemplate,PageTemplate,Frame,Paragraph,Spacer,PageBreak,Table,TableStyle,KeepTogether
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.graphics.shapes import Drawing,Rect,String,Line,Polygon
font=str(args.font)
pdfmetrics.registerFont(TTFont('K',font));pdfmetrics.registerFontFamily('K',normal='K',bold='K',italic='K',boldItalic='K')
navy=colors.HexColor('#163650');teal=colors.HexColor('#087F83');muted=colors.HexColor('#64748B');ink=colors.HexColor('#253549')
styles={
'body':ParagraphStyle('body',fontName='K',fontSize=10.3,leading=16.0,textColor=ink,spaceAfter=8.5,wordWrap='CJK'),
'h1':ParagraphStyle('h1',fontName='K',fontSize=19,leading=27,textColor=navy,spaceAfter=17,keepWithNext=True,wordWrap='CJK'),
'h2':ParagraphStyle('h2',fontName='K',fontSize=12.3,leading=18,textColor=teal,spaceBefore=10,spaceAfter=9,keepWithNext=True,wordWrap='CJK'),
'cell':ParagraphStyle('cell',fontName='K',fontSize=8.6,leading=12.8,textColor=ink,wordWrap='CJK'),
'head':ParagraphStyle('head',fontName='K',fontSize=9.0,leading=13.5,textColor=colors.white,wordWrap='CJK'),
'code':ParagraphStyle('code',fontName='K',fontSize=8.2,leading=12.3,textColor=navy,backColor=colors.HexColor('#F1F5F9'),borderPadding=9,spaceAfter=12,wordWrap='CJK'),
'bullet':ParagraphStyle('bullet',fontName='K',fontSize=10.0,leading=16,spaceAfter=7,leftIndent=12,firstLineIndent=-9,wordWrap='CJK',textColor=ink),
}
W,H=595.28,841.89;M=46;CW=W-2*M
class Guide(BaseDocTemplate):
 def __init__(self,path):
  super().__init__(str(path),pagesize=(W,H),leftMargin=M,rightMargin=M,topMargin=53,bottomMargin=47,title='장소 수집 통합 가이드',author='Course Backend')
  self.addPageTemplates(PageTemplate(id='main',frames=[Frame(M,47,CW,H-100,id='body',leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)],onPage=self.page))
 def page(self,c,doc):
  c.saveState()
  if doc.page>1:
   c.setFont('K',8);c.setFillColor(muted);c.drawString(M,H-29,'COURSE BACKEND  /  장소 수집 통합 가이드')
   c.setStrokeColor(colors.HexColor('#D8E2EA'));c.line(M,H-37,W-M,H-37)
   c.setFont('K',8);c.drawString(M,24,'2026-11-13  ·  구현·검증 기준');c.drawRightString(W-M,24,str(doc.page))
  c.restoreState()
 def afterFlowable(self,f):
  if isinstance(f,Paragraph) and f.style.name=='h1' and f.getPlainText()!='목차':
   key='sec'+str(self.seq.nextf('sec'));self.canv.bookmarkPage(key);self.notify('TOCEntry',(0,f.getPlainText(),self.page,key))
   self.canv.addOutlineEntry(f.getPlainText(),key,0,False)
def P(s,style='body'):return Paragraph(html.escape(s),styles[style])
def diagram():
 d=Drawing(CW,100)
 names=[('카카오','구역·페이지'),('Temp','원본·단계'),('네이버','장소·블로그'),('AI Batch','분류·검증'),('Place·Tag','추천 데이터')]
 gap=13;bw=(CW-gap*4)/5
 for i,(a,b) in enumerate(names):
  x=i*(bw+gap)
  d.add(Rect(x,23,bw,63,rx=7,ry=7,fillColor=colors.HexColor('#EEF6F7'),strokeColor=colors.HexColor('#B9D7DB')))
  d.add(String(x+bw/2,62,a,fontName='K',fontSize=11,fillColor=navy,textAnchor='middle'))
  d.add(String(x+bw/2,42,b,fontName='K',fontSize=8,fillColor=muted,textAnchor='middle'))
  if i<4:
   d.add(Line(x+bw+2,55,x+bw+gap-3,55,strokeColor=teal));d.add(Polygon([x+bw+gap-3,55,x+bw+gap-7,58,x+bw+gap-7,52],fillColor=teal,strokeColor=teal))
 return d
story=[]
story += [Spacer(1,105),Paragraph('PIPELINE OPERATIONS / v1.0',ParagraphStyle('eyebrow',fontName='K',fontSize=11,textColor=teal,spaceAfter=23)),Paragraph('장소 수집<br/>통합 가이드',ParagraphStyle('cover',fontName='K',fontSize=36,leading=49,textColor=navy,spaceAfter=25)),P('카카오 · 네이버 · AI Batch'),P('오류 처리와 중단 복구 구현 보고서'),Spacer(1,40),diagram(),Spacer(1,30),P(f"검증: {counts['tests']-counts['skipped']}건 통과 / {counts['skipped']}건 생략"),P('2026년 11월 13일'),PageBreak()]
story += [P('목차','h1')]
toc=TableOfContents();toc.levelStyles=[ParagraphStyle('toc',fontName='K',fontSize=10.8,leading=24,textColor=navy)];story +=[toc,PageBreak()]
lines=text.splitlines();i=2
while i<len(lines):
 l=lines[i].strip()
 if not l:i+=1;continue
 if l.startswith('## '):
  if len(story)>0 and not isinstance(story[-1],PageBreak):story.append(PageBreak())
  story.append(P(l[3:],'h1'));i+=1;continue
 if l.startswith('### '):story.append(P(l[4:],'h2'));i+=1;continue
 if l=='[FLOW]':story.append(diagram());i+=1;continue
 if l.startswith('```'):
  block=[];i+=1
  while i<len(lines) and not lines[i].startswith('```'):block.append(lines[i]);i+=1
  story.append(Paragraph('<br/>'.join(html.escape(v).replace(' ','&#160;') for v in block),styles['code']));i+=1;continue
 if l.startswith('|'):
  rows=[]
  while i<len(lines) and lines[i].strip().startswith('|'):
   row=[v.strip() for v in lines[i].strip().strip('|').split('|')]
   if not all(re.fullmatch(r':?-+:?',v) for v in row):rows.append(row)
   i+=1
  n=len(rows[0]);widths=([CW*.43,CW*.37,CW*.20] if rows[0][0]=='오류코드' else [CW/n]*n)
  if n==2:widths=[CW*.43,CW*.57]
  table=Table([[P(v,'head' if k==0 else 'cell') for v in row] for k,row in enumerate(rows)],colWidths=widths,repeatRows=1,hAlign='LEFT')
  table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),navy),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.HexColor('#F1F5F8'),colors.white]),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5),('LINEBELOW',(0,0),(-1,0),.5,navy),('LINEBELOW',(0,1),(-1,-1),.35,colors.HexColor('#DAE3EA'))]))
  story.extend([table,Spacer(1,13)]);continue
 if l.startswith('- '):story.append(P('• '+l[2:],'bullet'));i+=1;continue
 paragraph=[l];i+=1
 while i<len(lines) and lines[i].strip() and not lines[i].startswith(('#','|','- ','```','[FLOW]')):paragraph.append(lines[i].strip());i+=1
 story.append(P(' '.join(paragraph)))
Guide(args.output).multiBuild(story)
print(args.output)
