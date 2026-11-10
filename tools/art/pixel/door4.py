import math
from px2 import *
from door2 import fb
def seg(px,py,ax,ay,bx,by):
    dx,dy=bx-ax,by-ay; t=max(0,min(1,((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy))); return math.hypot(px-(ax+t*dx),py-(ay+t*dy)),t
def door_rapier():
    L0,L1=20,64
    W=lambda s:2.5-.7*(s-L0)/(L1-L0)
    def blade_layer(s,c,x,y):
        if s<L0 or s>L1+16: return None
        w=W(s) if s<=L1 else W(L1)*max(0,(1-(s-L1)/16))**.8
        if abs(c)>w: return None
        t=c/w
        n=fb(s*.14,c*.4,3); neb=max(0,n-.4)*2.4
        base=lerp((6,8,24),(40,52,140),neb); base=lerp(base,(90,52,156),max(0,fb(s*.09+4,c*.4,7)-.55)*1.7)
        if t<-.62: return lerp((110,140,225),(200,216,252),(-t-.62)/.38)
        if t>.7: return (6,8,22)
        if abs(c)<.26 and s>29: return (225,238,255)
        h=hsh(int(s),int(c),11)
        if h<.13: return (240,244,255)
        if h<.22: return (130,180,255)
        if h<.25: return (190,160,255)
        return lerp(base,(70,92,180),.35) if t<0 else base
    def wing(sgn):
        pts=[(21.2,0),(20.0,sgn*3.4),(17.6,sgn*6.6),(14.2,sgn*8.6)]
        def L(s,c,x,y):
            best=9
            for i in range(3):
                d,t=seg(s,c,pts[i][0],pts[i][1],pts[i+1][0],pts[i+1][1]); 
                th=1.6-.35*(i+t)
                if d<th: 
                    k=d/th; return lerp((255,236,170),(130,94,40),max(0,(k-.1))*.9+ (0.12 if sgn>0 else 0))
        return L
    def keyhole(s,c,x,y):
        if math.hypot(s-27.2,c)<1.15: return (4,4,12)
        if 24.6<=s<=27.2 and abs(c)<=.55+(s-24.6)*.12: return (4,4,12)
        if math.hypot(s-27.2,c)<1.9 and False: return None
    def boss(s,c,x,y):
        # tiny arched doorway in a brass frame
        top=21.6
        inside=(19.2<=s<=top and abs(c)<=1.5) or math.hypot(s-top,c)<=1.5
        frame=(18.6<=s<=top+.8 and abs(c)<=2.5) or math.hypot(s-top-.2,c)<=2.5
        if inside: return (10,8,22) if abs(c)>.5 else (230,240,255)
        if frame: return lerp((255,240,176),(150,108,48),(c+2.5)/5)
    layers=[blade_layer,keyhole,wing(-1),wing(1),boss,
      grip(6.2,18.4,1.7,(66,42,44),(30,20,26)),
      rect(6.0,6.9,2.2,(212,176,92)),rect(12.0,12.8,2.1,(212,176,92)),
      disc(4.0,0,3.0,lambda d,s,c,x,y:lerp((255,244,190),(120,84,36),min(1,math.hypot(s-3.2,c+0.9)/4.6))),disc(3.4,-1.0,.8,(255,252,225))]
    return render(layers)
if __name__=='__main__':
    im=door_rapier(); im.save('/tmp/px/door4.png')
    big=im.resize((N*10,N*10),Image.NEAREST); bg=Image.new('RGB',big.size,(54,66,58)); bg.paste(big,(0,0),big); bg.save('/tmp/px/door4_big.png')
