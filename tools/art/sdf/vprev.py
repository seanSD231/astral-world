import json,sys
from PIL import Image,ImageDraw
import numpy as np
def prev(name,rotx_deg=0,roty_deg=0,S=14,frame=0):
    import math
    tex=Image.open(f'/tmp/vox/{name}_vox.png').convert('RGB')
    models=[json.load(open(f'/tmp/vox/{name}_3d.json'))]
    import glob
    for g in glob.glob(f'/tmp/vox/{name}_part_*.json'): models.append(json.load(open(g)))
    quads=[]
    shade={'up':1.0,'down':.5,'north':.8,'south':.85,'east':.7,'west':.75}
    ry=math.radians(roty_deg); rx=math.radians(rotx_deg)
    def rot(p):
        x,y,z=p[0]-8,p[1]-8,p[2]-8
        x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
        y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
        return x,y,z
    for m in models:
        for e in m['elements']:
            (fd,fc),=e['faces'].items(); a,b=e['from'],e['to']; u=fc['uv']
            px=int((u[0]+u[2])/2); py=int((u[1]+u[3])/2)+frame*16
            c=tex.getpixel((px,py)); k=shade[fd]
            c=tuple(min(255,int(v*k)) for v in c)
            xs=[a[0],b[0]]; ys=[a[1],b[1]]; zs=[a[2],b[2]]
            if fd in('east','west'): pts=[(a[0],y,z) for y,z in ((a[1],a[2]),(b[1],a[2]),(b[1],b[2]),(a[1],b[2]))]
            elif fd in('up','down'): pts=[(x,a[1],z) for x,z in ((a[0],a[2]),(b[0],a[2]),(b[0],b[2]),(a[0],b[2]))]
            else: pts=[(x,y,a[2]) for x,y in ((a[0],a[1]),(b[0],a[1]),(b[0],b[1]),(a[0],b[1]))]
            pts=[rot(p) for p in pts]
            quads.append((sum(p[2] for p in pts)/4,pts,c))
    quads.sort(key=lambda q:q[0])
    W=16*S+40
    im=Image.new('RGB',(W,W),(24,26,38)); d=ImageDraw.Draw(im)
    for z,pts,c in quads:
        d.polygon([(20+(p[0]+8)*S,20+(8-p[1])*S) for p in pts],fill=c)
    return im
if __name__=='__main__':
    names=sys.argv[1:]
    ims=[prev(n) for n in names]
    s=Image.new('RGB',(ims[0].width*len(ims),ims[0].height))
    for i,m in enumerate(ims): s.paste(m,(i*m.width,0))
    s.save('/tmp/r3/vprev.png')
