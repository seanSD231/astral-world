import sys,struct,numpy as np,math
from PIL import Image,ImageDraw
def load(name):
    b=open(f'/tmp/mesh/{name}.bin','rb').read(); o=0
    npart,=struct.unpack_from('>i',b,o); o+=4; parts=[]
    for _ in range(npart):
        n,m=struct.unpack_from('>ii',b,o); o+=8
        rec=np.dtype([('p','>f4',3),('n','>f4',3),('c','u1',3),('k','u1'),('ph','u1'),('t','u1')])
        V=np.frombuffer(b,rec,n,o); o+=n*rec.itemsize
        F=np.frombuffer(b,'>i4',m*3,o).reshape(m,3); o+=m*12; parts.append((V,F))
    return parts
def prev(name,rx=0,ry=0,S=34,out=None):
    parts=load(name); W=int(16*S)+20; im=Image.new('RGB',(W,W),(40,60,50)); d=ImageDraw.Draw(im)
    cy,sy=math.cos(math.radians(ry)),math.sin(math.radians(ry)); cx,sx=math.cos(math.radians(rx)),math.sin(math.radians(rx))
    tris=[]
    for V,F in parts:
        P=(V['p'].astype(float)-.5)*16; N=V['n'].astype(float); C=V['c'].astype(float)
        def r(A): 
            x=A[:,0]*cy+A[:,2]*sy; z=-A[:,0]*sy+A[:,2]*cy; y=A[:,1]*cx-z*sx; z=A[:,1]*sx+z*cx; return np.stack([x,y,z],1)
        P=r(P); N=r(N); L=np.array([-.4,.6,.7]); L/=np.linalg.norm(L)
        sh=np.ones(len(N))*1.0
        for f in F:
            z=P[f,2].mean(); c=(C[f].mean(0)*sh[f].mean()).clip(0,255)
            tris.append((z,[(10+(P[i,0]+8)*S,10+(8-P[i,1])*S) for i in f],tuple(int(x) for x in c)))
    tris.sort(key=lambda t:t[0])
    for z,pts,c in tris: d.polygon(pts,fill=c)
    im.save(out or f'/tmp/r3/mp_{name}.png')
if __name__=='__main__': prev(sys.argv[1],*(int(x) for x in sys.argv[2:4]))
