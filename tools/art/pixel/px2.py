import math, random
from PIL import Image, ImageDraw
N=64; K=7
S2=math.sqrt(.5)
O=(4.0,60.0)
def sc(x,y):
    dx,dy=x+.5-O[0],y+.5-O[1]; return dx*S2-dy*S2, dx*S2+dy*S2     # s along blade (up-right), c across (+ = lower-right)
def lerp(a,b,t): t=max(0,min(1,t)); return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def hsh(x,y,k=0): return (math.sin(x*12.9898+y*78.233+k*37.7)*43758.5453)%1
class Pal:
    def __init__(s,rim,light,mid,dark,ridge,edge): s.rim,s.light,s.mid,s.dark,s.ridge,s.edge=rim,light,mid,dark,ridge,edge
def bladeshade(t,pal,s=0,ds=0):
    a=abs(t)
    if a>.88: return pal.rim if t<0 else pal.edge
    if a<.1: return pal.ridge
    if t<0: return lerp(pal.mid,pal.light,(-t-.1)/.5)
    return lerp(pal.mid,pal.dark,(t-.1)/.7)
def blade(s0,s1,wf,pal,tip=7,cf=lambda s:0,tex=None):
    def L(s,c,x,y):
        if s<s0 or s>s1+tip: return None
        w=wf(s) if s<=s1 else wf(s1)*max(0,(1-(s-s1)/tip))**.8
        cc=c-cf(s)
        if w<=.2 or abs(cc)>w: return None
        col=bladeshade(cc/w,pal,s)
        return tex(s,cc,x,y,col,w) if tex else col
    return L
def rect(s0,s1,w,col,wf=None):
    def L(s,c,x,y):
        if s0<=s<=s1 and abs(c)<=(wf(s) if wf else w): return col(s,c,x,y) if callable(col) else col
    return L
def disc(sc_,cc,r,col):
    def L(s,c,x,y):
        d=math.hypot(s-sc_,c-cc)
        if d<=r: return col(d,s,c,x,y) if callable(col) else col
    return L
def render(layers):
    im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
    for y in range(N):
        for x in range(N):
            s,c=sc(x,y); col=None
            for L in layers:
                r=L(s,c,x,y)
                if r is not None: col=r
            if col: px[x,y]=tuple(col)+(255,)
    return outline(im)
def outline(im,oc=(12,9,18)):
    w,h=im.size; px=im.load(); out=im.copy(); po=out.load()
    for y in range(h):
        for x in range(w):
            if px[x,y][3]==0:
                for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    X,Y=x+dx,y+dy
                    if 0<=X<w and 0<=Y<h and px[X,Y][3]>0: po[x,y]=oc+(255,); break
    return out
def grip(s0,s1,w,c1,c2,wrap=True):
    def f(s,c,x,y):
        t=c/w; base=lerp(c1,c2,(t+1)/2)
        if wrap and int((s+abs(c)*.6)/2.2)%2==0: base=lerp(base,(0,0,0),.25)
        return base
    return rect(s0,s1,w,f)
def stars(n,seed,smin,smax,cw,cols,sw=None):
    r=random.Random(seed); pts=[(r.uniform(smin,smax),r.uniform(-1,1),r.choice(cols),r.random()<.25) for _ in range(n)]
    def L(s,c,x,y):
        if s<smin or s>smax: return None
        for (ps,pc,col,big) in pts:
            w=(sw(ps) if sw else cw)
            ds,dc=s-ps,c-pc*w*.8
            if (abs(ds)<.75 and abs(dc)<.75) or (big and ((abs(ds)<.75 and abs(dc)<1.9) or (abs(dc)<.75 and abs(ds)<1.9))): return col
    return L
# ---------------- designs ----------------
def door():
    w=lambda s:5.6 if s<60 else 5.6
    pal=Pal((120,130,190),(30,36,72),(14,16,36),(6,7,18),(54,90,170),(4,4,12))
    def tex(s,c,x,y,col,w):
        if hsh(x,y)<.05: return (200,215,255)
        return col
    layers=[blade(24,64,lambda s:5.8-.5*(s-24)/40,pal,tip=12,tex=tex),
      stars(46,5,26,76,5.0,[(255,255,255),(255,255,255),(120,180,255),(170,150,255)],sw=lambda s:5.2),
      # door-frame crossguard: tall frame with dark door opening and arched top
      rect(19,24,13,lambda s,c,x,y:(70,84,150) if abs(c)>9 or s<20.2 or s>22.8 else (8,9,22)),
      rect(19.2,23.8,9.2,lambda s,c,x,y:(10,12,30) if abs(c)<7 else (24,28,56)),
      disc(21.5,0,2.2,(230,200,120)),rect(21.2,21.8,.6,(12,9,18)),
      grip(7,19,2.8,(52,34,30),(30,20,24)),
      disc(4.8,0,3.8,lambda d,s,c,x,y:(235,205,125) if d>1.7 else (14,10,20)),rect(3.4,6.4,.7,(14,10,20))]
    return render(layers)
def shadow():
    pal=Pal((190,140,255),(120,70,210),(70,42,130),(30,18,60),(205,150,255),(14,8,30))
    r=random.Random(11)
    def tex(s,c,x,y,col,w):
        return col
    def wisps(s,c,x,y):
        if s<26 or s>74: return None
        w=5.4-.3*(s-26)/48
        if abs(c)<=w: return None
        k=hsh(int(s*.7),int(c>0),3)
        reach=w+1+4*k*(1-(s-26)/60)
        if abs(c)<reach and hsh(x,y,2)<.55-.4*(abs(c)-w)/6: return lerp((90,50,170),(30,18,60),(abs(c)-w)/6)
    layers=[wisps,blade(22,66,lambda s:5.6-.4*(s-22)/44,pal,tip=11,tex=tex),
      rect(18,23.5,12,lambda s,c,x,y:lerp((22,16,40),(60,40,100),abs(c)/12)),
      disc(20.7,0,2.6,lambda d,s,c,x,y:lerp((235,190,255),(120,60,200),d/2.6)),
      grip(7,18,2.6,(30,22,48),(16,12,28)),disc(4.6,0,3.6,lambda d,s,c,x,y:lerp((130,80,210),(36,24,70),d/3.6))]
    return render(layers)
def sun():
    pal=Pal((255,252,210),(255,236,140),(250,196,60),(196,120,24),(255,255,225),(120,60,10))
    def guard(d,s,c,x,y):
        return (255,214,92) if d>7.2 else (lerp((255,240,160),(236,150,30),(d/6))) 
    def rays(s,c,x,y):
        d=math.hypot(s-22,c); 
        if 7.2<d<=11:
            a=math.atan2(c,s-22); k=(a*8/math.pi)%2
            if k<.55+(11-d)*.12: return (255,222,100) if d<10 else (255,246,170)
    layers=[blade(26,70,lambda s:6.2-.5*(s-26)/44,pal,tip=10),rays,disc(22,0,7.4,guard),disc(22,0,3.4,lambda d,s,c,x,y:lerp((255,255,230),(255,200,70),d/3.4)),
      grip(7,15.5,2.6,(176,40,30),(100,22,22)),disc(4.5,0,3.5,lambda d,s,c,x,y:lerp((255,230,120),(200,120,20),d/3.5))]
    return render(layers)
def storm():
    pal=Pal((240,250,255),(200,224,248),(150,182,222),(96,120,170),(255,255,255),(40,52,86))
    cf=lambda s:-0.0024*(s-20)**2
    def tex(s,c,x,y,col,w):
        line=1.6+.9*math.sin(s*.45)+.5*math.sin(s*1.3)
        if c>0 and c<line+1.0 and c>line-.7: return lerp(col,(255,255,255),.55)
        if hsh(int(s),int(c*2),9)<.045: return (120,230,255)
        return col
    def bolt(s,c,x,y):
        for k in range(6):
            s0=26+k*8.5; t=s-s0
            if 0<=t<5.5:
                cc=cf(s)+(1 if k%2 else -1)*(2.4+math.sin(t*1.7)*1.8)+ (t*.35)
                if abs(c-cc)<.65: return (255,250,140) if hsh(x,y)<.5 else (140,235,255)
    layers=[blade(20,74,lambda s:3.9-.25*(s-20)/54,pal,tip=9,cf=cf,tex=tex),bolt,
      disc(17.8,0,7.4,lambda d,s,c,x,y:(212,170,60) if d>6.0 else ((16,18,36) if abs(math.hypot(s-17.8,c)-3.6)>1.1 or hsh(int(s),int(c))>0.2 else (36,40,80))),
      grip(5.5,16,2.2,(24,28,80),(14,16,50)),disc(4,0,2.7,(212,170,60))]
    return render(layers)
def bone():
    pal=Pal((255,252,232),(240,230,196),(210,196,156),(150,134,100),(255,255,240),(70,58,40))
    def tex(s,c,x,y,col,w):
        if hsh(int(s*.5),int(c*.5),4)<.06 and abs(c)<w*.8: return (96,84,60)
        return col
    def fire(s,c,x,y):
        if s<26 or s>76: return None
        w=7.0-.4*(s-26)/50
        if abs(c)<=w+0.2 and abs(c)>1.5 and hsh(int(s*.6),int(c*.6),6)<.38: 
            t=(s-26)/50; return lerp((40,200,90),(170,255,170),hsh(x,y,3))
        if abs(c)>w and abs(c)<w+4:
            h=hsh(int(s*.55),1 if c>0 else 0,5)*5
            if abs(c)-w<h*(.9-.0) and s+(abs(c)-w)*1.0>0: return lerp((40,200,90),(180,255,170),(abs(c)-w)/5)
    layers=[blade(22,66,lambda s:7.0-.5*(s-22)/44,pal,tip=12,tex=tex),fire,
      rect(18,22.5,16,lambda s,c,x,y:lerp((214,202,164),(150,134,100),abs(c)/16),wf=lambda s:16-abs(s-20.2)*1.2),
      grip(8,18,2.6,(92,56,40),(58,34,26)),
      disc(5.2,0,4.6,lambda d,s,c,x,y:(244,236,208)),rect(3.4,5.4,1.4,(14,10,18)),disc(4.2,-1.7,1.0,(14,10,18)),disc(4.2,1.7,1.0,(14,10,18))]
    return render(layers)
def fortune():
    pal=Pal((255,244,196),(246,208,120),(214,164,68),(140,98,34),(255,248,215),(80,50,14))
    cols=[(210,40,50),(40,70,200),(244,204,60),(40,70,200),(210,40,50),(244,204,60),(40,70,200),(210,40,50)]
    def wheel(d,s,c,x,y):
        if d>6.4: return (150,100,32) if d<8.2 else (236,190,80)
        a=(math.atan2(c,s-21)+math.pi)/(2*math.pi)
        if abs((a*8)%1-.5)>.46 or abs(d-3.8)<.5 or d<1.6: return (250,226,150)
        return lerp(cols[int(a*8)%8],(255,255,255),.15*(1-d/6.4))
    def snake(s,c,x,y):
        if s<30 or s>74: return None
        cc=4.2*math.sin(s*.42)
        if abs(c-cc)<1.5: 
            sq=(int(s*1.0)+int(c*2))%2
            return (40,170,80) if sq else (20,110,52)
    def head(s,c,x,y):
        d=math.hypot(s-77,c-0.0)
        if d<2.4: return (50,190,95)
        if math.hypot(s-77.8,c+0.8)<.7 or math.hypot(s-77.8,c-0.8)<.7: return (255,220,60)
    layers=[blade(26,62,lambda s:5.2-.3*(s-26)/36,pal,tip=12),snake,head,disc(21,0,8.4,wheel),
      grip(6,13,2.6,(110,44,34),(70,28,24)),disc(4.2,0,3.3,lambda d,s,c,x,y:lerp((250,220,120),(190,130,40),d/3.3))]
    return render(layers)
def chained():
    pal=Pal((170,160,210),(90,80,140),(48,42,82),(22,18,40),(130,120,190),(10,8,20))
    def tex(s,c,x,y,col,w):
        k=((s-28)%10)
        if s>28 and s<70 and k<2.0 and abs(c+(s-28)*0.0)<w*.9: return (176,176,196) if (int(s)+int(c))%2 else (96,96,116)
        return col
    def hang(s,c,x,y):
        for i in range(4):
            d=math.hypot(s-(15-i*2.8),c-(10.5+i*2.4)*1.0)
            if 1.4<d<2.4: return (150,150,170)
    layers=[blade(24,66,lambda s:5.4-.4*(s-24)/42,pal,tip=11,tex=tex),hang,
      rect(18.5,23,12,lambda s,c,x,y:(62,54,96)),disc(20.7,-12,3.1,lambda d,s,c,x,y:(150,150,170) if d>1.6 else (12,9,18)),disc(20.7,12,3.1,lambda d,s,c,x,y:(150,150,170) if d>1.6 else (12,9,18)),
      disc(20.7,0,2.2,lambda d,s,c,x,y:lerp((255,150,160),(190,24,48),d/2.2)),
      grip(7,18,2.6,(30,24,38),(18,14,26)),disc(4.5,0,3.5,lambda d,s,c,x,y:lerp((255,140,150),(170,20,44),d/3.5))]
    return render(layers)
def twilight():
    def tex(s,c,x,y,col,w):
        t=(s-22)/50; return lerp(col,(255,226,150),max(0,t-.45)*.9) if c>-w*.4 else lerp(col,(255,226,170),max(0,t-.3)*.6)
    pal=Pal((255,226,150),(255,170,70),(240,100,30),(160,42,18),(255,236,170),(90,24,10))
    layers=[blade(22,64,lambda s:7.4-.9*(s-22)/42,pal,tip=13,tex=tex),
      rect(18,22.5,15,lambda s,c,x,y:lerp((250,205,92),(180,120,30),abs(c)/15),wf=lambda s:15-abs(s-20.2)*.9),
      disc(20.2,-15,1.6,(255,226,130)),disc(20.2,15,1.6,(255,226,130)),
      grip(7,17.5,2.6,(120,34,30),(74,20,20)),disc(4.6,0,3.7,lambda d,s,c,x,y:lerp((255,226,130),(190,130,40),d/3.7))]
    return render(layers)
def quill():
    pal=Pal((255,255,255),(244,244,252),(214,214,236),(150,150,196),(120,120,170),(80,80,130))
    def edge(s):
        return 5.4*math.sin(min(1,(s-24)/12)*math.pi/2)*(1-max(0,(s-60))/18)+ (0.9 if int(s)%3==0 else 0)
    def tex(s,c,x,y,col,w):
        if abs(c)<.9: return (120,120,172)
        if (int(s*1.0+abs(c)*1.4))%4==0 and abs(c)>1.3: return lerp(col,(150,150,196),.45)
        return col
    layers=[blade(22,72,lambda s:max(.3,5.8*math.sin(min(1,(s-22)/16)*math.pi/2)*(1-max(0,s-58)/22)**.7+.6*((int(s)%3)==0)),pal,tip=4,tex=tex),
      disc(75,0,1.6,(26,26,66)),
      rect(19,22.5,8,lambda s,c,x,y:lerp((84,84,150),(40,40,96),abs(c)/8)),disc(20.7,0,1.8,(222,192,70)),
      grip(7,18.5,2.2,(40,40,86),(28,28,64)),disc(4.6,0,3.2,lambda d,s,c,x,y:lerp((100,100,176),(40,40,92),d/3.2)),disc(4.6,0,1.1,(222,192,70))]
    return render(layers)
ITEMS=[('Door (starry sniper)',door),('Shadow sword',shadow),('Sun (spear of sun)',sun),('Storm katana',storm),('Bone greatsword',bone),('Wheel of Fortune',fortune),('Chained (bow)',chained),('Twilight longsword',twilight),('Quill of Alzuhod',quill)]
if __name__=='__main__':
    cols=3; rows=3; W=cols*(N*K+20)+20; H=rows*(N*K+36)+20
    sheet=Image.new('RGB',(W,H),(54,66,58)); d=ImageDraw.Draw(sheet)
    for i,(n,f) in enumerate(ITEMS):
        im=f(); im.save(f'/tmp/px/v2_{i}.png')
        big=im.resize((N*K,N*K),Image.NEAREST); x=20+(i%cols)*(N*K+20); y=20+(i//cols)*(N*K+36)
        sheet.paste(big,(x,y+20),big); d.text((x,y+2),n,fill=(240,240,240))
    sheet.save('/tmp/px/sheet2.png')
