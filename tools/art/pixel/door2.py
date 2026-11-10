import math, random
from px2 import *
def vn(x,y,k=0):
    xi,yi=math.floor(x),math.floor(y); xf,yf=x-xi,y-yi
    f=lambda t:t*t*(3-2*t)
    a=hsh(xi,yi,k);b=hsh(xi+1,yi,k);c=hsh(xi,yi+1,k);d=hsh(xi+1,yi+1,k)
    return (a*(1-f(xf))+b*f(xf))*(1-f(yf))+(c*(1-f(xf))+d*f(xf))*f(yf)
def fb(x,y,k=0): return .5*vn(x,y,k)+.3*vn(x*2.1,y*2.1,k+1)+.2*vn(x*4.3,y*4.3,k+2)
def door_sword():
    W=lambda s:6.4-1.1*(s-26)/40            # broad longsword taper
    NAVY=(8,10,26)
    def blade_layer(s,c,x,y):
        if s<24 or s>80: return None
        w=W(s) if s<=60 else W(60)*max(0,(1-(s-60)/20))**.7
        if abs(c)>w: return None
        t=c/w
        # deep night base with a drifting nebula
        n=fb(s*.11,c*.2,3); neb=max(0,n-.42)*2.2
        base=lerp((6,8,22),(34,40,110),neb)
        base=lerp(base,(70,40,120),max(0,fb(s*.08+4,c*.25,7)-.55)*1.6)
        # bevels: lit upper-left face, dark lower-right face, centre fuller line
        if abs(t)>.9: return (150,170,230) if t<0 else (4,5,14)
        if abs(t)<.08: return (86,120,210)
        if abs(t)<.3: base=lerp(base,(0,0,6),.35)                    # recessed fuller
        elif t<0: base=lerp(base,(60,76,150),(-t-.3)*.5)
        else: base=lerp(base,(2,3,10),(t-.3)*.5)
        # stars: tiny, many, varied; a few cross-shaped sparkles
        h=hsh(int(s*1.0),int(c*1.0),11)
        if h<.045: return (235,240,255)
        if h<.075: return (120,170,255)
        if h<.085: return (180,150,255)
        return base
    def sparkle(s,c,x,y):
        r=random.Random(8)
        for _ in range(4):
            ps=r.uniform(32,66); w=W(ps); pc=r.uniform(-.6,.6)*w
            ds,dc=s-ps,c-pc
            if (abs(ds)<.8 and abs(dc)<.8) or (abs(ds)<.6 and abs(dc)<1.9) or (abs(dc)<.6 and abs(ds)<1.9): return (255,255,255) if abs(ds)+abs(dc)<1.0 else (150,190,255)
    # guard: a tall door frame (arched), brass, with a dark door and a little handle
    def guard(s,c,x,y):
        if not (16.5<=s<=24 and abs(c)<=14): return None
        # frame
        inner= (18.2<=s<=22.4 and abs(c)<=9.8)
        if not inner:
            t=(c+14)/28; col=lerp((250,222,150),(120,86,38),t)
            if s<17.4 or s>23.2 or abs(c)>13: col=lerp(col,(0,0,0),.25)
            return col
        # door panel: dark wood with a lit crack of starlight, hinges and a knob
        col=lerp((30,18,22),(54,34,40),(c+10)/20)
        if abs(c)<.7: col=(200,215,255)          # light spilling through the doorway
        if abs(c-5.4)<.9 and abs(s-20.3)<1.0: col=(250,222,150)    # knob
        if abs(c+8.6)<.9 and (abs(s-18.9)<.9 or abs(s-21.7)<.9): col=(176,146,84)  # hinges
        return col
    def arch(s,c,x,y):
        d=math.hypot(s-20.3,c); 
        if 0<=s and False: return None
    layers=[blade_layer,sparkle,guard,
      grip(7.5,16.5,2.6,(70,44,40),(36,22,26)),
      disc(5.2,0,4.0,lambda d,s,c,x,y:(252,226,150) if d>2.0 else (8,6,16)),
      rect(3.6,5.0,.8,(8,6,16)),disc(5.6,0,1.1,(8,6,16))]
    return render(layers)
if __name__=='__main__':
    im=door_sword(); im.save('/tmp/px/door2.png')
    big=im.resize((N*10,N*10),Image.NEAREST); bg=Image.new('RGB',big.size,(54,66,58)); bg.paste(big,(0,0),big); bg.save('/tmp/px/door2_big.png')
