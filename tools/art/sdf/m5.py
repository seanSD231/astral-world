import sys; sys.path.insert(0,'/tmp/r3')
from m4 import *
import m2
from m2 import *
def S_(a,b): return np.maximum(a,-b)

def rows(p,x0,x1,sp,y,z,r,axis=0):
    """a row of small domes (rivets) along x between x0..x1 with spacing sp, at height y,z."""
    q=np.stack([np.clip(np.round((p[:,0]-x0)/sp)*sp+x0,x0,x1),np.full(len(p),y),np.full(len(p),z)],1)
    return norm(p-q)-r
def gear_fn(cx,cy,z0,R,teeth,hz,hub=.25,depth=.2,spin=0.0):
    def f(p):
        q=p-np.array([cx,cy,z0]); r_=np.sqrt(q[:,0]**2+q[:,1]**2); th=np.arctan2(q[:,1],q[:,0])+spin
        tooth=np.clip(np.sin(teeth*th)*3,-1,1)*.5+.5
        dxy=r_-(R-depth*.5+depth*tooth)
        dxy=np.maximum(dxy,-(r_-hub))
        ring=np.maximum(np.abs(r_-R*.62)-R*.1,0)*0
        spokes=np.maximum(np.minimum(np.abs(np.sin(3*th))*r_-R*.12,1e3),0)*0
        dz=np.abs(q[:,2])-hz
        w=np.stack([dxy,dz],1); return np.minimum(w.max(1),0)+norm(np.maximum(w,0))-.02
    return f
def shroud_fn(x0,x1,yc,R,rin,holes_n=14,hsp=1.0,hr=.24,slot=None):
    def f(p):
        q=p-np.array([0,yc,0.]); r_=np.sqrt(q[:,1]**2+q[:,2]**2); th=np.arctan2(q[:,2],q[:,1])
        dx=np.maximum(x0-p[:,0],p[:,0]-x1)
        shell=np.maximum(np.abs(r_-(R+rin)/2)-(R-rin)/2,dx)
        row=np.floor((p[:,0]-x0)/hsp+.5); step=2*PI/holes_n
        thr=th+(row%2)*step/2
        u=((thr+step/2)%step-step/2)*R; v=((p[:,0]-x0)%hsp)-hsp/2
        dh=np.sqrt(u*u+v*v)-hr
        d=np.maximum(shell,-dh)
        if slot is not None:
            sx0,sx1,sth,sw=slot
            dth=np.abs(((th-sth+PI)%(2*PI))-PI)*R
            dsl=np.maximum(np.maximum(sx0-p[:,0],p[:,0]-sx1),dth-sw)
            d=np.maximum(d,-dsl)
        return d
    return f
def plate_perf(cx,cy,hx,hy,hz,sp,hr,r=.12):
    base=rbox((cx,cy,0),(hx,hy,hz),r)
    def f(p):
        b=base(p); u=((p[:,0]-cx+sp/2)%sp)-sp/2; v=((p[:,1]-cy+sp/2)%sp)-sp/2
        dh=np.sqrt(u*u+v*v)-hr
        inner=(np.abs(p[:,0]-cx)<hx-.35)&(np.abs(p[:,1]-cy)<hy-.35)
        return np.where(inner,np.maximum(b,-dh),b)
    return f

def rifle3():
    sc=Scene()
    def aged(p,base,k=.12):
        n=fbm(p*.9); w=np.clip(fbm(p*2.5+3)*2-.9,0,1)
        c=np.array(base)[None,:]*(.82+.3*n)[:,None]
        return c+np.array([.28,.26,.2])[None,:]*(w*k*2)[:,None]
    steel=Mat(base=lambda p:aged(p,(.2,.23,.21)),metal=.85,gloss=70,envk=.55,bump=lambda p:.006*fbm(p*3.5),bk=.25)
    steel2=Mat(base=lambda p:aged(p,(.12,.14,.13)),metal=.85,gloss=60,envk=.5,bump=lambda p:.005*fbm(p*3),bk=.25)
    brass=Mat(base=lambda p:aged(p,(.82,.58,.22),.18),metal=1.0,gloss=90,envk=1.0,bump=lambda p:.004*fbm(p*4),bk=.2)
    copper=Mat(base=lambda p:aged(p,(.74,.38,.2),.15),metal=1.0,gloss=80,envk=.9)
    def lea(p):
        st=np.abs(np.sin(p[:,0]*6.0))<.06
        c=np.array([.26,.11,.08])[None,:]*(.75+.5*fbm(p*3))[:,None]
        return np.where(st[:,None],np.array([.75,.6,.35]),c)
    leather=Mat(base=lea,metal=.05,gloss=24,envk=.15,bump=lambda p:.02*fbm(p*5),bk=.4)
    glassm=Mat(base=(.9,.55,.15),metal=.1,gloss=140,emis=lambda p:np.array([1,.5,.1])*1.3,ao=0)
    lens=Mat(base=(.5,.65,.9),metal=.2,gloss=150,emis=lambda p:np.array([.2,.4,.9])*.7,ao=0)
    snake=Mat(base=lambda p:np.where((np.sin(p[:,0]*8+p[:,1]*8)*np.sin(p[:,0]*8-p[:,2]*8+p[:,1]*3)>.15)[:,None],np.array([.12,.55,.24]),np.array([.04,.22,.1])),metal=.35,gloss=90,envk=.5,emis=lambda p:np.array([0,.12,.04])*np.ones((len(p),1)))
    eye=Mat(base=(1,.85,.1),emis=lambda p:np.array([1,.8,.05])*1.6,ao=0)
    black=Mat(base=(.02,.02,.03),metal=0,ao=1)
    # ---- receiver: thick steel body with a window onto working gears and an orange glass tube
    def recv(p):
        b=rbox((5.8,.3,0),(8.4,1.55,.68),.35)(p)
        win=np.maximum(rbox((9.6,.35,0),(2.0,.62,2),.25)(p),0)  # window cut through
        win2=rbox((9.6,.35,0),(2.0,.62,2),.25)(p)
        return np.maximum(b,-win2)
    sc.add(recv,steel)
    sc.add(rbox((5.8,1.88,0),(8.4,.12,.5),.06),brass)                                     # top rail
    for k in range(14): sc.add(rbox((-1.9+k*1.15,2.0,0),(.28,.08,.5),.02),steel2) if k%1==0 and False else None
    sc.add(lambda p:np.maximum(rbox((5.8,2.02,0),(8.0,.14,.42),.03)(p),-(np.abs(((p[:,0]+.5)%1.1)-.55)-.32)),steel2)   # picatinny teeth
    sc.add(lambda p:rows(p,-2.0,13.8,.9,1.3,.69,.07),brass)                               # rivet rows
    sc.add(lambda p:rows(p,-2.0,13.8,.9,1.3,-.69,.07),brass)
    sc.add(lambda p:rows(p,-2.0,13.8,.9,-.7,.69,.07),brass)
    sc.add(lambda p:rows(p,-2.0,13.8,.9,-.7,-.69,.07),brass)
    for sd in (-1,1): sc.add(lambda p,sd=sd:plate_perf(12.6,.3,1.5,.95,.07,.5,.14)(p-np.array([0,0,sd*.7])),brass)
    # inside the window: gear train, glass tube, pipes
    sc.add(gear_fn(8.6,.6,0,.75,14,.18),brass); sc.add(gear_fn(10.0,.0,.1,.55,10,.18,spin=.2),copper); sc.add(gear_fn(10.6,.95,-.1,.42,8,.18,spin=.4),brass)
    sc.add(gear_fn(11.4,.1,0.0,.62,12,.18,spin=.1),steel2)
    sc.add(cyl((9.6,.05,.2),.38,1.3,roty(PI/2),r=.1),glassm)                              # glass tube with glowing fluid
    for x in (8.3,10.9): sc.add(cyl((x,.05,.2),.46,.14,roty(PI/2),r=.04),brass)
    # ---- the wheel of fortune (animated)
    WX,WY,WR=2.6,.55,1.75
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
        sc.add(cyl((WX,WY,z),WR*.97,.07,I3,r=.02),sectm)
        sc.add(torus((WX,WY,z+sd*.04),WR,.14,I3),brass); sc.add(torus((WX,WY,z+sd*.05),WR*.68,.07,I3),brass)
        for k in range(8):
            a_=k*PI/4+PI/8; ex,ey=WX+WR*math.cos(a_),WY+WR*math.sin(a_)
            sc.add(capsule((WX,WY,z+sd*.07),(ex,ey,z+sd*.07),.07),brass); sc.add(sphere((ex,ey,z+sd*.1),.17),brass)
        sc.add(cyl((WX,WY,z+sd*.06),.45,.08,I3,r=.03),brass); sc.add(sphere((WX,WY,z+sd*.2),.3),lens)
    sc.add(cone((WX,WY+WR+.35,0),.28,.7,rotx(PI/2)),brass)
    sc.marks=getattr(sc,'marks',{}); sc.marks['wheel']=(_w0,len(sc.objs)); sc.pivots=getattr(sc,'pivots',{}); sc.pivots['wheel']=(WX,WY,0.)
    # ---- perforated shroud with visible rods, steel muzzle cap with glowing bore
    sc.add(shroud_fn(13.8,24.0,.35,1.5,1.18,slot=(14.8,20.5,-PI/2,.55)),brass)
    for a_ in (0,2.094,4.188): sc.add(cyl((19.0,.35+.78*math.cos(a_),.78*math.sin(a_)),.18,5.2,roty(PI/2),r=.04),copper)
    sc.add(cyl((18.5,.35,0),.55,9.0,roty(PI/2),r=.05),steel2)                              # barrel
    for x in (14.1,15.3,16.5): sc.add(cyl((x,.35,0),1.62-.05*(x-14),.2,roty(PI/2),r=.06),brass)
    sc.add(cyl((24.3,.35,0),1.62,.5,roty(PI/2),r=.14),steel)
    sc.add(torus((24.82,.35,0),1.0,.14,roty(PI/2)),Mat(base=(1,.9,.7),emis=lambda p:np.array([1,.85,.6])*2.2,ao=0))
    sc.add(lambda p:rows(p,-1,1,1,0,0,.0001)*0+1e3,steel)
    for k in range(10):
        a_=k*PI/5; sc.add(sphere((24.45,.35+1.55*math.cos(a_),1.55*math.sin(a_)),.1),brass)
    # ---- copper pipes with collars
    for sd in (-1,1):
        pts=[C(*q) for q in bez3((4.5,.3,sd*.78),(6.0,-.9,sd*.9),(8.0,-1.0,sd*.9),(9.8,-.35,sd*.8),n=14)]
        chain(sc,pts,.13,.13,copper)
        for i in (3,7,11): sc.add(torus((pts[i][0],pts[i][1],pts[i][2]),.2,.07,roty(PI/2)),brass)
        pts2=[C(*q) for q in bez3((11.8,.9,sd*.78),(12.6,1.6,sd*.8),(13.2,1.6,sd*.8),(13.6,.9,sd*.78),n=10)]
        chain(sc,pts2,.12,.12,copper)
    # ---- stock: angular steel frame, leather cheek pad and butt pad, triangle cut-out
    def stock(p):
        a=poly([(-13.6,2.4),(-9.0,2.1),(-2.6,1.7),(-2.6,-.8),(-5.5,-.8),(-9.2,-2.6),(-13.6,-3.4)],.55,r=.2)(p)
        cut=poly([(-11.0,.9),(-5.2,.2),(-8.2,-1.6)],1.2,r=.35)(p)
        return np.maximum(a,-cut)
    sc.add(stock,steel)
    sc.add(rbox((-8.3,2.25,0),(3.6,.42,.62),.2),leather)                                  # cheek pad
    sc.add(rbox((-13.9,-.5,0),(.55,2.9,.66),.2),leather)                                   # butt pad
    sc.add(rbox((-14.5,-.5,0),(.12,3.05,.68),.05),brass)
    chain(sc,[C(-10.4,-.5,.0),C(-9.6,-.1,.0),C(-9.0,-.6,0),C(-8.4,-.1,0),C(-7.8,-.7,0)],.12,.12,copper)   # coil in the cut-out
    # ---- pistol grip (leather) and trigger group
    sc.add(poly([(-2.6,-.7),(-1.0,-.7),(-1.3,-2.3),(-1.9,-4.2),(-2.7,-5.6),(-4.1,-5.7),(-4.8,-4.8),(-4.5,-3.0),(-3.6,-1.5)],.6,r=.3),leather)
    sc.add(rbox((-3.4,-5.75,0),(.9,.12,.64),.05),brass)
    chain(sc,[C(*q) for q in bez3((-1.0,-.75,0),(-1.0,-3.3,0),(1.6,-3.3,0),(1.6,-.75,0),n=16)],.17,.17,brass)
    chain(sc,[C(-.1,-.7,0),C(-.2,-1.6,0),C(-.45,-2.2,0)],.16,.1,steel2)
    # ---- magazine with brass shells and a gauge porthole
    sc.add(poly([(1.9,-.7),(4.9,-.7),(5.1,-4.6),(2.1,-4.7)],.5,r=.15),steel)
    for sd in (-1,1):
        sc.add(rbox((3.5,-2.0,sd*.5),(.9,1.5,.03),.02),steel2)
        for k in range(3): sc.add(cyl((2.9,-1.5-k*1.0,sd*.58),.34,.22,I3,r=.1),brass)
        sc.add(torus((4.15,-3.7,sd*.56),.4,.1,I3),brass); sc.add(cyl((4.15,-3.7,sd*.55),.34,.05,I3),lens)
    sc.add(rbox((3.5,-4.78,0),(1.6,.14,.56),.06),brass)
    # ---- ring sight on rail
    sc.add(rbox((6.6,2.25,0),(.6,.2,.4),.06),steel2)
    sc.add(torus((6.6,3.5,0),1.05,.1,roty(PI/2)),steel); sc.add(torus((6.6,3.5,0),.62,.05,roty(PI/2)),brass)
    chain(sc,[C(6.6,2.45,0),C(6.6,2.7,0)],.2,.2,steel)
    for ang in (0,PI/2): chain(sc,[C(6.6,3.5+.95*math.sin(ang),.95*math.cos(ang)),C(6.6,3.5-.95*math.sin(ang),-.95*math.cos(ang))],.03,.03,steel)
    # ---- bolt with snake-head handle
    chain(sc,[C(0.0,1.5,.55),C(-.7,1.3,1.5),C(-1.1,.8,2.3)],.2,.15,steel)
    sc.add(sphere((-1.2,.7,2.4),.4),snake); sc.add(sphere((-.7,.6,2.5),.3),snake)
    for s in (-1,1): sc.add(sphere((-.95,.8,2.4+s*.22),.08),eye)
    # ---- snake coiled around the shroud, head raised at the muzzle
    pts=[]; n=120
    for k in range(n+1):
        u=k/n; x=14.4+u*9.4; R=1.88; a=u*5.0*2*PI
        pts.append(C(x,.35+R*math.cos(a),R*math.sin(a)))
    for i,q in enumerate(pts):
        u=i/n; sc.add(sphere(q,.28+.09*math.sin(u*PI)+(.07 if u>.9 else 0)),snake)
    hd=pts[-1]+C(.6,.5,0)
    sc.add(sphere(hd,.46),snake); sc.add(sphere(hd+C(.5,-.05,0),.38),snake); sc.add(sphere(hd+C(.95,-.1,0),.28),snake)
    for s in (-1,1): sc.add(sphere(hd+C(.35,.25,s*.3),.1),eye)
    chain(sc,[hd+C(1.2,-.12,0),hd+C(1.9,-.1,0),hd+C(2.3,.12,.15)],.035,.02,black); chain(sc,[hd+C(1.9,-.1,0),hd+C(2.4,-.2,-.18)],.03,.02,black)
    return sc
m2.JOBS['snake_rifle']=(rifle3,.25,1.0,(5.0,.2),.043,(.4,.9,.5),.14)
if __name__=='__main__':
    import time; t=time.time()
    img=render(rifle3(),N=700,ang=.25,scale=1.0,mc=(5.0,.2),unit=.043); img=bloom(img); save(img,'/tmp/r3/n_snake_rifle.png'); print(time.time()-t)
