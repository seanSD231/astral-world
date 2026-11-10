import sys; sys.path.insert(0,'/tmp/r3')
from m2 import *
import m2

def scroll(sc,cx_,cy_,z,r,mat,flip=1,turns=2.3,rad=.05):
    pts=[]
    n=26
    for i in range(n+1):
        th=i/n*turns*PI; rr=r*(1-.85*i/n)
        pts.append(C(cx_+flip*rr*math.cos(th),cy_+rr*math.sin(th),z))
    chain(sc,pts,rad,rad*.8,mat); sc.add(sphere(pts[-1],rad*2.2),mat)


def gear(sc,cx_,cy_,z,r,teeth,mat,hub=None):
    sc.add(torus((cx_,cy_,z),r,.07*r+.03,I3),mat)
    sc.add(cyl((cx_,cy_,z),.28*r,.05,I3,r=.01),mat)
    for k in range(teeth):
        a=k*2*PI/teeth
        sc.add(rbox((cx_+(r+.07*r)*math.cos(a),cy_+(r+.07*r)*math.sin(a),z),(.17*r,.17*r*.55,.05),.015,rotz(a)),mat)
    for k in range(4):
        a=k*PI/2+.4; sc.add(capsule((cx_,cy_,z),(cx_+r*math.cos(a),cy_+r*math.sin(a),z),.03+.02*r),mat)
    if hub is not None: sc.add(sphere((cx_,cy_,z+.03),.12*r+.04),hub)

def spirit(sc,c,sc_,mat,eye,sway=1.0,dirx=1):
    c=np.array(c,float)
    sc.add(sphere(c,1.15*sc_),mat); sc.add(sphere(c+C(0,-.7*sc_,0),1.0*sc_),mat)
    pts=[c+C(dirx*sway*(.6*math.sin(i*.9)*sc_*(1+i*.15)),-1.3*sc_-i*1.3*sc_,0) for i in range(7)]
    chain(sc,pts,.95*sc_,.12*sc_,mat)
    for s in (-1,1): sc.add(sphere(c+C(s*.42*sc_,.1*sc_,.9*sc_),.26*sc_),eye)
    sc.add(sphere(c+C(0,-.55*sc_,1.0*sc_),.2*sc_),eye)

def flame(sc,base,h,lean,mat,w=1.1):
    base=np.array(base,float)
    pts=[base+C(lean*(t**1.6)*h*.5+.25*w*math.sin(t*7),t*h,0.3) for t in np.linspace(0,1,9)]
    for t in np.linspace(0,1,16):
        q=base+C(lean*(t**1.6)*h*.5+.25*w*math.sin(t*7),t*h,0.3); sc.add(sphere(q,w*(1-t)**1.1+.07),mat)

def revolver2():
    sc=Scene()
    def sky_base(p):
        n=fbm(p*.5+1.5); neb=np.clip(n*1.9-.7,0,1)
        c=np.array([.015,.02,.06])+np.array([.04,.14,.55])*neb[:,None]*1.5
        return c+np.array([.35,.1,.5])*np.clip(fbm(p*.7+8)*2-1.2,0,1)[:,None]*.45
    def stars(p): return starfield(p,6.0,.86)*.9+starfield(p+2.7,10.0,.88)*.8+starfield(p+5.1,3.0,.97)*1.1
    navy=Mat(base=lambda p: sky_base(p)+stars(p)[:,None]*1.3,metal=.85,gloss=120,envk=.85,emis=lambda p: np.array([.55,.7,1.0])*(stars(p)*1.0)[:,None])
    silver=Mat(base=(.82,.86,.95),metal=1.0,gloss=150,envk=1.0)
    silver2=navy
    brass=Mat(base=(.98,.74,.28),metal=1.0,gloss=110,envk=1.0)
    gem=Mat(base=(.3,.5,1),emis=lambda p:np.array([.25,.55,1.0])*1.6,ao=0)
    dark=Mat(base=(.02,.03,.07),metal=0,ao=1)
    # barrel (starry) with silver rings, rib and crown
    sc.add(cyl((10.8,2.95,0),.55,3.7,roty(PI/2),r=.02),navy)
    for x in (7.9,10.4,13.0): sc.add(cyl((x,2.95,0),.62,.12,roty(PI/2),r=.03),silver)
    sc.add(cyl((14.45,2.95,0),.66,.2,roty(PI/2),r=.06),silver); sc.add(cyl((14.62,2.95,0),.4,.06,roty(PI/2)),dark)
    sc.add(rbox((11.0,3.53,0),(3.3,.05,.14),.02),silver)
    sc.add(rbox((14.0,3.75,0),(.2,.26,.05),.03),silver)                     # front sight
    sc.add(cyl((9.55,1.88,0),.3,2.2,roty(PI/2),r=.02),navy); sc.add(sphere((11.85,1.88,0),.34),silver)   # ejector housing + knob
    sc.add(rbox((7.7,2.35,0),(.5,.55,.3),.1),navy)                          # barrel lug
    # frame in silver: recoil shield, top strap, front web, bottom rail
    sc.add(poly([(.9,3.35),(1.4,3.55),(3.3,3.55),(3.3,.6),(2.6,.6),(2.2,.9),(2.0,2.2),(1.0,2.9)],.74,r=.1),silver2)
    sc.add(rbox((5.25,3.38,0),(2.0,.2,.72),.1),silver2)
    sc.add(poly([(5.2,3.55),(7.5,3.55),(7.5,1.1),(6.7,.6),(5.2,.6)],.7,r=.1),silver2)
    sc.add(rbox((4.25,.7,0),(1.1,.14,.66),.08),silver2)
    sc.add(rbox((1.25,3.7,0),(.18,.1,.2),.03),silver)                        # rear sight
    # fluted cylinder
    def fluted(c,rad,hh):
        c=np.array(c,float)
        def f(p):
            q=p-c; r=np.sqrt(q[:,1]**2+q[:,2]**2); th=np.arctan2(q[:,2],q[:,1]); rr=rad-.12*np.clip(np.cos(6*th)*2.4-1.2,0,1)
            d=np.stack([r-rr,np.abs(q[:,0])-hh],1); return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-.05
        return f
    sc.add(fluted((4.25,2.05,0),.98,.93),navy)
    for x in (3.35,5.15): sc.add(torus((x,2.05,0),.96,.06,roty(PI/2)),silver)
    sc.add(cyl((3.2,2.05,0),.3,.1,roty(PI/2)),silver); sc.add(sphere((3.12,2.05,0),.13),gem)
    # grip (starry), butt cap, medallions
    grip=[(1.0,3.0),(.55,2.2),(.15,1.0),(-.3,-.5),(-.75,-2.0),(-1.25,-3.6),(-.7,-4.0),(.6,-3.7),(1.5,-3.3),(1.95,-1.8),(2.3,-.3),(2.65,.6),(3.3,.62),(3.3,3.0)]
    sc.add(poly(grip,.6,r=.25),navy)
    sc.add(poly([(-1.3,-3.55),(-.7,-4.05),(.6,-3.75),(1.5,-3.35),(1.4,-3.0),(.55,-3.35),(-.7,-3.7),(-1.2,-3.3)],.66,r=.06),silver)
    # trigger guard, trigger, hammer
    chain(sc,[C(*q) for q in bez3((6.0,.7,0),(5.9,-.7,0),(4.4,-1.2,0),(3.35,-.8,0),n=16)]+[C(2.7,.2,0),C(2.65,.6,0)],.13,.13,silver)
    chain(sc,[C(4.1,.8,0),C(3.95,.25,0),C(3.8,-.15,0)],.09,.07,silver)
    # engraving: scrolls on both sides, rope of beads along the top strap, brass gear at the hammer pivot
    for sd in (-1,1):
        z=sd*.76
        scroll(sc,6.5,2.9,z,.35,silver,flip=1,rad=.04)
        pass
        chain(sc,[C(x,3.4,sd*.74) for x in np.linspace(3.5,7.2,9)],.05,.05,brass)
        chain(sc,[C(x,.62,sd*.68) for x in np.linspace(3.2,5.4,7)],.05,.05,brass)
        scroll(sc,9.2,2.95,sd*.56,.3,silver,flip=1,rad=.035)
    # the hammer IS a gear: a big open-work cog on the rear of the frame, with a ratchet rod down to the trigger
    HX,HY,HR=1.55,4.15,.95
    sc.add(cyl((HX,HY,0),.2,.7,I3,r=.03),silver)                           # axle
    for z in (-.3,.3):
        sc.add(torus((HX,HY,z),HR,.085,I3),silver); sc.add(torus((HX,HY,z),HR*.55,.06,I3),silver)
        for k in range(14):
            a=k*2*PI/14; sc.add(cone((HX+(HR+.05)*math.cos(a)+.12*math.cos(a),HY+(HR+.05)*math.sin(a)+.12*math.sin(a),z),.15,.42,rotz(a)@roty(PI/2)),silver)
        for k in range(5):
            a0=k*2*PI/5; P=bez3((HX,HY,z),(HX+.45*math.cos(a0+.8),HY+.45*math.sin(a0+.8),z),(HX+.8*math.cos(a0+.1),HY+.8*math.sin(a0+.1),z),(HX+HR*math.cos(a0+.6),HY+HR*math.sin(a0+.6),z),n=8)
            chain(sc,[C(*q) for q in P],.06,.05,silver)
        sc.add(sphere((HX,HY,z+(.1 if z>0 else -.1)),.2),gem)
    chain(sc,[C(2.2,3.7,0),C(2.9,2.6,0),C(3.5,1.4,0),C(3.9,.85,0)],.08,.07,silver)     # linkage rod to the trigger
    for sd in (-1,1):
        chain(sc,[C(x,3.45,sd*.78) for x in (3.9,4.6,5.5,6.6)],.06,.06,silver)
        gear(sc,2.55,2.55,sd*.82,.5,11,silver,gem)
    return sc

def vine(sc,x0,x1,yc,z,mat,amp=.17,rad=.035,leaf=None):
    xs=np.linspace(x0,x1,60); pts=[C(x,yc+amp*math.sin((x-x0)*3.2),z) for x in xs]
    chain(sc,pts,rad,rad,mat)
    k=0
    for x in np.arange(x0+.5,x1-.3,.9):
        s=1 if k%2==0 else -1; y=yc+amp*math.sin((x-x0)*3.2)
        scroll(sc,x+.12,y+s*.2,z,.19,mat,flip=s,turns=1.7,rad=.03); k+=1

def revolver3():
    sc=Scene()
    def sky_base(p):
        n=fbm(p*.5+1.5); neb=np.clip(n*1.9-.7,0,1)
        c=np.array([.015,.02,.06])+np.array([.04,.14,.55])*neb[:,None]*1.5
        return c+np.array([.35,.1,.5])*np.clip(fbm(p*.7+8)*2-1.2,0,1)[:,None]*.45
    def stars(p): return starfield(p,6.0,.86)*.9+starfield(p+2.7,10.0,.88)*.8+starfield(p+5.1,3.0,.97)*1.1
    navy=Mat(base=lambda p: sky_base(p)+stars(p)[:,None]*1.3,metal=.85,gloss=120,envk=.85,emis=lambda p: np.array([.55,.7,1.0])*(stars(p)*1.0)[:,None])
    silver=Mat(base=(.82,.86,.95),metal=1.0,gloss=150,envk=1.0)
    gem=Mat(base=(.3,.5,1),emis=lambda p:np.array([.25,.55,1.0])*1.6,ao=0)
    dark=Mat(base=(.02,.03,.07),metal=0,ao=1)
    # long slender barrel with flared collar, engraved vines, crown and sight
    sc.add(cyl((12.0,2.85,0),.52,3.8,roty(PI/2),r=.02),navy)
    sc.add(cyl((8.75,2.85,0),.78,.5,roty(PI/2),r=.06),silver)
    for x in (9.4,15.0): sc.add(torus((x,2.85,0),.56,.06,roty(PI/2)),silver)
    sc.add(cyl((15.7,2.85,0),.64,.22,roty(PI/2),r=.06),silver); sc.add(cyl((15.9,2.85,0),.4,.06,roty(PI/2)),dark)
    sc.add(rbox((15.2,3.5,0),(.2,.26,.05),.03),silver)
    sc.add(rbox((12.0,3.4,0),(3.1,.045,.12),.02),silver)
    for sd in (-1,1): vine(sc,9.7,14.8,2.85,sd*.54,silver)
    # frame
    sc.add(poly([(.9,3.35),(1.4,3.55),(3.3,3.55),(3.3,.6),(2.6,.6),(2.2,.9),(2.0,2.2),(1.0,2.9)],.74,r=.1),navy)
    sc.add(rbox((5.4,3.42,0),(2.1,.16,.6),.08),silver)
    sc.add(rbox((5.4,.66,0),(2.1,.12,.6),.07),silver)
    sc.add(poly([(7.5,3.5),(8.45,3.3),(8.45,1.2),(7.5,.62)],.62,r=.1),navy)
    sc.add(rbox((1.25,3.7,0),(.18,.1,.2),.03),silver)
    # long fluted cylinder
    def fluted(c,rad,hh,nfl=8):
        c=np.array(c,float)
        def f(p):
            q=p-c; r=np.sqrt(q[:,1]**2+q[:,2]**2); th=np.arctan2(q[:,2],q[:,1]); rr=rad-.17*np.clip(np.cos(nfl*th)*2.6-1.0,0,1)
            d=np.stack([r-rr,np.abs(q[:,0])-hh],1); return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-.05
        return f
    sc.add(fluted((5.4,2.05,0),.98,2.05),navy)
    for x in (3.4,5.4,7.4): sc.add(torus((x,2.05,0),.96,.07,roty(PI/2)),silver)
    sc.add(cyl((3.2,2.05,0),.3,.1,roty(PI/2)),silver); sc.add(sphere((3.12,2.05,0),.13),gem)
    # grip
    grip=[(1.0,3.0),(.55,2.2),(.15,1.0),(-.3,-.5),(-.75,-2.0),(-1.25,-3.6),(-.7,-4.0),(.6,-3.7),(1.5,-3.3),(1.95,-1.8),(2.3,-.3),(2.65,.6),(3.3,.62),(3.3,3.0)]
    sc.add(poly(grip,.6,r=.25),navy)
    sc.add(poly([(-1.3,-3.55),(-.7,-4.05),(.6,-3.75),(1.5,-3.35),(1.4,-3.0),(.55,-3.35),(-.7,-3.7),(-1.2,-3.3)],.66,r=.06),silver)
    for sd in (-1,1):
        scroll(sc,.75,-.6,sd*.62,.55,silver,flip=1,turns=2.6,rad=.04); scroll(sc,1.2,-2.3,sd*.62,.4,silver,flip=-1,turns=2.4,rad=.04)
    # trigger guard, trigger
    chain(sc,[C(*q) for q in bez3((6.0,.7,0),(5.9,-.7,0),(4.4,-1.2,0),(3.35,-.8,0),n=16)]+[C(2.7,.2,0),C(2.65,.6,0)],.13,.13,silver)
    chain(sc,[C(4.1,.8,0),C(3.95,.25,0),C(3.8,-.15,0)],.09,.07,silver)
    # clockwork hammer: a smooth rimmed wheel on the side plate with a hub pin, a spur arm, and a flat link bar bolted forward
    HX,HY,HR=1.75,2.75,.88
    chain(sc,[C(*q) for q in bez3((HX,HY+.2,0),(1.2,3.8,0),(.55,4.1,0),(.5,4.8,0),n=10)],.26,.17,silver)       # hammer spur
    for sd in (-1,1):
        z=sd*.84
        sc.add(cyl((HX,HY,z),HR,.08,I3,r=.03),silver)                         # wheel face
        sc.add(torus((HX,HY,z+sd*.05),HR,.1,I3),silver)                       # rim
        sc.add(torus((HX,HY,z+sd*.08),HR*.52,.07,I3),silver)                  # inner ring
        sc.add(cyl((HX,HY,z+sd*.1),HR*.28,.06,I3,r=.02),silver); sc.add(sphere((HX,HY,z+sd*.2),.15),gem)   # hub pin
        for k in range(3):
            a=k*2*PI/3+.5; sc.add(capsule((HX+.28*HR*math.cos(a),HY+.28*HR*math.sin(a),z+sd*.09),(HX+.52*HR*math.cos(a),HY+.52*HR*math.sin(a),z+sd*.09),.045),silver)
        # flat link bar to a second pivot with a bolt
        ex,ey=4.1,1.45; sc.add(capsule((HX,HY,z+sd*.14),(ex,ey,z+sd*.14),.17,.15),silver)
        for (px_,py_) in ((ex,ey),):
            sc.add(cyl((px_,py_,z+sd*.14),.3,.07,I3,r=.02),silver); sc.add(sphere((px_,py_,z+sd*.22),.12),silver)
        sc.add(cyl((3.0,2.1,z+sd*.12),.2,.06,I3,r=.02),silver)               # mid rivet plate
        chain(sc,[C(x,3.45,sd*.78) for x in (3.9,4.6,5.5,6.6)],.06,.06,silver)
    return sc

def revolver4():
    sc=Scene()
    def wst(p): return starfield(p,4.5,.965)*.9+starfield(p+5.1,2.4,.985)*1.3
    def bst(p): return starfield(p+2.7,7.0,.955)*1.0+starfield(p+9.3,3.4,.975)*.9
    def blk(p):
        return np.array([.006,.007,.014])+np.array([.01,.02,.06])*np.clip(fbm(p*.5)*2-.6,0,1)[:,None]+wst(p)[:,None]*np.array([1,1,1.])+bst(p)[:,None]*np.array([.25,.5,1.])
    navy=Mat(base=blk,metal=.85,gloss=110,envk=.75,emis=lambda p: np.array([1,1,1.])*(wst(p)*.9)[:,None]+np.array([.25,.5,1.])*(bst(p)*1.2)[:,None])
    silver=Mat(base=(.78,.82,.92),metal=1.0,gloss=130,envk=1.0)
    wrap=Mat(base=lambda p:np.array([.2,.11,.07])*(.85+.3*fbm(p*3))[:,None],metal=0,gloss=25,bump=lambda p:.05*fbm(p*4),bk=.5)
    dark=Mat(base=(.02,.03,.07),metal=0,ao=1)
    # long tapered barrel that flares at the muzzle
    def barrel(x0,x1,yc,r0,r1,fl):
        def f(p):
            x=p[:,0]; t=np.clip((x-x0)/(x1-x0),0,1); r=r0+(r1-r0)*t+fl*np.clip((t-.88)/.12,0,1)**2
            rr=np.sqrt((p[:,1]-yc)**2+p[:,2]**2)-r; dx=np.maximum(x0-x,x-x1)
            d=np.stack([rr*.9,dx],1); return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-.03
        return f
    sc.add(barrel(5.7,15.6,2.75,.8,.46,.16),navy)
    sc.add(cyl((15.6,2.75,0),.64,.1,roty(PI/2),r=.04),silver); sc.add(cyl((15.7,2.75,0),.38,.05,roty(PI/2)),dark)
    sc.add(cyl((6.6,2.75,0),.84,.12,roty(PI/2),r=.03),silver)
    sc.add(rbox((15.0,3.45,0),(.16,.2,.05),.03),silver)
    sc.add(rbox((10.8,3.5,0),(4.4,.03,.1),.015),silver)
    # frame
    sc.add(poly([(.45,3.2),(1.1,3.55),(5.0,3.62),(5.9,3.3),(5.9,1.2),(5.2,.62),(2.6,.62),(1.9,.9),(1.0,1.7),(.5,2.5)],.8,r=.3),navy)
    sc.add(rbox((.95,3.72,0),(.22,.12,.22),.04),silver); sc.add(rbox((6.4,2.0,0),(.7,.55,.5),.15),navy)
    sc.add(cyl((7.4,1.85,0),.3,1.6,roty(PI/2),r=.03),navy); sc.add(sphere((9.1,1.85,0),.34),silver)   # lug and ejector housing
    sc.add(rbox((3.3,3.78,0),(2.2,.08,.35),.04),navy)
    for sd in (-1,1):
        z=sd*.82
        chain(sc,[C(x,3.5,sd*.78) for x in np.linspace(1.6,5.4,10)],.045,.045,silver)
    sc.add(rbox((1.0,3.78,0),(.16,.1,.2),.03),silver)
    # big smooth cylinder with a groove and bands
    sc.add(cyl((4.0,2.1,0),1.12,1.2,roty(PI/2),r=.14),navy)
    for x in (2.9,5.1): sc.add(torus((x,2.1,0),1.1,.06,roty(PI/2)),silver)
    sc.add(torus((4.0,2.1,0),1.13,.04,roty(PI/2)),silver)
    # hammer spur and rear sight
    chain(sc,[C(*q) for q in bez3((1.5,2.9,0),(.6,3.0,0),(-.1,2.9,0),(-.45,2.2,0),n=12)],.2,.09,silver)
    sc.add(sphere((-.45,2.2,0),.13),silver); sc.add(cyl((1.5,2.75,0),.28,.9,I3,r=.03),silver)
    # grip: one smooth, soft-blended crescent that flows out of the frame (no hard corners)
    path=[(1.35,2.5,.7),(1.4,1.7,.9),(1.3,.8,.95),(1.15,-.2,.92),(1.0,-1.3,.88),(.98,-2.3,.86),(1.15,-3.1,.82),(1.5,-3.65,.7),(1.95,-3.8,.55),(2.35,-3.6,.38)]
    pts_=np.array(path); 
    def blobgrip(p):
        d=None
        for (x,y,rr) in path:
            q=(p-np.array([x,y,0.]))*np.array([1,1,1.3]); di=norm(q)-rr
            d=di if d is None else smin(d,di,.55)
        return d*.9
    sc.add(blobgrip,navy)
    hatch=Mat(base=lambda p:np.where((np.sin(24*(p[:,0]+p[:,1]))*np.sin(24*(p[:,0]-p[:,1]))>0)[:,None],np.array([.55,.6,.72]),np.array([.08,.09,.13])),metal=.9,gloss=60,envk=.5)
    ang=np.linspace(0,2*PI,28,endpoint=False)
    for sd in (-1,1):
        sc.add(poly([(1.05+.5*math.cos(t),-.45+.36*math.sin(t)) for t in ang],.04,c=(0,0,sd*.78),r=.02),hatch)
        sc.add(cyl((1.75,-.25,sd*.78),.12,.05,I3,r=.03),silver)
        sc.add(torus((1.0,-3.0,sd*.0),.9,.0,I3) if False else sphere((2.3,-3.62,sd*.16),.01),silver)
    sc.add(rbox((1.9,-3.82,0),(.28,.07,.5),.06,rotz(.5)),silver)             # little toe cap
    # trigger guard + trigger
    chain(sc,[C(*q) for q in bez3((4.9,.65,0),(4.9,-1.0,0),(2.9,-1.1,0),(2.3,.35,0),n=18)],.12,.12,silver)
    chain(sc,[C(3.9,.8,0),C(3.8,.2,0),C(3.55,-.45,0)],.1,.07,silver)
    return sc

def blobsdf(path,k=.5,zs=1.3):
    P=np.array(path,float); n=len(P); t=np.linspace(0,n-1,n*6-5)
    path=[tuple(np.interp(t_,np.arange(n),P[:,c]) for c in range(3)) for t_ in t]
    def f(p):
        d=None
        for (x,y,rr) in path:
            q=(p-np.array([x,y,0.]))*np.array([1,1,zs]); di=norm(q)-rr
            d=di if d is None else smin(d,di,k)
        return d*.9
    return f

def sniper():
    sc=Scene()
    def wst(p): return starfield(p,3.2,.94)*.9+starfield(p+5.1,1.8,.975)*1.3
    def bst(p): return starfield(p+2.7,5.0,.94)*1.0+starfield(p+9.3,2.6,.965)*.9
    def blk(p):
        return np.array([.006,.007,.014])+np.array([.01,.02,.06])*np.clip(fbm(p*.4)*2-.6,0,1)[:,None]+wst(p)[:,None]*np.array([1,1,1.])+bst(p)[:,None]*np.array([.25,.5,1.])
    navy=Mat(base=blk,metal=.85,gloss=110,envk=.75,emis=lambda p: np.array([1,1,1.])*(wst(p)*.9)[:,None]+np.array([.25,.5,1.])*(bst(p)*1.2)[:,None])
    silver=Mat(base=(.8,.84,.94),metal=1.0,gloss=140,envk=1.0)
    glass=Mat(base=(.2,.4,1),metal=.2,gloss=150,emis=lambda p:np.array([.2,.45,1.0])*1.2,ao=0)
    dark=Mat(base=(.02,.03,.06),metal=0,ao=1)
    # angular chassis-style stock with raised cheek riser, angled pistol grip, flat-bottomed forend
    sc.add(poly([(-12.6,1.5),(-10.5,2.0),(-7.5,2.0),(-5.0,1.7),(-2.2,1.65),(-2.2,.2),(-3.2,-.45),(-6.5,-.65),(-9.0,-1.0),(-11.6,-2.1),(-12.6,-1.9)],.62,r=.12),navy)
    sc.add(rbox((-8.0,2.3,0),(1.6,.3,.5),.1),navy)                                            # adjustable cheek riser
    for x in (-9.0,-7.0): sc.add(rbox((x,1.95,0),(.12,.25,.3),.04),silver)
    sc.add(rbox((-12.75,-.1,0),(.22,1.85,.72),.08),silver)                                    # butt plate
    sc.add(rbox((-10.5,-.15,.64),(.9,.5,.04),.03),dark)                                       # cut-out panel
    sc.add(poly([(-1.5,.2),(-1.9,-1.4),(-2.5,-3.0),(-3.7,-3.35),(-4.1,-2.9),(-3.7,-1.2),(-3.3,.2)],.55,r=.18),navy)   # raked pistol grip
    sc.add(rbox((-2.9,-3.4,0),(.9,.14,.58),.06),silver)                                       # grip cap
    # receiver and bolt
    sc.add(rbox((1.8,.95,0),(4.4,.75,.62),.12),navy)
    sc.add(rbox((1.8,1.85,0),(4.2,.12,.34),.04),silver)                                             # scope rail
    sc.add(rbox((1.8,.3,0),(2.4,.25,.55),.15),silver)                                                 # ejection port plate
    _b0=len(sc.objs)
    chain(sc,[C(-.4,1.1,.5),C(-.9,.9,1.2),C(-1.5,.65,1.9)],.13,.13,silver); sc.add(sphere((-1.6,.62,2.05),.34),silver)   # bolt handle
    sc.add(rbox((-.3,1.0,.3),(.9,.22,.2),.08),silver)
    sc.marks=getattr(sc,'marks',{}); sc.marks['bolt']=(_b0,len(sc.objs))
    # barrel with forend, muzzle brake
    sc.add(rbox((9.6,.85,0),(4.1,.95,.66),.14),navy)
    sc.add(rbox((9.6,1.88,0),(4.0,.1,.32),.03),silver)
    for k in range(6): sc.add(rbox((6.9+k*1.05,.7,.67),(.35,.12,.03),.02),dark)                      # M-LOK slots
    sc.add(cyl((18.5,.95,0),.38,8.7,roty(PI/2),r=.04),navy)
    for x in (17.5,21.0): sc.add(torus((x,.95,0),.4,.07,roty(PI/2)),silver)
    sc.add(rbox((24.2,.95,0),(.65,.5,.5),.12),silver)
    for x in (23.8,24.2,24.6): sc.add(rbox((x,.95,0),(.07,.62,.12),.03),dark)
        # scope
    sc.add(cyl((3.6,3.1,0),.7,3.6,roty(PI/2),r=.2),navy)
    sc.add(cyl((7.6,3.1,0),1.05,1.0,roty(PI/2),r=.25),navy); sc.add(cyl((8.55,3.1,0),.82,.12,roty(PI/2)),silver); sc.add(cyl((8.62,3.1,0),.66,.05,roty(PI/2)),glass)
    sc.add(cyl((-.4,3.1,0),.9,.8,roty(PI/2),r=.2),navy); sc.add(cyl((-1.25,3.1,0),.55,.2,roty(PI/2)),silver)
    sc.add(cyl((3.5,3.1,0),.9,.12,roty(PI/2)),silver)
    for x in (1.2,6.0): sc.add(rbox((x,2.1,0),(.5,.5,.35),.12),silver); sc.add(torus((x,3.1,0),.76,.1,roty(PI/2)),silver)
    sc.add(cyl((3.6,3.95,0),.28,.4,rotx(PI/2),r=.06),silver); sc.add(cyl((4.2,3.1,.95),.28,.4,I3,r=.06),silver)  # turrets
    # magazine, trigger guard, trigger
    sc.add(poly([(1.0,.2),(3.2,.2),(3.6,-2.1),(1.5,-2.3)],.5,r=.1),navy)
    chain(sc,[C(*q) for q in bez3((-.6,-.1,0),(-.8,-1.7,0),(-2.2,-1.8,0),(-2.4,-.5,0),n=14)],.11,.11,silver)
    chain(sc,[C(-1.2,-.1,0),C(-1.3,-.7,0)],.1,.07,silver)
    # folded bipod
    for sd in (-1,1):
        chain(sc,[C(9.0,.2,sd*.95),C(10.6,-1.0,sd*1.1),C(12.0,-2.6,sd*1.2)],.14,.1,silver)
        sc.add(sphere((9.0,.25,sd*.95),.26),silver)
    return sc

def rifle2():
    sc=Scene()
    wood=Mat(base=lambda p:np.array([.33,.15,.06])[None,:]*(.65+.35*np.abs(np.sin(p[:,1]*3.2+p[:,2]*2.0+fbm(p*.35)*7)))[:,None]*(1.0+.2*fbm(p*2))[:,None],metal=.0,gloss=70,envk=.25,bump=lambda p:.015*fbm(p*3),bk=.3)
    brass=Mat(base=(.98,.72,.26),metal=1.0,gloss=120,envk=1.0)
    brass2=Mat(base=(.72,.48,.16),metal=1.0,gloss=60,envk=.8,bump=lambda p:.01*fbm(p*8),bk=.3)
    steel=Mat(base=(.12,.14,.2),metal=.95,gloss=110,envk=.9)
    def scales(p):
        a=np.sin(p[:,0]*8+p[:,1]*8)*np.sin(p[:,0]*8-p[:,2]*8+p[:,1]*3)
        return np.where((a>.15)[:,None],np.array([.12,.55,.24]),np.array([.04,.22,.1]))
    snake=Mat(base=scales,metal=.35,gloss=90,envk=.5,emis=lambda p:np.array([.0,.12,.04])*np.ones((len(p),1)))
    eye=Mat(base=(1,.85,.1),emis=lambda p:np.array([1,.8,.05])*1.6,ao=0)
    glass=Mat(base=(.4,.6,.9),metal=.2,gloss=150,emis=lambda p:np.array([1,.65,.2])*.9,ao=0)
    black=Mat(base=(.02,.02,.03),metal=0,ao=1)
    # stock: walnut with comb, wrist curve and pistol grip
    sc.add(poly([(-14.2,1.6),(-10,1.7),(-6,1.45),(-2.5,1.3),(-2.5,-1.2),(-4.8,-1.3),(-7.5,-1.3),(-10.5,-1.9),(-13.2,-2.6),(-14.2,-2.2)],.62,r=.35),wood)
    sc.add(poly([(-2.6,-.7),(-1.2,-.7),(-1.4,-2.2),(-1.9,-4.1),(-2.7,-5.5),(-4.0,-5.6),(-4.7,-4.8),(-4.4,-3.0),(-3.5,-1.5)],.62,r=.35),wood)   # full-size pistol grip
    sc.add(rbox((-3.3,-5.65,0),(.85,.12,.62),.05),brass)                                          # grip cap
    for k in range(5): sc.add(rbox((-2.2-k*.0-0.0,-2.2-k*.55,.0),(.0001,.0001,.0001),.0001),brass) if False else None
    sc.add(poly([(-14.55,1.5),(-14.55,-2.4),(-14.1,-2.6),(-14.1,1.75)],.66,r=.1),brass)                    # butt plate
    for yy in (.1,.7,-.5): sc.add(rbox((-14.35,yy,0),(.06,.08,.5),.02),black)
    # brass receiver with engraved bands
    sc.add(rbox((.4,.35,0),(3.3,1.15,.66),.28),brass)
    for x in (-2.2,-.8,2.6): sc.add(torus((x,.35,0),.0,.0,I3) if False else rbox((x,.35,0),(.07,1.2,.7),.03),brass2)
    sc.add(rbox((.2,1.62,0),(2.4,.12,.3),.04),brass2)
    # pressure gauge dial and a copper pipe to the forend
    WX,WY,WR=2.3,.75,1.75
    def sector(p):
        q=p-np.array([WX,WY,0.]); th=np.arctan2(q[:,1],q[:,0]); r_=np.sqrt(q[:,0]**2+q[:,1]**2)
        k=np.floor((th+PI)/(2*PI/8)).astype(int)%8
        cols=np.array([[.05,.1,.4],[.55,.06,.08],[.9,.7,.2],[.55,.06,.08],[.05,.1,.4],[.9,.7,.2],[.05,.1,.4],[.55,.06,.08]])
        c=cols[k]; line=(np.abs(((th+PI)/(2*PI/8))%1-.5)>.46)|(np.abs(r_-WR*.68)<.05)
        return np.where(line[:,None],np.array([.95,.78,.3]),c)
    _w0=len(sc.objs)
    sectm=Mat(base=sector,metal=.6,gloss=90,envk=.5,emis=lambda p:sector(p)*.18)
    for sd in (-1,1):
        z=sd*.72
        sc.add(cyl((WX,WY,z),WR*.97,.07,I3,r=.02),sectm)                       # painted wheel face
        sc.add(torus((WX,WY,z+sd*.04),WR,.14,I3),brass); sc.add(torus((WX,WY,z+sd*.05),WR*.68,.07,I3),brass)
        for k in range(8):                                                     # spokes and rim studs
            a_=k*PI/4+PI/8; ex,ey=WX+WR*math.cos(a_),WY+WR*math.sin(a_)
            sc.add(capsule((WX,WY,z+sd*.07),(ex,ey,z+sd*.07),.07),brass)
            sc.add(sphere((ex,ey,z+sd*.1),.17),brass)
        sc.add(cyl((WX,WY,z+sd*.06),.45,.08,I3,r=.03),brass); sc.add(sphere((WX,WY,z+sd*.2),.3),glass)
    sc.add(cone((WX,WY+WR+.35,0),.28,.7,rotx(PI/2)),brass)                      # fortune pointer
    sc.marks=getattr(sc,'marks',{}); sc.marks['wheel']=(_w0,len(sc.objs)); sc.pivots=getattr(sc,'pivots',{}); sc.pivots['wheel']=(WX,WY,0.)
    for sd in (-1,1): chain(sc,[C(2.9,-.2,sd*.72),C(4.2,-.55,sd*.75),C(5.4,-.1,sd*.78)],.1,.1,brass2)
    # bolt with a snake-head handle
    chain(sc,[C(-1.5,1.0,.5),C(-2.2,.7,1.5),C(-2.6,.1,2.4)],.2,.15,steel)
    sc.add(sphere((-2.7,.0,2.5),.4),snake); sc.add(sphere((-2.2,-.1,2.6),.3),snake)
    for s in (-1,1): sc.add(sphere((-2.45,.15,2.5+s*.22),.08),eye)
    # trigger guard + trigger
    chain(sc,[C(*q) for q in bez3((-1.1,-.75,0),(-1.1,-3.2,0),(1.5,-3.2,0),(1.5,-.75,0),n=16)],.18,.18,brass)
    chain(sc,[C(-.1,-.7,0),C(-.2,-1.6,0),C(-.45,-2.2,0)],.16,.1,steel)
    # detachable box magazine ahead of the trigger guard, with feed lips and a base plate
    sc.add(poly([(2.2,-.6),(4.6,-.6),(4.8,-3.3),(2.4,-3.4)],.5,r=.12),brass2)
    sc.add(rbox((3.55,-3.5,0),(1.3,.14,.56),.06),brass)
    for sd in (-1,1):
        sc.add(rbox((3.4,-1.9,sd*.51),(.9,.12,.03),.02),black)
        sc.add(rbox((-1.7,.5,sd*.69),(.85,.3,.03),.02),black)                                        # loading port
        for k in range(3): sc.add(sphere((-2.15+k*.45,.5,sd*.75),.1),brass)
    # forend, barrel with brass bands, front sight
    sc.add(rbox((8.6,.35,0),(5.0,.95,.62),.3),wood)
    sc.add(cyl((18.0,.35,0),.45,10.5,roty(PI/2),r=.04),steel)
    for x in (13.8,17.5,21.0,24.2): sc.add(cyl((x,.35,0),.6,.18,roty(PI/2),r=.05),brass)
    sc.add(cyl((14.0,.35,0),.8,.25,roty(PI/2),r=.08),brass)                                           # forend cap
    sc.add(rbox((22.9,1.0,0),(.14,.3,.05),.03),brass)
    # snake coiled along the forend and barrel, head raised at the muzzle end
    pts=[]
    n=140
    for k in range(n+1):
        u=k/n; x=4.6+u*19.0; R=.95 if x<14 else .72; a=u*7.0*2*PI
        pts.append(C(x,.35+R*math.cos(a),R*math.sin(a)))
    for i,q in enumerate(pts):
        u=i/n; r=.3+.1*math.sin(u*PI)+(.08 if u>.9 else 0)
        sc.add(sphere(q,r),snake)
    hd=pts[-1]+C(.6,.4,0)
    sc.add(sphere(hd,.46),snake); sc.add(sphere(hd+C(.5,-.05,0),.38),snake); sc.add(sphere(hd+C(.95,-.1,0),.28),snake)
    for s in (-1,1): sc.add(sphere(hd+C(.35,.25,s*.3),.1),eye)
    chain(sc,[hd+C(1.2,-.12,0),hd+C(1.9,-.1,0),hd+C(2.3,.12,.15)],.035,.02,black); chain(sc,[hd+C(1.9,-.1,0),hd+C(2.4,-.2,-.18)],.03,.02,black)  # forked tongue
    # brass telescope sight on rings
    sc.add(cyl((2.8,2.75,0),.5,4.2,roty(PI/2),r=.15),brass2)
    sc.add(cyl((7.4,2.75,0),.78,.9,roty(PI/2),r=.2),brass); sc.add(cyl((8.3,2.75,0),.62,.08,roty(PI/2)),glass)
    sc.add(cyl((-1.5,2.75,0),.7,.7,roty(PI/2),r=.15),brass); sc.add(cyl((-2.2,2.75,0),.45,.1,roty(PI/2)),glass)
    for x in (.6,5.0): sc.add(rbox((x,2.1,0),(.4,.55,.28),.1),brass); sc.add(cyl((x,2.75,0),.62,.22,roty(PI/2),r=.05),brass)
    sc.add(cyl((2.8,3.4,0),.22,.35,rotx(PI/2),r=.05),brass)
    return sc

def bone2():
    sc=Scene()
    def ivory_b(p):
        n=fbm(p*.22); x=np.abs(p[:,0])
        c=np.array([1.0,1.0,.98])[None,:]*(1.45+.1*n)[:,None]
        stain=np.clip((p[:,0]-3.8)*.0,0,1)
        grain=.98+.02*np.sin(p[:,1]*5+fbm(p*.5)*6)
        return c*grain[:,None]*(1-.03*np.clip(x/6,0,1))[:,None]
    def cracks(p): return np.clip(1-np.abs(fbm(p*.5+3)-.5)*26,0,1)**2
    ivory=Mat(base=lambda p: ivory_b(p)*(1-.15*cracks(p))[:,None],metal=.0,gloss=40,envk=.1,bump=lambda p:.01*fbm(p*.9),bk=.3,
              emis=lambda p:np.array([.2,1.0,.35])*(cracks(p)*.35)[:,None])
    dark=Mat(base=(.17,.1,.07),metal=.05,gloss=16,bump=lambda p:.04*np.sin(p[:,1]*7),bk=.4)
    black=Mat(base=(.02,.02,.025),metal=0,ao=1)
    teal=Mat(base=(.04,.07,.05),emis=lambda p:np.array([.25,1.0,.4])*1.3,ao=0)
    y0,y1,W,T=3.0,76.0,5.9,1.25
    chips={1:[(18,1.5),(31,1.9),(47,1.3),(58,1.0)],-1:[(24,1.7),(38,1.4),(52,1.2),(64,.8)]}
    def wf(y,s):
        w=W*(1-.16*np.clip((y-4)/60,0,1))                       # gentle taper
        w=w*np.where(y>y1-14,np.clip((y1-y)/14,0,1)**.95,1)      # long spear point
        for (cy,r) in chips[s]: w=w-r*np.exp(-((y-cy)/1.1)**2)    # bite-shaped chips
        w=w-.25*np.clip(np.sin(y*3.1+s*2)*np.sin(y*.7),0,1)
        return np.maximum(w,.02)
    def blade(p):
        x,y,z=p[:,0],p[:,1],p[:,2]; s=np.where(x>=0,1,-1)
        w=np.where(s>0,wf(y,1),wf(y,-1)); t=T*np.clip(w/W,.15,1)**.5
        u=np.abs(x)/w+np.abs(z)/t
        d1=(u-1)/np.sqrt(1/w**2+1/t**2)*.75
        d2=np.maximum(y0-y,y-y1)
        return np.maximum(d1,d2)
    sc.add(blade,ivory)
    sc.add(rbox((0,40,0),(.28,32,T+.05),.15),ivory)                 # raised mid-ridge
    sc.add(rbox((0,42,T-.15),(.14,24,.18),.08),teal)               # glowing seam
    for yb in (5.0,6.4): sc.add(rbox((0,yb,0),(W+.1,.35,T+.15),.12),dark)   # lashings at the base
    # guard: broad flat bone crossguard, swept down at the ends into points, with fang teeth underneath
    R=[(0,2.6),(5,2.4),(11,.6),(17.5,-4.2),(18.8,-6.6),(13.6,-4.8),(8,-3.6),(0,-3.8)]
    sc.add(poly([(x,y) for x,y in R]+[(-x,y) for x,y in R[::-1]],1.35,r=.55),ivory)
    sc.add(rbox((0,-.6,0),(3.0,3.3,1.9),.9),ivory)
    for s in (-1,1):
        for (x,y) in ((6,-3.9),(10,-4.6),(14,-5.5)): sc.add(cone((s*x,y-.8,0),.8,2.4,rotx(PI/2)),ivory)
        sc.add(rbox((s*8,-.2,1.45),(3.4,.12,.1),.05),dark)
    for yb in (-3.4,1.9): sc.add(rbox((0,yb,0),(3.15,.3,2.05),.12),dark)
    # long two-handed grip: sinew wrap with exposed vertebra discs
    sc.add(capsule((0,-4,0),(0,-27,0),1.15,1.05),dark)
    for k,y in enumerate(np.linspace(-5,-26,12)):
        sc.add(torus((0,y,0),1.2,.3,rotx(PI/2)),ivory if k%3==0 else Mat(base=(.38,.25,.16),metal=0,gloss=14))
    sc.add(torus((0,-28.2,0),1.7,.45,rotx(PI/2)),ivory)
    sc.add(sphere((0,-31,0),2.7),ivory); sc.add(sphere((-1.6,-33,0),1.7),ivory); sc.add(sphere((1.6,-33,0),1.7),ivory)
    return sc

m2.JOBS.update({
 'snake_rifle':(rifle2,.25,1.0,(5.0,.2),.043,(.4,.9,.5),.14),
 'starry_sniper':(sniper,.2,1.0,(5.9,.2),.043,(.3,.5,1.0),.3),
 'starry_revolver':(revolver4,.06,1.0,(7.3,.2),.108,(.3,.5,1.0),.3),
 'bone_greatsword':(bone2,-PI/4,1.0,(0,20),.0185,(.3,1.0,.45),.22),
})

from PIL import Image,ImageDraw,ImageFilter
def fire_overlay(img,ang,mc,unit,scale,N,seed=3):
    Rz=rotz(ang); sh=Rz@np.array([mc[0]*unit,mc[1]*unit,0.])
    def px(mx,my):
        w=Rz@np.array([mx*unit,my*unit,0.]); X=w[0]-sh[0]; Y=w[1]-sh[1]
        return np.array([(X/scale+1)/2*N,(1-Y/scale)/2*N])
    S=4; k0=N/352
    u=px(0,1)-px(0,0); u/=np.linalg.norm(u); nrm=np.array([-u[1],u[0]])      # u: toward the tip, nrm: across the blade
    def tongue(d,b,h,wd,ph,col,al,bend):
        L=[];R=[]
        for t in np.linspace(0,1,22):
            c=b+u*h*t+nrm*(math.sin(t*4.0+ph)*wd*.5*t+bend*t**1.5)
            hw=wd*.5*(1-t**1.2)*(1+.3*math.sin(t*9+ph*2)*t)+.5
            L.append(c-nrm*hw); R.append(c+nrm*hw)
        d.polygon([(p[0]*S,p[1]*S) for p in L+R[::-1]],fill=col+(al,))
    rng=np.random.RandomState(seed); items=[]
    for k in range(150):                        # flames rooted all over the blade, streaming to the tip
        my=rng.uniform(2,66); w_=5.9*(1-.16*min(max((my-4)/60,0),1))*(.95 if my<62 else max((76-my)/14,.05))
        b=px(rng.uniform(-1.25,1.25)*w_,my); h=rng.uniform(34,85)*k0; wd=rng.uniform(9,17)*k0
        items.append((b,h,wd,rng.uniform(0,6.28),rng.uniform(-12,12)*k0))
    for k in range(26):                         # big plume streaming off the tip
        b=px(rng.uniform(-3,3),rng.uniform(55,74)); items.append((b,rng.uniform(80,150)*k0,rng.uniform(12,24)*k0,rng.uniform(0,6.28),rng.uniform(-20,20)*k0))
    layers=[]
    for col,sc_,al in (((10,150,60),1.0,100),((40,215,90),.8,160),((140,255,130),.58,205),((235,255,225),.32,245)):
        L=Image.new('RGBA',(N*S,N*S),(0,0,0,0)); d=ImageDraw.Draw(L)
        for (b,h,wd,ph,bend) in items: tongue(d,b,h*sc_,wd*sc_,ph,col,al,bend*sc_)
        L=L.filter(ImageFilter.GaussianBlur(S*1.4)).resize((N,N),Image.LANCZOS); layers.append(L)
    # translucent front pass so the fire covers the blade itself
    F=Image.new('RGBA',(N*S,N*S),(0,0,0,0)); d=ImageDraw.Draw(F)
    for (b,h,wd,ph,bend) in items[:150]: tongue(d,b,h*.55,wd*.7,ph,(90,240,120),48,bend*.5)
    F=F.filter(ImageFilter.GaussianBlur(S*1.6)).resize((N,N),Image.LANCZOS)
    E=Image.new('RGBA',(N*S,N*S),(0,0,0,0)); d=ImageDraw.Draw(E)
    for k in range(70):
        c=px(rng.uniform(-8,8),rng.uniform(10,80))+u*rng.uniform(10,120)*k0+nrm*rng.uniform(-25,25)*k0; r=rng.uniform(.8,2.2)*k0
        d.ellipse([(c[0]-r)*S,(c[1]-r)*S,(c[0]+r)*S,(c[1]+r)*S],fill=(170,255,150,230))
    E=E.filter(ImageFilter.GaussianBlur(S*.7)).resize((N,N),Image.LANCZOS)
    base=Image.fromarray((np.clip(img,0,1)*255).astype('uint8'),'RGBA')
    out=Image.new('RGBA',(N,N),(0,0,0,0))
    for L in layers: out=Image.alpha_composite(out,L)
    out=Image.alpha_composite(out,base); out=Image.alpha_composite(out,F); out=Image.alpha_composite(out,E)
    return np.asarray(out).astype(float)/255.

if __name__=='__main__':
    import os,time; NN=int(os.environ.get('NN','256'))
    for n in sys.argv[1:]:
        f,ang,vs,mc,unit,ac,ast=m2.JOBS[n]; t=time.time()
        img=render(f(),N=NN,ang=ang,scale=vs,mc=mc,unit=unit)
        if n=='bone_greatsword': img=fire_overlay(img,ang,mc,unit,vs,NN)
        img=bloom(img); img=aura(img,ac,ast,NN//30); save(img,f'/tmp/r3/n_{n}.png'); print(n,round(time.time()-t,1),flush=True)
