import math, random
from PIL import Image
N=128; F=2.0; S2=math.sqrt(.5); O=(8.0,120.0); FR=8
def lerp(a,b,t): t=max(0,min(1,t)); return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def hsh(x,y,k=0): return (math.sin(x*12.9898+y*78.233+k*37.7)*43758.5453)%1
def vn(x,y,k=0):
    xi,yi=math.floor(x),math.floor(y); xf,yf=x-xi,y-yi; f=lambda t:t*t*(3-2*t)
    a=hsh(xi,yi,k);b=hsh(xi+1,yi,k);c=hsh(xi,yi+1,k);d=hsh(xi+1,yi+1,k)
    return (a*(1-f(xf))+b*f(xf))*(1-f(yf))+(c*(1-f(xf))+d*f(xf))*f(yf)
def fb(x,y,k=0): return .5*vn(x,y,k)+.3*vn(x*2.1,y*2.1,k+1)+.2*vn(x*4.3,y*4.3,k+2)
def sc(x,y): dx,dy=x+.5-O[0],y+.5-O[1]; return (dx*S2-dy*S2)/F,(dx*S2+dy*S2)/F
def seg(px,py,ax,ay,bx,by):
    dx,dy=bx-ax,by-ay; t=max(0,min(1,((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy))); return math.hypot(px-(ax+t*dx),py-(ay+t*dy)),t
L0,L1=20,64
W=lambda s:2.5-.7*(s-L0)/(L1-L0)
rs=random.Random(21)
STARS=[(rs.uniform(30,70),rs.uniform(-.8,.8),rs.random(),rs.random()<.2) for _ in range(34)]
CONST=[(33,-.6),(39,.5),(45,-.3),(51,.6),(57,-.2),(62,.3)]
def blade(s,c,x,y,t):
    if s<L0 or s>L1+16: return None
    w=W(s) if s<=L1 else W(L1)*max(0,(1-(s-L1)/16))**.8
    if abs(c)>w: return None
    u=c/w
    a=math.cos(2*math.pi*t); b=math.sin(2*math.pi*t)
    n=a*fb(s*.14,c*.5,3)+b*fb(s*.14,c*.5,13); n=.5+.5*n if False else (n*.5+.5*abs(a)+.0)*1.0
    n=(a*fb(s*.14,c*.5,3)+b*fb(s*.14,c*.5,13))*.7+fb(s*.1,c*.5,23)*.5
    neb=max(0,n-.42)*2.3
    base=lerp((5,7,22),(40,54,150),neb); base=lerp(base,(96,54,160),max(0,fb(s*.09+4,c*.5,7)+.15*b-.55)*1.8)
    # bevels
    if u<-.7: return lerp((100,130,222),(205,220,252),(-u-.7)/.3)
    if u>.76: return (5,6,18)
    if -.7<=u<-.62: return (30,40,100)            # etched groove next to the lit edge
    col=lerp(base,(70,92,180),.3) if u<0 else base
    # constellation lines
    for i in range(len(CONST)-1):
        d,_=seg(s,c,CONST[i][0],CONST[i][1]*w*.8,CONST[i+1][0],CONST[i+1][1]*w*.8)
        if d<.34: col=lerp(col,(120,160,255),.55)
    # fine dust stars
    h=hsh(int(s*2),int(c*2),11)
    if h<.05: col=(215,225,250)
    elif h<.09: col=(110,150,235)
    elif h<.1: col=(180,150,250)
    # light crack down the middle, pulsing toward the tip
    if abs(c)<.2 and s>29:
        p=.55+.45*math.sin(2*math.pi*(s*.1-t)); return lerp((120,170,255),(240,248,255),p)
    return col
def stars(s,c,x,y,t):
    for (ps,pc,ph,big) in STARS:
        w=W(ps)
        if abs(c-pc*w)>w: continue
        ds,dc=s-ps,c-pc*w
        br=.25+.75*max(0,math.sin(2*math.pi*(t+ph)))**2
        if abs(ds)<.28 and abs(dc)<.28: return lerp((20,30,80),(255,255,255),br)
        if big and br>.5 and ((abs(ds)<.28 and abs(dc)<1.4*br) or (abs(dc)<.28 and abs(ds)<1.4*br)): return lerp((60,100,200),(210,230,255),br)
def keyhole(s,c,x,y,t):
    g=.5+.5*math.sin(2*math.pi*t)
    d=math.hypot(s-27.2,c)
    if d<1.2: return (3,3,10)
    if 24.4<=s<=27.2 and abs(c)<=.5+(s-24.4)*.13: return (3,3,10)
    if d<1.6 and s>26: return lerp((20,30,90),(150,190,255),g)
def wing(sg):
    pts=[(21.2,0),(20.0,sg*3.4),(17.6,sg*6.6),(14.2,sg*8.6)]
    def L(s,c,x,y,t):
        for i in range(3):
            d,tt=seg(s,c,pts[i][0],pts[i][1],pts[i+1][0],pts[i+1][1]); th=1.6-.35*(i+tt)
            if d<th:
                k=d/th; col=lerp((255,238,176),(128,92,40),max(0,k-.1)*.9+(.12 if sg>0 else 0))
                if abs(d)<.22: col=lerp(col,(90,62,24),.5)            # engraved line along the wing
                return col
        if math.hypot(s-14.0,c-sg*8.8)<.9: return (120,190,255)        # gem on the wing tip
    return L
def boss(s,c,x,y,t):
    top=21.6
    inside=(19.2<=s<=top and abs(c)<=1.5) or math.hypot(s-top,c)<=1.5
    frame=(18.6<=s<=top+.8 and abs(c)<=2.5) or math.hypot(s-top-.2,c)<=2.5
    if inside:
        if abs(c)<.3: return lerp((200,225,255),(255,255,255),.5+.5*math.sin(2*math.pi*t))
        if abs(s-20.2)<.35 and abs(c-1.0)<.35: return (250,222,150)
        return (10,8,22)
    if frame: return lerp((255,240,176),(150,108,48),(c+2.5)/5)
def grip(s,c,x,y,t):
    if 6.6<=s<=18.4 and abs(c)<=1.7:
        base=lerp((26,36,96),(8,12,38),(c+1.7)/3.4)
        ph=(s+c*1.15)*1.7
        if int(ph)%3==0: base=lerp(base,(0,0,10),.4)               # cord grooves
        if abs(((s*1.1+c*1.6)%2.6)-1.3)<.2: base=(196,206,232)      # silver wire spiral
        return base
    for b0 in (6.2,12.3,17.7):                                      # silver collars
        if b0<=s<=b0+.8 and abs(c)<=2.0: return lerp((236,240,252),(120,130,160),(c+2)/4)
    if 5.0<=s<6.2 and abs(c)<=2.3-(6.2-s)*1.3: return lerp((255,238,176),(128,92,40),(c+2.3)/4.6)   # gold collar
def pommel(s,c,x,y,t):
    ds,dc=s-3.0,c
    a,b=3.0,2.5
    r=abs(ds)/a+abs(dc)/b
    if r>1.0: return None
    if r>.82: return lerp((255,238,176),(128,92,40),(dc+b)/(2*b))      # gold setting
    # four facets: tip (toward blade), tail (toward the end), left/right
    q=r/.82
    if abs(ds)/a>abs(dc)/b:
        col=(150,196,255) if ds>0 else (60,90,200)
    else:
        col=(110,150,240) if dc<0 else (30,48,140)
    col=lerp(col,(235,245,255),max(0,1-q)*.55)
    g=.5+.5*math.sin(2*math.pi*t)
    if abs(ds)<.5 and abs(dc)<.5: col=lerp((200,225,255),(255,255,255),g)                 # glinting centre
    if abs(dc)<.2 and abs(ds)<1.6*g+.4: col=(255,255,255)
    return col
LAYERS=[blade,stars,keyhole,wing(-1),wing(1),boss,grip,pommel]
def render(t):
    im=Image.new('RGBA',(N,N),(0,0,0,0)); px=im.load()
    for y in range(N):
        for x in range(N):
            s,c=sc(x,y); col=None
            for L in LAYERS:
                r=L(s,c,x,y,t)
                if r is not None: col=r
            if col: px[x,y]=tuple(col)+(255,)
    out=im.copy(); po=out.load(); w,h=im.size
    for y in range(h):
        for x in range(w):
            if px[x,y][3]==0:
                for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    X,Y=x+dx,y+dy
                    if 0<=X<w and 0<=Y<h and px[X,Y][3]>0: po[x,y]=(12,9,18,255); break
    return out
if __name__=='__main__':
    frames=[render(i/FR) for i in range(FR)]
    K=6; big=[f.resize((N*K,N*K),Image.NEAREST) for f in frames]
    bgs=[]
    for b in big:
        bg=Image.new('RGB',b.size,(54,66,58)); bg.paste(b,(0,0),b); bgs.append(bg)
    bgs[0].save('/tmp/px/door5.gif',save_all=True,append_images=bgs[1:],duration=120,loop=0)
    bgs[0].save('/tmp/px/door5_still.png')
    strip=Image.new('RGBA',(N,N*FR))
    for i,f in enumerate(frames): strip.paste(f,(0,i*N))
    strip.save('/tmp/px/door5_strip.png')
