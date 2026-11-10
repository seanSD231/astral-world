import sys; sys.path.insert(0,'/tmp/r3')
from eng import *
from models import aura,bloom,starfield,stripes
PI=math.pi
def C(*a): return np.array(a,float)
def chain(sc,pts,r0,r1,mat):
    n=len(pts)-1
    for i,(a,b) in enumerate(zip(pts,pts[1:])): sc.add(capsule(a,b,r0+(r1-r0)*i/n,r0+(r1-r0)*(i+1)/n),mat)
def bez3(p0,p1,p2,p3,n=14): 
    p0,p1,p2,p3=[np.asarray(q,float) for q in (p0,p1,p2,p3)]; return [((1-t)**3)*p0+3*(1-t)**2*t*p1+3*(1-t)*t*t*p2+t**3*p3 for t in np.linspace(0,1,n)]
def bez2(p0,p1,p2,n=12):
    p0,p1,p2=[np.asarray(q,float) for q in (p0,p1,p2)]; return [((1-t)**2)*p0+2*(1-t)*t*p1+t*t*p2 for t in np.linspace(0,1,n)]
def smoothstep(a,b,x): t=np.clip((x-a)/(b-a),0,1); return t*t*(3-2*t)

# ======================= REVOLVER (inches) =======================
def revolver():
    sc=Scene()
    def sky_base(p):
        n=fbm(p*.5+1.5); neb=np.clip(n*1.9-.7,0,1)
        c=np.array([.02,.03,.08])+np.array([.05,.16,.6])*neb[:,None]*1.6
        c=c+np.array([.35,.1,.5])*np.clip(fbm(p*.7+8)*2-1.2,0,1)[:,None]*.5
        return c
    def stars(p): return starfield(p,2.6,.93)+starfield(p+2.7,4.5,.95)*.8
    body=Mat(base=lambda p: sky_base(p)+stars(p)[:,None]*np.array([1,1,1.0])*1.2,metal=.85,gloss=110,envk=.8,emis=lambda p: np.array([.55,.7,1.0])*(stars(p)*1.1)[:,None])
    silver=Mat(base=(.7,.76,.92),metal=1.0,gloss=130,envk=1.0)
    wood=Mat(base=lambda p:(np.array([.03,.035,.09])+np.array([.05,.12,.34])*np.clip(fbm(p*.4+2)*2.2-.8,0,1)[:,None])+stars(p)[:,None]*1.2,metal=.3,gloss=70,envk=.5,emis=lambda p: np.array([.55,.7,1.0])*(stars(p)*.9)[:,None])
    glowb=Mat(base=(.3,.5,1),metal=0,gloss=10,emis=lambda p:np.array([.25,.55,1.0])*1.4,ao=0)
    # barrel (round, slightly tapered) and muzzle
    sc.add(cyl((10.2,1.78,0),.36,5.2,roty(PI/2)),body)
    for k in range(14): sc.add(torus((6.2+k*.62,1.78,0),.4,.05,roty(PI/2)),silver)
    sc.add(cyl((15.0,1.78,0),.43,.22,roty(PI/2),r=.05),silver); sc.add(cyl((5.6,1.78,0),.44,.2,roty(PI/2),r=.05),silver)
    sc.add(cyl((9.4,1.78,0),.385,.07,roty(PI/2)),silver)
    sc.add(rbox((14.6,2.28,0),(.14,.22,.06),.03),silver)            # front sight blade
    sc.add(rbox((10.3,2.16,0),(4.7,.025,.08),.01),silver)               # inlaid rib line
    # ejector rod housing and rod knob
    sc.add(cyl((8.1,1.0,0),.2,2.75,roty(PI/2)),body); sc.add(sphere((10.95,1.0,0),.24),silver)
    sc.add(rbox((5.9,1.4,0),(.16,.42,.2),.04),body); sc.add(rbox((8.3,1.4,0),(.12,.42,.18),.03),body)
    # frame
    sc.add(poly([(2.7,2.55),(3.5,2.55),(3.5,.55),(3.1,.3),(2.75,.85)],.42,r=.14),body)         # recoil shield
    sc.add(rbox((4.35,2.38,0),(1.7,.2,.42),.1),body)                                           # top strap
    sc.add(rbox((4.45,.55,0),(1.55,.24,.42),.1),body)                                          # bottom rail
    sc.add(rbox((5.55,1.5,0),(.4,1.05,.42),.12),body)                                          # front block
    sc.add(rbox((4.35,2.62,0),(1.5,.03,.1),.015),silver)                                       # top-strap trim
    sc.add(rbox((3.6,2.55,0),(.2,.1,.2),.02),silver)                                           # rear sight notch block
    # fluted cylinder
    def fluted(c,rad,hh):
        c=np.array(c,float)
        def f(p):
            q=p-c; r=np.sqrt(q[:,1]**2+q[:,2]**2); th=np.arctan2(q[:,2],q[:,1]); rr=rad-.11*np.clip(np.cos(6*th)*2.4-1.2,0,1)
            d=np.stack([r-rr,np.abs(q[:,0])-hh],1); return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-.05
        return f
    sc.add(fluted((4.35,1.5,0),.76,.82),body)
    sc.add(torus((3.62,1.5,0),.74,.05,roty(PI/2)),silver); sc.add(torus((5.08,1.5,0),.74,.05,roty(PI/2)),silver)
    sc.add(cyl((3.5,1.5,0),.3,.1,roty(PI/2)),silver); sc.add(sphere((3.42,1.5,0),.12),glowb)
    for k in range(6):
        a=k*PI/3+PI/6; sc.add(cyl((5.12,1.5+.52*math.sin(a),.52*math.cos(a)),.14,.05,roty(PI/2)),Mat(base=(.02,.03,.07),metal=0.,ao=1))
    # hammer
    sc.add(poly([(3.05,2.55),(2.65,2.6),(1.95,3.05),(1.85,2.72),(2.35,2.25),(2.85,1.95)],.2,r=.08),body)
    # grip
    grip=[(3.5,.65),(3.05,.9),(2.6,.35),(2.1,-.7),(1.5,-1.9),(1.05,-2.9),(1.2,-3.45),(1.75,-3.55),(2.25,-3.3),(2.6,-2.2),(3.0,-1.1),(3.45,-.15)]
    sc.add(poly(grip,.55,r=.28),wood)
    sc.add(poly([(2.2,-3.35),(1.2,-3.45),(1.05,-2.9),(1.55,-1.9),(1.7,-1.9)],.6,r=.08),silver) if False else None
    # star medallion on the grip side
    ang=math.atan2(1.7,.9)
    for sd in (-1,1):
        pts=[]
        for i in range(10):
            a=-PI/2+i*PI/5; rr=.5 if i%2==0 else .2; pts.append((2.35+rr*math.cos(a),-1.5+rr*math.sin(a)))
        sc.add(poly(pts,.04,c=(0,0,sd*.56),r=.01),silver)
    # silver scrollwork curls on the frame and a brass gear at the hammer
    brass=Mat(base=(.95,.7,.25),metal=1.0,gloss=100,envk=1.0)
    for (cx_,cy_,r_) in ((3.9,1.95,.34),(4.5,.95,.26),(5.4,.5,.2),(2.9,1.6,.26)):
        for sd in (-1,1): sc.add(torus((cx_,cy_,sd*.44),r_,.045,I3),silver)
    for sd in (-1,1):
        sc.add(cyl((2.9,2.1,sd*.34),.38,.06,I3,r=.02),brass)
        for k in range(10):
            a=k*PI/5; sc.add(sphere((2.9+.4*math.cos(a),2.1+.4*math.sin(a),sd*.34),.07),brass)
    # trigger guard and trigger
    sc.add(torus((3.7,.05,0),.62,.1,I3),body)
    sc.add(capsule((3.55,.5,0),(3.48,-.2,0),.1,.05),silver)
    return sc

# ======================= SHADOW SWORD / SHIELD =======================
def shmat(rim=(.5,.25,.9)):
    return Mat(base=lambda p: np.array([.06,.035,.12])*(.6+.8*fbm(p*.35))[:,None],metal=.45,gloss=70,rim=tuple(np.array(rim)*1.7),envk=.45,ao=0,
               emis=lambda p: np.array([.35,.15,.8])*(.05+.2*np.clip(fbm(p*.5+5)*2-.9,0,1))[:,None])
def smoke(fn,amp,f,yfade=None):
    def g(p):
        a=amp*(1.0 if yfade is None else np.clip(yfade(p),0,1))
        return fn(p)+a*(fbm(p*f)-.5)*2
    return g
def wisp(sc,m,rng,x,y,n,dirx,diry,r,spread=.5):
    pts=[(x,y,0.)]
    for s in range(n): x+=dirx+rng.randn()*spread*.4; y+=diry+rng.randn()*spread*.4; pts.append((x,y,rng.randn()*.15))
    chain(sc,pts,r,r*.25,m)
def shadow_sword():
    sc=Scene(); m=shmat(); edge=Mat(base=(.5,.3,.9),metal=0,emis=lambda p:np.array([.35,.15,.9])*1.2,ao=0)
    yf=lambda p:(p[:,1]-18)/14
    # blade: 32in long, straight, with fuller and a proper point
    sc.add(smoke(poly([(0,34),(.55,31),(1.2,26),(1.35,6),(1.35,0),(-1.35,0),(-1.35,6),(-1.2,26),(-.55,31)],.2,r=.08),.18,.4,yf),m)
    sc.add(rbox((0,16,.2),(.14,14,.02),.02),edge)                    # glowing fuller line on both faces
    sc.add(rbox((0,16,-.2),(.14,14,.02),.02),edge)
    sc.add(rbox((0,.5,0),(1.5,.55,.35),.12),m)                        # ricasso block
    # simple strong cross guard: a straight bar with rounded ends
    sc.add(capsule((-4.8,-.2,0),(4.8,-.2,0),.42,.42),m); sc.add(sphere((-5.1,-.2,0),.62),m); sc.add(sphere((5.1,-.2,0),.62),m)
    # grip with wrapping, round pommel
    sc.add(capsule((0,-.6,0),(0,-7.6,0),.55,.5),m)
    for y in np.linspace(-1.2,-7.2,9): sc.add(torus((0,y,0),.58,.1,rotx(PI/2)),Mat(base=(.02,.01,.04),metal=.3,gloss=30,rim=(.35,.2,.7),ao=0))
    sc.add(sphere((0,-8.4,0),1.05),m); sc.add(sphere((0,-8.4,.5),.5),edge) if False else None
    rng=np.random.RandomState(3)
    for k in range(7):   # shadow trailing off the blade
        side=1 if k%2 else -1; wisp(sc,m,rng,side*1.4,6+rng.rand()*24,6,side*.15,.9+rng.rand()*.6,.28)
    return sc
def shadow_shield():
    sc=Scene(); m=shmat(); edge=Mat(base=(.5,.3,.9),metal=0,emis=lambda p:np.array([.35,.15,.9])*1.0,ao=0)
    ff=lambda p: np.clip((np.abs(p[:,0])-7)/6+(-p[:,1]-5)/12,0,1)
    heater=[(-11,15),(-5,16.5),(0,15.5),(5,16.5),(11,15),(11.5,4),(9,-5),(4.5,-12),(0,-17),(-4.5,-12),(-9,-5),(-11.5,4)]
    sc.add(smoke(poly(heater,.55,r=.25),.35,.35,ff),m)
    inner=[(-9.2,13.5),(0,14),(9.2,13.5),(9.7,4),(7.5,-4),(3.8,-10.5),(0,-14.5),(-3.8,-10.5),(-7.5,-4),(-9.7,4)]
    sc.add(smoke(poly(inner,1.2,r=.5),.25,.35,ff),m)
    sc.add(capsule((0,13.5,1.1),(0,-14,1.1),.75),m)                                     # central ridge
    sc.add(capsule((-9,5,1.0),(9,5,1.0),.55),m)                                         # cross band
    sc.add(sphere((0,5,1.1),1.7),m)
    sc.add(rbox((0,13.7,1.2),(.12,.1,.05),.02),edge) if False else None
    for sd in (-1,1): sc.add(rbox((0,-4.5,1.6),(.12,9,.03),.02),edge) if sd==1 else None
    rng=np.random.RandomState(6)
    for k in range(12):
        a=rng.rand()*2*PI; x=10.5*math.cos(a); y=2+13*math.sin(a)*(.9 if math.sin(a)>0 else 1.2)
        wisp(sc,m,rng,x,y,6,math.cos(a)*.8,math.sin(a)*.8,.38,.6)
    return sc

# ======================= SUN & JUSTICE SPEAR =======================
def spear():
    sc=Scene()
    white=Mat(base=lambda p: np.array([.98,.72,.2])*(.9+.1*np.sin(p[:,1]*3+p[:,0]*8))[:,None],metal=.95,gloss=95,envk=.8,bump=lambda p:.015*fbm(p*4),bk=.2)
    gold=Mat(base=(1,.85,.4),metal=1.0,gloss=130,envk=1.0)
    gold2=Mat(base=(.8,.5,.1),metal=1.0,gloss=60,envk=.8)
    steel=Mat(base=(.95,.78,.3),metal=1.0,gloss=160,envk=1.0)
    amber=Mat(base=(1,.55,.12),metal=0,gloss=60,emis=lambda p:np.array([1,.5,.1])*1.3,ao=0)
    crim=Mat(base=(.55,.05,.07),metal=0,gloss=14,bump=lambda p:.05*np.sin(p[:,1]*16),bk=.4)
    blue=Mat(base=(.3,.5,1),metal=0,gloss=80,emis=lambda p:np.array([.2,.4,1.0])*.8,ao=0)
    sc.add(capsule((0,-46,0),(0,11,0),.62),white)
    for y in np.linspace(-44,8,40):         # spiral gold inlay
        a=y*1.1; sc.add(sphere((.62*math.cos(a),y,.62*math.sin(a)),.13),gold)
    for y in (-40,-24,-8,3): sc.add(torus((0,y,0),.7,.2,rotx(PI/2)),gold)
    sc.add(cone((0,-46.5,0),.7,3.4,rotx(-PI/2)),gold); sc.add(cyl((0,-45,0),.8,.3,rotx(PI/2),r=.1),gold)
    # socket collar, sun rays, amber heart
    sc.add(cyl((0,12.5,0),1.15,1.6,rotx(PI/2),r=.2),gold); sc.add(torus((0,14,0),1.2,.28,rotx(PI/2)),gold2); sc.add(torus((0,11,0),1.1,.25,rotx(PI/2)),gold2)
    sc.add(sphere((0,13.3,0),.95),amber)
    for i in range(0):
        a=i*2*PI/12; s=-1 if False else 1
        d=C(math.cos(a),0,math.sin(a)); pts=[C(0,12.5,0)+d*1.2+C(0,.0,0), C(0,13.5,0)+d*3.2+C(0,1.8,0), C(0,17.5+(i%2)*2,0)+d*4.4+C(0,0,0), C(0,23+(i%2)*3.5,0)+d*4.0]
        P=bez3(*pts,n=10); chain(sc,P,.34,.06,gold if i%2==0 else gold2)
        sc.add(sphere(P[-1],.2),amber)
    _s0=len(sc.objs)
    for i in range(8):
        a=i*2*PI/8; sc.add(cone((2.9*math.cos(a),12.7+2.9*math.sin(a),0),.55,2.6,rotz(a)@roty(PI/2)),gold)
    sc.marks=getattr(sc,'marks',{}); sc.marks['sun']=(_s0,len(sc.objs)); sc.pivots=getattr(sc,'pivots',{}); sc.pivots['sun']=(0.,12.7,0.)
    # head: long double-edged leaf blade, gold-inlaid fuller
    sc.add(poly([(0,31),(.65,29.5),(1.5,25),(2.05,19),(1.7,15),(.8,14.2),(-.8,14.2),(-1.7,15),(-2.05,19),(-1.5,25),(-.65,29.5)],.17,r=.07),steel)
    sc.add(poly([(0,30.5),(.28,26),(.35,19),(.15,15),(-.15,15),(-.35,19),(-.28,26)],.26,r=.08),steel)
    sc.add(poly([(0,29),(.1,25),(.14,18),(-.14,18),(-.1,25)],.30,r=.03),gold)
    # balance of justice: a small cross-bar with two pans below the collar
    sc.add(capsule((-2.6,8.0,0),(2.6,8.0,0),.14),gold); sc.add(sphere((0,8,0),.32),gold)
    for s in (-1,1):
        sc.add(capsule((s*2.6,8.0,0),(s*2.6,6.4,0),.05),gold)
        sc.add(cyl((s*2.6,6.2,0),.8,.08,rotx(PI/2),r=.05),gold); sc.add(torus((s*2.6,6.3,0),.8,.08,rotx(PI/2)),gold)
    sc.add(sphere((-2.6,6.7,0),.28),blue); sc.add(sphere((2.6,6.7,0),.28),amber)
    # crimson tassel and ribbons
    for k in range(7):
        a=k*2*PI/7; chain(sc,[C(.7*math.cos(a),2.5,.7*math.sin(a)),C(1.0*math.cos(a),-.5,1.0*math.sin(a)),C(1.2*math.cos(a)+.4*math.sin(k),-4.8,1.2*math.sin(a)),C(1.2*math.cos(a)+.8*math.sin(k*2),-8.5,1.2*math.sin(a)+.3)],.22,.08,crim)
    sc.add(torus((0,2.6,0),.78,.22,rotx(PI/2)),gold)
    return sc

# ======================= KATANA =======================
def katana():
    sc=Scene()
    def cx(y): return .0029*((y+6.0)**2-36.0)
    ys=np.concatenate([np.linspace(0,25.6,40),np.linspace(25.8,28,9)])
    def hw(y):
        w=.78-.16*(y/25.6) if y<25.6 else .5*max(0.02,(28-y)/2.4)**.65
        return w
    EDGE=1   # edge on +x
    Lp=[(cx(y)-hw(y),y) for y in ys]; Rp=[(cx(y)+hw(y),y) for y in ys[::-1]]
    def hamon_u(p):   # 0 at the edge, 1 at the spine
        y=p[:,1]; cxx=.0029*((np.clip(y,0,28)+6.0)**2-36.0); hww=np.where(y<25.6,.78-.16*(y/25.6),.3); return np.clip((cxx+hww-p[:,0])/(2*hww),0,1)
    def steel_base(p):
        u=hamon_u(p); y=p[:,1]
        line=.30+.07*np.sin(y*1.9)+.05*np.sin(y*5.3+1)
        grain=.82+.12*np.sin(p[:,1]*24+fbm(p*3)*9)
        hm=smoothstep(line+.03,line-.03,u)          # 1 inside the hardened edge
        base=np.array([.62,.68,.8])[None,:]*grain[:,None]*(1-hm[:,None]*.0)+np.array([.38,.42,.52])*(1-hm)[:,None]*.0
        base=np.where(hm[:,None]>.5,np.array([.86,.9,.97])[None,:]*(.9+.1*fbm(p*5))[:,None],base*(.8))
        return base
    def storm(p):
        u=hamon_u(p); y=p[:,1]; line=.30+.07*np.sin(y*1.9)+.05*np.sin(y*5.3+1)
        ridge=np.clip(1-np.abs(fbm(p*np.array([1.2,1.2,1.2])*2.0+4)-.5)*14,0,1)**2
        near=smoothstep(line+.12,line-.02,u)
        return np.array([.35,.7,1.0])[None,:]*(ridge*near*2.2+near*.25)[:,None]*(.6+.4*np.sin(y*.5-fbm(p)*3))[:,None]
    steel=Mat(base=steel_base,metal=.95,gloss=140,envk=.7,emis=storm)
    sc.add(poly(Lp+Rp,.07,r=.03),steel)
    sc.add(poly([(cx(y)-.4,y) for y in ys[:30]]+[(cx(y)+.4,y) for y in ys[:30][::-1]],.115,r=.05),steel)   # shinogi (thicker spine)
    gold=Mat(base=(.9,.65,.2),metal=1.0,gloss=100,envk=1.0)
    iron=Mat(base=(.1,.1,.12),metal=.9,gloss=70,envk=.6,bump=lambda p:.03*fbm(p*5),bk=.5)
    sc.add(cyl((0,-.55,0),.78,.55,rotx(PI/2),r=.08),gold)                  # habaki
    # tsuba, round openwork guard
    sc.add(torus((0,-1.15,0),1.55,.12,rotx(PI/2)*0+I3),iron) if False else None
    def tsuba(c):
        T=loc(c,rotx(PI/2))
        def f(p):
            q=T(p); r=np.sqrt(q[:,0]**2+q[:,1]**2); th=np.arctan2(q[:,1],q[:,0])
            hole=np.maximum(np.abs(r-.95)-.34, 0)*0
            d2=r-1.6; 
            # four lobed openings: subtract round holes at 45deg offsets
            hd=np.full(len(p),1e9)
            for k in range(4):
                a=k*PI/2+PI/4; hd=np.minimum(hd,np.sqrt((q[:,0]-1.0*math.cos(a))**2+(q[:,1]-1.0*math.sin(a))**2)-.38)
            d2=np.maximum(d2,-hd)
            d2=np.maximum(d2,-(r-.0)+0*r)
            dz=np.abs(q[:,2])-.1
            w=np.stack([d2,dz],1); return np.minimum(w.max(1),0)+norm(np.maximum(w,0))-.04
        return f
    sc.add(cyl((0,-1.15,0),2.0,.12,rotx(PI/2),r=.05),iron)
    sc.add(torus((0,-1.15,0),2.0,.1,rotx(PI/2)),gold)
    for k in range(8):
        a=k*PI/4; sc.add(sphere((2.0*math.cos(a)*.62,-1.15,2.0*math.sin(a)*.62),.2),gold)
    # tsuka: ito-wrapped grip, curved slightly
    def wrapb(p):
        a=np.abs(np.sin((p[:,1]*2.1+p[:,0]*2.4)))*np.abs(np.sin((p[:,1]*2.1-p[:,0]*2.4)))
        ito=a>.14; return np.where(ito[:,None],np.array([.04,.06,.16]),np.array([.86,.84,.78]))
    tsuka=Mat(base=wrapb,metal=.1,gloss=40,bump=lambda p:.1*np.abs(np.sin(p[:,1]*2.1+p[:,0]*2.4))*np.abs(np.sin(p[:,1]*2.1-p[:,0]*2.4)),bk=.9)
    P=[(0,-1.8,0),(-.05,-5.5,0),(-.2,-9.4,0),(-.5,-13,0)]
    chain(sc,[C(cx(y),y,0) for y in (-1.7,-3.5,-6,-9,-11.2)],.74,.8,tsuka)
    sc.add(cyl((0,-1.95,0),.8,.28,rotx(PI/2),r=.08),gold)                        # fuchi
    sc.add(sphere((-.5,-11.4,0),.9),gold) if False else sc.add(cyl((cx(-11.45),-11.45,0),.88,.35,rotx(PI/2),r=.14),gold)
    for s in (-1,1): sc.add(sphere((-.1,-5.8,s*.78),.2),gold); sc.add(sphere((-.12,-7.4,s*.78),.17),gold)   # menuki
    # electricity crawling on the blade, thin and sharp
    elec=Mat(base=(.8,.95,1),metal=0,gloss=10,emis=lambda p:np.array([.5,.8,1.0])*2.6,ao=0)
    rng=np.random.RandomState(21)
    for k in range(16):
        y=3+rng.rand()*22; side=1 if k%2 else -1; x=cx(y)+side*hw(y)*(.8+rng.rand()*.3); pts=[(x,y,.12)]; ang=(0 if side>0 else PI)+rng.randn()*.4+PI/2*.25
        for s in range(5): ang+=rng.randn()*.9; x+=.7*math.cos(ang); y+=.7*math.sin(ang)+.2; pts.append((x,y,.12+rng.randn()*.1))
        chain(sc,[C(*q) for q in pts],.06,.035,elec)
    return sc

# ======================= BONE GREATSWORD =======================
def bone():
    sc=Scene()
    def bone_base(p):
        n=fbm(p*.25); c=np.array([.95,.89,.74])[None,:]*(.88+.16*n)[:,None]
        edge=np.clip((np.abs(p[:,0])-5.0)*1.5,0,1)[:,None]; return c*(1-.12*edge)
    ivory=Mat(base=bone_base,metal=.04,gloss=50,envk=.4,bump=lambda p:.01*fbm(p*.8),bk=.3)
    dark=Mat(base=(.2,.12,.08),metal=.05,gloss=18,bump=lambda p:.04*np.sin(p[:,1]*8),bk=.4)
    teal=Mat(base=(.04,.07,.07),emis=lambda p:np.array([.2,.95,.85])*1.1,ao=0)
    # colossal slab blade: 11 wide, ~64 long, thick, ragged edges and a notched point
    R_=[(0,70),(2.2,62),(3.6,54),(4.4,46),(5.2,38),(4.6,30),(6.2,24),(5.0,16),(6.4,9),(5.3,1)]
    L_=[(-1.8,63),(-3.4,55),(-4.2,47),(-5.4,39),(-4.4,31),(-6.0,23),(-4.8,15),(-6.2,8),(-5.3,1)]
    sc.add(poly([(0,70)]+R_[1:]+[(-5.3,1)]+L_[::-1][1:]+[(-1.8,63)],1.3,r=.45),ivory)
    sc.add(poly([(0,66)]+[(1.6,58),(2.6,50),(3.4,6),(-3.4,6),(-2.6,50),(-1.6,58)],1.6,r=.5),ivory)   # raised centre slab
    sc.add(rbox((0,36,1.55),(.45,28,.3),.2),teal)                                                      # glowing fuller
    for y in (14,24,34,44,54): sc.add(rbox((0,y,1.65),(3.2,.22,.14),.1),dark)                        # lashings
    # jagged serration along both edges
    for s,pts in ((1,R_),(-1,L_)):
        for (x,y) in pts[1:-1]:
            sc.add(cone((x+s*.6,y-.5,0),1.1,3.4,rotz(-s*PI/2+s*.5)@roty(0)),ivory)
    # massive cross guard: two giant curved ribs
    for s in (-1,1):
        P=bez3((s*1.0,-1.5,0),(s*8,-2.5,0),(s*14,-1.0,0),(s*15.5,5.0,0),n=14); chain(sc,[C(*q) for q in P],1.5,.6,ivory)
        sc.add(sphere(C(*P[0]),1.6),ivory); sc.add(sphere(C(*P[-1]),.7),ivory)
    sc.add(rbox((0,-.5,0),(3.2,1.6,1.3),.6),ivory)
    # long two-handed grip wrapped in sinew, vertebra pommel
    sc.add(capsule((0,-3,0),(0,-22,0),1.1,1.0),dark)
    for y in np.linspace(-4,-21,16): sc.add(torus((0,y,0),1.15,.22,rotx(PI/2)),Mat(base=(.34,.22,.15),metal=.0,gloss=14))
    for k in range(4): sc.add(sphere((0,-24-k*2.6,0),1.7-.2*k),ivory)
    sc.add(sphere((0,-35,0),1.2),ivory)
    return sc

# ======================= RIFLE (Victorian lever-action with a snake) =======================
def rifle():
    sc=Scene()
    walnut=Mat(base=lambda p:(np.array([.30,.15,.07])*(.62+.5*np.sin(p[:,0]*1.4+fbm(p*.5)*7)*.5+.4)[:,None]),metal=.15,gloss=90,envk=.45,bump=lambda p:.04*np.sin(p[:,0]*6+fbm(p)*4),bk=.4)
    blued=Mat(base=(.08,.1,.16),metal=1.0,gloss=140,envk=.9)
    brass=Mat(base=(.85,.58,.2),metal=1.0,gloss=90,envk=1.0)
    steel=Mat(base=(.6,.64,.7),metal=1.0,gloss=120,envk=1.0)
    def sc_b(p):
        s=.5+.5*np.sin(p[:,0]*5.2+np.sin(p[:,1]*3.1)*1.5)*np.sin(p[:,2]*5.4+p[:,0]*1.7)
        return np.array([.05,.34,.12])[None,:]*(.55+.9*s[:,None])+np.array([.5,.4,.1])*(np.sin(p[:,0]*1.2)>.88)[:,None]*.5
    snake=Mat(base=sc_b,metal=.45,gloss=70,envk=.5)
    amber=Mat(base=(1,.85,.2),emis=lambda p:np.array([1,.75,.1])*1.4,ao=0)
    red=Mat(base=(.8,.1,.1),emis=lambda p:np.array([.6,.05,.05]),ao=0)
    greenglow=Mat(base=(.3,.9,.5),emis=lambda p:np.array([.2,.9,.4])*1.2,ao=0)
    # stock with comb, wrist, and a tall butt
    sc.add(poly([(0,4.6),(4,4.4),(10,3.7),(14.5,3.1),(17,3.05),(17,.5),(14,.3),(12,-.1),(6.5,-.4),(.2,-.8)],.7,r=.4),walnut)
    sc.add(poly([(.05,4.7),(-.15,4.6),(-.15,-.9),(.05,-.9)],.78,r=.05),brass)
    # snake carved along the stock side in relief
    sp=[C(.6+i*.5,1.9+1.1*math.sin(i*.7),.78) for i in range(26)]
    chain(sc,sp,.42,.3,snake)
    sc.add(sphere(sp[0]+C(-.3,0,0),.5),snake)
    for s in (-1,1): sc.add(sphere(sp[0]+C(-.35,.28,s*.12+.0),.09),amber)
    sp2=[C(.6+i*.5,1.9+1.1*math.sin(i*.7),-.78) for i in range(26)]; chain(sc,sp2,.42,.3,snake)
    # receiver
    sc.add(rbox((18.8,1.9,0),(2.9,1.65,.78),.3),brass)
    sc.add(rbox((19.2,3.7,0),(2.5,.14,.5),.05),steel); sc.add(rbox((19,.12,0),(2.7,.14,.6),.05),steel)
    for k in range(3): sc.add(sphere((17.6+k*1.7,1.6,.8),.28),steel)
    sc.add(rbox((19,2.7,.8),(1.7,.05,.02),.01),steel)
    # hammer
    sc.add(poly([(16,3.1),(15.4,4.8),(14.6,5.2),(14.2,4.8),(15,3.5)],.22,r=.08),steel)
    # barrel and magazine tube with forestock and bands
    sc.add(cyl((34.5,2.9,0),.58,12.8,roty(PI/2)),blued)
    sc.add(cyl((30,1.3,0),.45,8.4,roty(PI/2)),steel)
    sc.add(poly([(21.6,3.0),(33,2.9),(33,.6),(21.6,.0)],.62,r=.3),walnut)
    for x in (24.5,30.5,36.2,41.5): sc.add(cyl((x,2.15 if x<34 else 2.9,0),.82 if x<34 else .72,.24,roty(PI/2),r=.06),brass)
    sc.add(cyl((47.6,2.9,0),.62,.22,roty(PI/2),r=.05),steel); sc.add(rbox((46.8,3.7,0),(.12,.35,.05),.03),steel)
    sc.add(rbox((27,3.6,0),(.8,.18,.35),.04),steel); sc.add(rbox((27,4.0,0),(.12,.28,.3),.03),steel)
    # steam capsule: a small brass cage on the receiver with a green glowing vial
    sc.add(cyl((19.5,2.0,.85),.75,.1,rotx(PI/2),r=.04),brass); sc.add(torus((19.5,2.0,.95),.75,.1,rotx(PI/2)*0+I3),brass)
    sc.add(sphere((19.5,2.0,.98),.5),greenglow)
    # snake lever: the lever loop is a snake with head near the trigger
    pts=bez3((17.4,.4,0),(16.5,-3.2,0),(21.5,-4.8,0),(24.5,-2.2,0),n=18)
    pts2=bez3(pts[-1],(26.5,-.6,0),(25,.6,0),(23.2,.5,0),n=8)
    chain(sc,[C(*q) for q in pts],.6,.62,snake); chain(sc,[C(*q) for q in pts2],.62,.5,snake)
    hd=pts2[-1]; sc.add(sphere(hd+C(.2,0,0),.8),snake); sc.add(sphere(hd+C(.9,-.1,0),.52),snake)
    for s in (-1,1): sc.add(sphere(hd+C(.5,.35,s*.45),.15),amber)
    sc.add(capsule(hd+C(1.4,-.1,0),hd+C(2.3,-.1,0),.05),red)
    sc.add(capsule(hd+C(2.3,-.1,0),hd+C(2.8,.2,0),.04),red); sc.add(capsule(hd+C(2.3,-.1,0),hd+C(2.8,-.4,0),.04),red)
    # trigger
    sc.add(capsule((19.2,.3,0),(19.0,-1.2,0),.18,.1),steel)
    return sc

# ======================= GRASPING SHIELD =======================
def bow():
    sc=Scene()
    iron=Mat(base=lambda p:np.array([.07,.07,.1])+np.array([.08,.06,.14])*np.clip(fbm(p*.5)*2-.8,0,1)[:,None],metal=.9,gloss=70,envk=.7,bump=lambda p:.02*fbm(p*3),bk=.4)
    steel=Mat(base=(.62,.66,.74),metal=1.0,gloss=110,envk=1.0)
    band=Mat(base=lambda p:np.array([.86,.82,.72])*(.88+.12*np.sin(p[:,1]*9+p[:,2]*4))[:,None],metal=0,gloss=14,bump=lambda p:.05*np.sin(p[:,1]*9+p[:,2]*4),bk=.6)
    rose=Mat(base=(.62,.04,.08),metal=0,gloss=40,bump=lambda p:.06*fbm(p*5),bk=.6)
    leaf=Mat(base=(.08,.25,.1),metal=0,gloss=30)
    glow=Mat(base=(.5,.4,.9),emis=lambda p:np.array([.55,.35,1.0])*1.5,ao=0)
    up=bez3((0,2.5,0),(5.5,8,0),(5.8,17,0),(.5,25,0),n=16); lo=[C(q[0],-q[1],q[2]) for q in up]
    for P in (up,lo):
        chain(sc,[C(*q) for q in P],1.7,.8,iron)
        for k,q in enumerate(P[2:-1]):          # chain links wrapped along the limb, alternating
            sc.add(torus(C(*q)+C(.0,0,0),.95,.12,rotx(PI/2)@rotz(k*PI/2) if k%2 else roty(PI/2)),steel)
        for k,q in enumerate(P[3:-2:2]):        # thorns
            sc.add(cone(C(*q)+C(.6,0,.6),.28,1.5,roty(PI/4)),leaf)
            sc.add(cone(C(*q)+C(-.2,0,-.9),.28,1.5,roty(PI+PI/4)),leaf)
        sc.add(sphere(C(*P[-1]),.8),steel)
    # bandage-wrapped grip and riser
    sc.add(capsule((0,-5,0),(0,5,0),1.15,1.15),band)
    for y in np.linspace(-4.8,4.8,12): sc.add(torus((0,y,0),1.2,.17,rotx(PI/2)@rotz(.0)),band)
    sc.add(sphere((0,6.2,0),1.4),steel); sc.add(sphere((0,-6.2,0),1.4),steel)
    # roses at the limb tips and at the grip
    for (x,y,z) in ((0.5,25,.2),(.5,-25,.2),(1.4,0,1.1)):
        sc.add(sphere((x+.4,y,z),1.25),rose)
        for a in (0,2.1,4.2): sc.add(sphere((x+.4+.7*math.cos(a),y+.2,z+.7*math.sin(a)),.7),rose)
        sc.add(sphere((x+.4,y+.2,z+.2),.45),Mat(base=(.9,.1,.12),metal=0,gloss=40))
    _k0=len(sc.objs)
    # chain bowstring, glowing at the nock
    for k in range(18):
        y=-24.5+k*49/17; sc.add(torus((-1.0,y,0),.5,.1,roty(PI/2) if k%2 else rotx(PI/2)),steel)
    sc.add(sphere((-1.0,0,0),.7),glow)
    sc.marks=getattr(sc,'marks',{}); sc.marks['string']=(_k0,len(sc.objs))
    return sc

def hand(sc,base,dirv,up,mat,scale=1.0,curl=.35):
    d=np.array(dirv,float); d/=np.linalg.norm(d); u=np.array(up,float); u-=d*(u@d); u/=np.linalg.norm(u); s=np.cross(d,u)
    palm=base+d*.9*scale
    sc.add(rbox(palm,(.95*scale,.85*scale,.35*scale),.25*scale,np.stack([s,u,d],1)),mat)
    for f in range(4):
        off=(f-1.5)*.55*scale; L=[1.05,1.25,1.15,.95][f]*scale
        p0=base+d*1.5*scale+s*off*1.0+u*.05*scale
        p1=p0+d*L*(1-curl*.4)+u*L*curl*.5; p2=p1+d*L*.8*(1-curl)+u*L*curl*1.0; p3=p2+d*L*.45*(1-curl*1.4)+u*L*curl*1.1
        chain(sc,[p0,p1,p2,p3],.2*scale,.11*scale,mat)
        for q in (p1,p2): sc.add(sphere(q,.2*scale),mat)
    t0=base+d*.7*scale+s*1.05*scale; t1=t0+d*.7*scale+s*.5*scale+u*.2*scale; t2=t1+d*.5*scale+s*.1*scale+u*.5*scale
    chain(sc,[t0,t1,t2],.28*scale,.15*scale,mat)
def gshield():
    sc=Scene()
    iron=Mat(base=lambda p:np.array([.14,.14,.17])*(.7+.6*fbm(p*.8))[:,None],metal=.8,gloss=45,envk=.5,bump=lambda p:.15*fbm(p*2.5),bk=.5)
    def cloth_b(p):
        stain=fbm(p*.5+3); c=np.array([.78,.7,.54])[None,:]*(.65+.6*stain)[:,None]
        weave=.88+.12*np.sin(p[:,0]*14)*np.sin(p[:,1]*14); return c*weave[:,None]
    cloth=Mat(base=cloth_b,metal=0,gloss=8,envk=.1,bump=lambda p:.08*np.sin(p[:,0]*14)*np.sin(p[:,1]*14)+.12*fbm(p*2),bk=.7,rim=(.35,.12,.3))
    briar=Mat(base=(.06,.1,.06),metal=.1,gloss=25,bump=lambda p:.05*fbm(p*6),bk=.4)
    rose=Mat(base=(.38,.03,.07),metal=.1,gloss=40,bump=lambda p:.1*np.sin(np.arctan2(p[:,1],p[:,0])*5)*0+.06*fbm(p*5))
    glow=Mat(base=(.5,.2,.4),emis=lambda p:np.array([.7,.2,.5])*.6,ao=0)
    R_=12.0
    sc.add(cyl((0,0,0),R_,.7,r=.35),iron)
    sc.add(cyl((0,0,.65),R_-.9,.35,r=.2),iron)
    sc.add(torus((0,0,.2),R_,.9),iron)
    for i in range(18):
        a=i*2*PI/18; sc.add(sphere((R_*math.cos(a),R_*math.sin(a),1.0),.38),Mat(base=(.3,.3,.34),metal=.9,gloss=80))
    # bandage strips wrapped across the face
    for k,(a,off,w) in enumerate([(.5,-3,1.8),(.55,2.4,1.5),(-.8,-5,1.6),(-.75,4.8,1.7),(1.25,0.0,1.4),(.0,-7.4,1.5),(0.1,7.0,1.6)]):
        c,s_=math.cos(a),math.sin(a); R2=rotz(a)
        sc.add(rbox((s_*off*-1,c*off,1.02),(R_*.93,w/2,.09),.06,rotz(a-PI/2+PI/2) if False else rotz(a)),cloth) if False else None
        sc.add(rbox((-s_*off,c*off,1.02),(R_*.8,w/2,.08),.05,rotz(a)),cloth)
    sc.add(sphere((0,0,1.0),2.9),iron); sc.add(torus((0,0,1.5),2.9,.28,I3)*0 if False else torus((0,0,1.4),2.6,.3),iron)
    sc.add(sphere((0,0,2.3),1.1),glow)
    # thorned briar and roses around the rim
    pts=[C((R_+.9)*math.cos(t),(R_+.9)*math.sin(t),1.2+.5*math.sin(t*7)) for t in np.linspace(0,2*PI,70)]
    chain(sc,pts,.36,.36,briar)
    for k in range(34):
        a=k*2*PI/34+.1; p=C((R_+.9)*math.cos(a),(R_+.9)*math.sin(a),1.3); sc.add(cone(p+C(math.cos(a),math.sin(a),.4)*.15,.26,1.2,rotz(a-PI/2)@roty(PI/2)*0+rotz(a)@roty(PI/2)),briar)
    for a in (.4,1.7,2.9,4.2,5.4):
        p=C((R_+.9)*math.cos(a),(R_+.9)*math.sin(a),1.6)
        for k in range(5): sc.add(sphere(p+C(.5*math.cos(k*1.3)*.5,.5*math.sin(k*1.3)*.5,.2+.25*k),1.0-.12*k),rose)
    # grasping bandaged arms reaching out from behind the rim
    for a0 in (.7,2.35,3.95,5.5):
        d=C(math.cos(a0),math.sin(a0),.25); base=C(math.cos(a0),math.sin(a0),0)*(R_-1.5)
        pts=[base+d*u*7+C(0,0,1.0+1.2*math.sin(u*PI))+C(-math.sin(a0),math.cos(a0),0)*math.sin(u*PI*1.2)*2.2 for u in np.linspace(0,1,10)]
        chain(sc,pts,1.05,.78,cloth)
        for q in pts[1:-1:2]: sc.add(torus(q,.95,.14,rotx(PI/2)),cloth) if False else None
        hand(sc,pts[-1],(pts[-1]-pts[-2]),(0,0,1),cloth,scale=1.5,curl=.4)
    return sc

JOBS={ # name: builder, angle, view half-size, model-centre, unit, aura colour, strength
 'starry_revolver':(revolver,.06,1.0,(8.2,-.35),.12,(.3,.5,1.0),.3),
 'shadow_sword':(shadow_sword,-PI/4,1.0,(0,12.4),.058,(.5,.25,1.0),.4),
 'shadow_shield':(shadow_shield,0.0,1.0,(0,-0.5),.047,(.5,.25,1.0),.4),
 'spear_of_sun':(spear,-PI/4,1.0,(0,-7.5),.0315,(1.0,.8,.35),.3),
 'storm_katana':(katana,-PI/4,1.0,(0,8.5),.05,(.45,.7,1.0),.3),
 'bone_greatsword':(bone,-PI/4,1.0,(0,14),.0215,(.5,1.0,.9),.18),
 'snake_rifle':(rifle,.62,1.0,(24,1.5),.047,(.4,.9,.5),.14),
 'chained_bow':(bow,.35,1.0,(3,0),.036,(.9,.3,.6),.25),
}
if __name__=='__main__':
    import os,time; NN=int(os.environ.get('NN','256'))
    for n in (sys.argv[1:] or list(JOBS)):
        f,ang,vs,mc,unit,ac,ast=JOBS[n]; t=time.time()
        img=render(f(),N=NN,ang=ang,scale=vs,mc=mc,unit=unit); img=bloom(img); img=aura(img,ac,ast,NN//30); save(img,f'/tmp/r3/n_{n}.png'); print(n,round(time.time()-t,1),flush=True)
