"""Generate an editable Java item model and a geometry preview; no plugin registration."""
import base64, json, uuid, math, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
palette = ['#343941','#626c76','#a4adb3','#27292d','#747974','#252321','#ff852c','#ffc65b','#79512f','#b28a55','#c8d2d7']
texture = Image.new('RGB', (128,128), '#343941')
d = ImageDraw.Draw(texture)
for i,c in enumerate(palette):
    x,y=(i%8)*16,(i//8)*16
    d.rectangle((x,y,x+15,y+15), fill=c)
texture.save(ROOT/'miningbot.png')
elements=[]
groups={name: [] for name in ['Lore', 'Ofen', 'Spitzhacke rechts', 'Schaufel links']}
def cube(group,name,a,b,color):
    x,y=(color%8)*16,(color//8)*16
    element=dict(name=name,type='cube',uuid=str(uuid.uuid4()),from_=a,to=b,origin=[8,0,8],box_uv=False,
                 faces={f:dict(uv=[x+1,y+1,x+15,y+15],texture=0) for f in ['north','east','south','west','up','down']})
    element['from']=element.pop('from_')
    elements.append(element); groups[group].append(element['uuid'])
    element['_color']=color

cube('Lore','Unterboden',[1,2,-1],[15,4,17],0)
for x in [1,13]:
    for z in [1,12]:
        cube('Lore','Rad',[x,0,z],[x+2,3,z+3],3)
        cube('Lore','Radnabe',[x-.1,1,z+1],[x+2.1,2,z+2],1)
for x in [0,14]:
    cube('Lore','Seitenwand',[x,4,-1],[x+2,9,17],1)
    cube('Lore','Rand oben',[x-.25,9,-1.5],[x+2.25,10,17.5],2)
for z in [-1,15]:
    cube('Lore','Stirnwand',[2,4,z],[14,9,z+2],1)
    cube('Lore','Rand Stirnseite',[2,9,z-.25],[14,10,z+2.25],2)
for x in [2.5,12.5]:
    cube('Lore','Niete vorne',[x,6,-1.15],[x+1,7,-.95],2)
cube('Ofen','Ofenkorpus',[3,7,3],[13,18,13],4)
cube('Ofen','Ofendeckel',[2.8,18,2.8],[13.2,19,13.2],1)
cube('Ofen','Obere Ofenöffnung',[4.5,15,2.8],[11.5,16.5,3],3)
cube('Ofen','Feueröffnung',[4.5,10.5,2.75],[11.5,14,3],5)
cube('Ofen','Glut',[5,10.7,2.6],[11,11.5,2.8],6)
for x,h in [(5.5,1.3),(7.5,2),(9.5,1.5)]:
    cube('Ofen','Flamme',[x,11,2.55],[x+1,11+h,2.75],7)
for x in [5.5,8,10.5]:
    cube('Ofen','Ofenrost',[x,10.5,2.4],[x+.35,14,2.6],0)
cube('Ofen','Abgasstutzen',[7,19,9],[9,21,11],0)
for side,group in [(17,'Spitzhacke rechts'),(-2,'Schaufel links')]:
    for n in range(7):
        cube(group,'Holzstiel',[side,6+n,12-n],[side+1,7+n,13-n],8)
    for y,z in [(8,10),(11,7)]:
        cube(group,'Haltebügel',[side-.2,y,z],[side+1.2,y+.5,z+1.3],2)
cube('Spitzhacke rechts','Kopf Mitte',[16.8,13,3],[18.2,14.5,9],10)
cube('Spitzhacke rechts','Spitze vorn',[16.8,11.5,2],[18.2,14,3.5],10)
cube('Spitzhacke rechts','Spitze hinten',[16.8,11.5,8.5],[18.2,14,10],10)
cube('Schaufel links','Blatt Mitte',[-2.2,13,3.5],[-.8,16,6.5],10)
cube('Schaufel links','Blatt Spitze',[-2.2,16,4],[-.8,17,6],10)

display={'gui':{'rotation':[25,225,0],'translation':[0,-1,0],'scale':[.7,.7,.7]},
         'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
         'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]}}
outliner=[dict(name=k,uuid=str(uuid.uuid4()),origin=[8,0,8],children=v,export=True,isOpen=True) for k,v in groups.items()]
clean=[{k:v for k,v in e.items() if not k.startswith('_')} for e in elements]
bb=dict(meta=dict(format_version='5.0',model_format='java_block',box_uv=False),name='MiningBot · Ofenlore',
        model_identifier='miningbot',resolution=dict(width=128,height=128),elements=clean,outliner=outliner,display=display,
        textures=[dict(name='miningbot.png',id='0',relative_path='miningbot.png',width=128,height=128,uv_width=128,uv_height=128,
                       uuid=str(uuid.uuid4()),particle=True,internal=True,saved=True,
                       source='data:image/png;base64,'+base64.b64encode((ROOT/'miningbot.png').read_bytes()).decode())])
(ROOT/'miningbot.bbmodel').write_text(json.dumps(bb,ensure_ascii=False,indent=2),encoding='utf-8')
export=[]
for e in elements:
    export.append(dict(name=e['name'],from_=e['from'],to=e['to'],faces={f:dict(uv=[n/8 for n in face['uv']],texture='#0') for f,face in e['faces'].items()}))
for e in export: e['from']=e.pop('from_')
(ROOT/'miningbot.json').write_text(json.dumps(dict(credit='MiningBot design draft',texture_size=[128,128],textures={'0':'casino:item/miningbot','particle':'casino:item/miningbot'},elements=export,display=display),indent=2),encoding='utf-8')

# Orthographic preview of the actual cuboids from both front sides.
image=Image.new('RGB',(1400,820),'#19212a'); draw=ImageDraw.Draw(image)
font_path='C:/Windows/Fonts/arial.ttf'
font=ImageFont.truetype(font_path,22); title=ImageFont.truetype(font_path,34)
draw.text((45,30),'MININGBOT · OFENLORE',font=title,fill='#f0f2f4')
draw.text((45,78),'Modellentwurf · Spitzhacke rechts / Schaufel links in Fahrtrichtung',font=font,fill='#adb9c5')
for view,center in [(1,350),(-1,1050)]:
    def project(p):
        x,y,z=p; x-=8; z-=8
        return (center+view*x*16+z*12,580-y*19-z*7+view*x*7)
    polys=[]
    for e in elements:
        x,y,z=e['from']; X,Y,Z=e['to']
        faces=[([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1.1),
               ([(x,y,z),(X,y,z),(X,Y,z),(x,Y,z)],.9)]
        if view==1: faces.append(([(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)],.7))
        else: faces.append(([(x,y,Z),(x,y,z),(x,Y,z),(x,Y,Z)],.75))
        color=tuple(bytes.fromhex(palette[e['_color']][1:]))
        for points,shade in faces:
            depth=sum(view*p[0]+p[1]*.55-p[2] for p in points)/4
            polys.append((depth,[project(p) for p in points],tuple(min(255,int(c*shade)) for c in color)))
    for _,points,color in sorted(polys,key=lambda p:p[0]): draw.polygon(points,fill=color,outline='#242931')
    draw.text((center-240,730),'Vorne / rechts' if view==1 else 'Vorne / links',font=font,fill='#d6e0e8')
image.save(ROOT/'miningbot-vorschau.png')
print(f'Created {len(elements)} cuboids, bbmodel, JSON, PNG and preview.')
