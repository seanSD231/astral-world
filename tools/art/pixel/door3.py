import math, random
from px2 import *
from door2 import fb
def door_short():
    L0,L1=22,60                       # short blade
    W=lambda s:3.9-.6*(s-L0)/(L1-L0)
    def blade_layer(s,c,x,y):
        if s<L0 or s>L1+14: return None
        w=W(s) if s<=L1 else W(L1)*max(0,(1-(s-L1)/14))**.75
        cc=c+ (0.0)
        if abs(cc)>w: return None
        t=cc/w
        n=fb(s*.13,c*.3,3); neb=max(0,n-.4)*2.4
        base=lerp((6,8,24),(38,48,130),neb); base=lerp(base,(86,50,150),max(0,fb(s*.09+4,c*.3,7)-.55)*1.7)
        if abs(t)>.86: return (170,190,245) if t<0 else (5,6,16)
        if abs(t)<.12: return (104,140,230)
        if t<0: base=lerp(base,(70,90,170),(-t-.12)*.55)
        else: base=lerp(base,(2,3,10),(t-.12)*.5)
        h=hsh(int(s),int(c),11)
        if h<.05: return (240,244,255)
        if h<.085: return (130,180,255)
        if h<.1: return (190,160,255)
        return base
    def trails(s,c,x,y):                 # faint star-trail streaks behind the blade: speed
        if s<26 or s>70: return None
        for k,(off,ln,s0) in enumerate(((6.0,22,30),(-5.6,16,38),(7.8,10,50))):
            if s0<=s<=s0+ln and abs(c-off)<.5: 
                t=(s-s0)/ln; return lerp((46,60,120),(200,220,255),t**1.5)
    def guard(s,c,x,y):                  # small door-frame guard, slightly swept
        if not (17<=s<=21.6 and abs(c)<=8.6): return None
        inner=(18.4<=s<=20.2 and abs(c)<=5.6)
        if not inner:
            col=lerp((252,226,156),(124,90,40),(c+8.6)/17.2)
            if abs(c)>8.0 or s>21.0 or s<17.6: col=lerp(col,(0,0,0),.22)
            return col
        return (12,8,20) if abs(c)>.6 else (206,222,255)         # the doorway, a sliver of light
    layers=[trails,blade_layer,guard,
      grip(8,17,2.3,(62,40,40),(30,20,26)),
      disc(5.6,0,3.5,lambda d,s,c,x,y:(252,226,156) if d>1.8 else (8,6,16)),rect(3.9,5.4,.7,(8,6,16)),disc(6.0,0,.9,(8,6,16))]
    return render(layers)
if __name__=='__main__':
    im=door_short(); im.save('/tmp/px/door3.png')
    big=im.resize((N*10,N*10),Image.NEAREST); bg=Image.new('RGB',big.size,(54,66,58)); bg.paste(big,(0,0),big); bg.save('/tmp/px/door3_big.png')
