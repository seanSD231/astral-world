import sys; sys.path.insert(0,'/tmp/r3')
from eng import *
from scipy.ndimage import gaussian_filter
PI=math.pi
def C(*a): return np.array(a,float)
def aura(img,col,strength=.5,rad=10,ibase=None):
    a=img[...,3]; g=(gaussian_filter(a,rad*1.6)**1.6)[...,None]*strength*1.6
    out=img.copy(); rgb=img[...,:3]*img[...,3:4]+np.array(col)*g*(1-img[...,3:4])
    al=np.clip(img[...,3]+g[...,0]*(1-img[...,3]),0,1)
    out[...,:3]=rgb/np.maximum(al[...,None],1e-6); out[...,3]=al; return out
def bloom(img,k=.9,rad=5):
    lum=img[...,:3].max(-1)*img[...,3]; mask=np.clip((lum-.8)*4,0,1)[...,None]; g=gaussian_filter(img[...,:3]*mask,(rad,rad,0))*k
    out=img.copy(); out[...,:3]=np.clip(img[...,:3]*img[...,3:4]+g,0,1); al=np.clip(img[...,3]+g.max(-1),0,1); out[...,:3]=out[...,:3]/np.maximum(al[...,None],1e-6); out[...,3]=al; return out
def starfield(pp,scale=60,th=.975):
    cell=np.floor(pp*scale); h=hash3(cell); f=pp*scale-cell-.5; r=norm(f); return np.clip(1-r*2.4,0,1)*(h>th)
def stripes(x,freq,w=.5): return (np.sin(x*freq)>(1-2*w)).astype(float)

# ---------- A: Spear of Sun & Justice ----------
def spear():
    sc=Scene()
    gold=Mat(base=lambda p: np.array([1.0,.7,.18])*(.85+.15*stripes(p[:,1],40,.3))[:,None],metal=.9,gloss=70,envk=1.0)
    white=Mat(base=(.98,.95,.85),metal=.4,gloss=40)
    hot=Mat(base=(1,.85,.4),metal=.2,gloss=20,emis=lambda p:np.array([1.0,.7,.2])*.9)
    dk=Mat(base=(.35,.2,.05),metal=.6,gloss=30)
    sc.add(capsule((0,-.93,0),(0,.28,0),.026),gold)
    for y in (-.75,-.5,-.25,0.0): sc.add(torus((0,y,0),.034,.012,rotx(PI/2)),white if y<-.4 else gold)
    sc.add(cone((0,-.95,0),.05,.1,rotx(-PI/2)),gold)    # butt spike (tip -y)
    # blade
    outl=[(0,1.0),(.05,.88),(.085,.7),(.095,.55),(.07,.42),(.035,.36),(-.035,.36),(-.07,.42),(-.095,.55),(-.085,.7),(-.05,.88)]
    sc.add(poly(outl,.014,r=.008),gold)
    sc.add(poly([(0,.99),(.02,.8),(.03,.6),(.015,.4),(-.015,.4),(-.03,.6),(-.02,.8)],.028,r=.012),Mat(base=(1,.9,.5),metal=.85,gloss=90,envk=1.0))
    # sun ornament
    sc.add(cyl((0,.22,.0),.115,.03,r=.01),gold)
    sc.add(torus((0,.22,.03),.1,.012),white)
    sc.add(sphere((0,.22,.03),.07),hot)
    for i in range(16):
        a=i*2*PI/16; L=.2 if i%2==0 else .15; d=np.array([math.cos(a),math.sin(a),0]); 
        sc.add(capsule(C(0,.22,0)+d*.1,C(0,.22,0)+d*L,.022,.004),hot if i%2==0 else gold)
    # scales of justice on the sun's face
    sc.add(rbox((0,.22,.055),(.004,.04,.004)),dk); sc.add(rbox((0,.255,.055),(.05,.004,.004)),dk)
    for s in (-1,1):
        sc.add(capsule((s*.05,.255,.055),(s*.05,.225,.055),.0025),dk); sc.add(torus((s*.05,.215,.055),.018,.004,rotx(PI/2.6)),dk)
    # ribbons
    def ribbon(x0,sw,col):
        pts=[(x0+sw*math.sin(i*.5)*i*.01,.12-i*.045) for i in range(10)]
        for a,b in zip(pts,pts[1:]): sc.add(capsule((a[0],a[1],.0),(b[0],b[1],.0),.013,.011),col)
    return sc
# ---------- B: Storm katana ----------
def katana():
    sc=Scene()
    def cx(y): return .07*((y+.15)/1.0)**2       # sori (curve)
    ys=np.linspace(-.18,.95,28)
    def hw(y):
        t=(y+.18)/1.13; w=.068*(1-.2*t)
        if t>.93: w*=max(0,(1-t)/.07)**.7
        return w
    L=[(cx(y)-hw(y),y) for y in ys]; R_=[(cx(y)+hw(y),y) for y in ys[::-1]]
    steel=Mat(base=lambda p: (np.array([.45,.52,.68])*(.75+.35*np.clip(.5+.5*np.sin(p[:,1]*30+np.sin(p[:,0]*140)*2)*(np.abs(p[:,0]-.07*((p[:,1]+.15))**2)/.05),0,1)[:,None])),metal=.85,gloss=90,envk=.55,
              emis=lambda p: np.array([.5,.8,1.0])*(np.clip(1-np.abs(fbm(p*np.array([9,9,9])+3)-.52)*18,0,1)**2*1.3)[:,None])
    sc.add(poly(L+R_,.007,r=.004),steel)
    sc.add(poly([(cx(y)-.002,y) for y in ys]+[(cx(y)+.002,y) for y in ys[::-1]],.014,r=.006),steel)   # ridge line
    gold=Mat(base=(.85,.62,.2),metal=.9,gloss=60,envk=1.0)
    dark=Mat(base=(.12,.14,.26),metal=.4,gloss=30)
    wrap=Mat(base=lambda p:(np.array([.10,.13,.30])+np.array([.2,.3,.6])*(np.abs(np.sin((p[:,1]+p[:,0])*60))*np.abs(np.sin((p[:,1]-p[:,0])*60))>.8)[:,None]),metal=.3,gloss=25)
    sc.add(cyl((cx(-.2),-.2,0),.115,.012,r=.004),gold)
    for i in range(8):
        a=i*2*PI/8; sc.add(sphere((cx(-.2)+.105*math.cos(a),-.2+.105*math.sin(a),0),.022),gold)
    sc.add(capsule((.0,-.22,0),(-.04,-.8,0),.032),wrap)
    sc.add(capsule((-.04,-.8,0),(-.045,-.86,0),.036),gold)
    sc.add(torus((cx(-.2),-.2,0),.04,.01,rotx(PI/2)),gold)
    # lightning crawling off the blade
    elec=Mat(base=(.8,.95,1),metal=0,gloss=10,emis=lambda p:np.array([.55,.85,1.0])*2.2,ao=0)
    rng=np.random.RandomState(11)
    for k in range(11):
        y=-.02+rng.rand()*.93; side=1 if k%2 else -1; x=cx(y)+side*hw(y); pts=[(x,y,.015)]; ang=(0 if side>0 else PI)+rng.randn()*.5+.5*side*-1*0
        for s in range(5):
            ang+=rng.randn()*.8; x+=.04*math.cos(ang); y+=.04*math.sin(ang)+.012; pts.append((x,y,.015+rng.randn()*.01))
        for a_,b_ in zip(pts,pts[1:]): sc.add(capsule(a_,b_,.0055,.004),elec)
        if k%3==0:
            bx,by,_=pts[2]; ang2=ang+1.2; p2=(bx+.05*math.cos(ang2),by+.05*math.sin(ang2),.015); sc.add(capsule(pts[2],p2,.004,.003),elec)
    return sc
# ---------- C: Bone greatsword ----------
def bone():
    sc=Scene()
    ivory=Mat(base=lambda p: np.array([1.0,.93,.74])*(.9+.2*fbm(p*14))[:,None]*(1-.4*np.clip(1-np.abs(fbm(p*np.array([22,22,22])+7)-.5)*16,0,1)**2)[:,None],metal=.05,gloss=18,envk=.3,ao=1,emis=lambda p:np.array([.16,.13,.09])*(.5+fbm(p*9))[:,None])
    dark=Mat(base=(.28,.16,.1),metal=.1,gloss=12)
    glowm=Mat(base=(.05,.07,.07),metal=0,gloss=5,emis=lambda p:np.array([.2,.9,.8])*.9)
    # blade outline: wide, jagged
    right=[(.04,.95),(.1,.82),(.13,.7),(.2,.62),(.17,.5),(.24,.42),(.19,.3),(.26,.2),(.2,.08),(.17,-.1)]
    left=[(-.04,.95),(-.1,.8),(-.15,.68),(-.17,.56),(-.22,.5),(-.2,.38),(-.27,.3),(-.19,.18),(-.22,.06),(-.17,-.1)]
    sc.add(poly(right+[(-x,y) for x,y in left[::-1]][::0+1] if False else (right+left[::-1]),.03,r=.015),ivory)
    # spine of vertebrae along the middle
    for i in range(11):
        y=-.1+i*.088; w=.06*(1-.35*i/11); sc.add(rbox((0,y,.035),(w,.034,.03),.02),ivory)
        for s in (-1,1): sc.add(cone((s*w*.9,y,.035),.022,.09,rotz(-s*PI/2+0)@roty(PI/2)*0+rotz(-s*PI/2)@roty(PI/2)),ivory) if False else None
    # rib guard
    for s in (-1,1):
        for k in range(4):
            pts=[]; 
            for u in np.linspace(0,1,9):
                x=s*(.08+(.25+.05*k)*u); y=-.12-.03*k-(.26*u*u)*(1+.3*k)+.04*math.sin(u*PI)*0
                pts.append((x,y,.0))
            for a,b in zip(pts,pts[1:]): sc.add(capsule(a,b,.024-.004*k,.018-.003*k),ivory)
    sc.add(sphere((0,-.14,0),.065),ivory)
    sc.add(capsule((0,-.2,0),(0,-.62,0),.034),dark)
    for y in np.linspace(-.24,-.6,7): sc.add(torus((0,y,0),.036,.01,rotx(PI/2)),Mat(base=(.15,.1,.07),gloss=10))
    # skull pommel
    sc.add(sphere((0,-.72,0),.095),ivory); sc.add(rbox((0,-.8,.03),(.06,.03,.05),.02),ivory)
    for s in (-1,1): sc.add(sphere((s*.04,-.71,.075),.028),glowm)
    sc.add(rbox((0,-.74,.09),(.012,.02,.01),.005),Mat(base=(.05,.05,.05)))
    return sc
# ---------- D: Starry revolver (faces right) ----------
def revolver():
    sc=Scene()
    def sky_base(p):
        n=fbm(p*4+1.5); c=np.array([.015,.02,.07])+np.array([.03,.12,.5])*np.clip(n*1.8-.55,0,1)[:,None]*1.6
        s=starfield(p,70,.972)[:,None]; s2=starfield(p+3.1,130,.985)[:,None]
        return c+s*np.array([1,1,1.0])*1.5+s2*np.array([.7,.85,1.0])
    body=Mat(base=sky_base,metal=.8,gloss=90,envk=.9,emis=lambda p: np.array([.5,.7,1.0])*(starfield(p,70,.972)*1.5)[:,None])
    silver=Mat(base=(.62,.7,.9),metal=.95,gloss=100,envk=1.1)
    glowb=Mat(base=(.2,.4,1),metal=0,gloss=10,emis=lambda p:np.array([.2,.5,1.0])*1.1,ao=0)
    wood=Mat(base=lambda p:(np.array([.05,.06,.14])+np.array([.1,.18,.4])*np.clip(fbm(p*6)-.4,0,1)[:,None]*1.5)*1.0+starfield(p,80,.975)[:,None]*1.3,metal=.35,gloss=60)
    # barrel
    sc.add(rbox((.62,.18,0),(.4,.04,.04),.02),body)
    sc.add(rbox((.55,.12,0),(.28,.03,.03),.012),body); sc.add(rbox((.62,.22,0),(.38,.008,.008),.004),silver)
    for x in (.3,.62,.95): sc.add(rbox((x,.18,0),(.01,.052,.052),.006),silver)
    sc.add(rbox((1.0,.235,0),(.012,.018,.006),.004),silver)
    sc.add(cyl((1.02,.18,0),.02,.05,roty(PI/2),r=.004),glowb)   # muzzle glow
    # frame: recoil shield, top strap, bottom rail, front block (the cylinder sits in the window between them)
    sc.add(poly([(-.4,.34),(-.17,.36),(-.17,-.03),(-.3,.0),(-.38,.12)],.045,r=.02),body)
    sc.add(rbox((.1,.345,0),(.32,.022,.045),.012),body); sc.add(rbox((.1,.0,0),(.32,.03,.045),.012),body)
    sc.add(rbox((.36,.18,0),(.07,.18,.045),.02),body)
    sc.add(rbox((.12,.372,0),(.3,.007,.02),.003),silver)
    # fluted cylinder
    def fluted(c,rad,hh):
        c=np.array(c,float)
        def f(p):
            q=p-c; r=np.sqrt(q[:,1]**2+q[:,2]**2); th=np.arctan2(q[:,2],q[:,1]); rr=rad-.02*np.clip(np.cos(6*th)*2.2-1.1,0,1)
            d=np.stack([r-rr,np.abs(q[:,0])-hh],1); return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-.008
        return f
    sc.add(fluted((.1,.17,0),.15,.17),body)
    for s in (-1,1): sc.add(cyl((.1+s*.175,.17,0),.1,.012,roty(PI/2),r=.004),silver)
    for s in (-1,1): sc.add(torus((.1+s*.12,.17,0),.15,.008,roty(PI/2)),silver)
    sc.add(sphere((.1+.185,.17,0),.035),glowb)
    # hammer
    sc.add(poly([(-.36,.34),(-.52,.44),(-.62,.42),(-.56,.34),(-.4,.22)],.02,r=.008),silver)
    # grip (raked back)
    grip=[(-.34,.06),(-.1,.06),(-.14,-.12),(-.3,-.5),(-.46,-.62),(-.6,-.56),(-.56,-.36),(-.44,-.1)]
    sc.add(poly(grip,.052,r=.028),wood)
    inl=[(-.3,-.12),(-.24,-.12),(-.34,-.46),(-.46,-.55),(-.5,-.46)]
    sc.add(poly(inl,.058,r=.005),silver)
    for k,(x,y) in enumerate([(-.34,-.28),(-.4,-.4)]): sc.add(sphere((x,y,.062),.018-.003*k),glowb)
    # trigger guard and trigger
    sc.add(torus((-.03,-.11,0),.11,.013,rotz(0)),silver)
    sc.add(capsule((-.02,-.03,0),(-.04,-.12,0),.012,.006),silver)
    return sc
# ---------- E: Shadow sword & shield ----------
def shadowmat():
    return Mat(base=lambda p: np.array([.05,.02,.1])*(.7+.6*fbm(p*5))[:,None],metal=.2,gloss=20,rim=(.55,.28,1.0),
               emis=lambda p: np.array([.5,.2,.95])*(np.clip(1-np.abs(fbm(p*np.array([6,6,6])+9)-.5)*9,0,1)**2*.8+.08)[:,None],envk=.15,ao=0)
def smoke(fn,amp=.012,f=7):
    return lambda p: fn(p)+amp*(fbm(p*f)-.5)*2
def shadow_sword():
    sc=Scene(); m=shadowmat()
    sc.add(smoke(poly([(0,.98),(.05,.85),(.075,.65),(.07,-.1),(-.07,-.1),(-.075,.65),(-.05,.85)],.018,r=.01)),m)
    sc.add(smoke(poly([(0,.9),(.014,.6),(0,-.05),(-.014,.6)],.03,r=.01)),m)
    sc.add(smoke(poly([(-.3,-.11),(-.18,-.07),(0,-.14),(.18,-.07),(.3,-.11),(.2,-.19),(0,-.18),(-.2,-.19)],.03,r=.012)),m)
    sc.add(smoke(capsule((0,-.18,0),(0,-.6,0),.03)),m); sc.add(sphere((0,-.66,0),.06),m)
    rng=np.random.RandomState(4)
    for k in range(9):
        x=(rng.rand()-.5)*.25; y=-.05+rng.rand()*.8; pts=[(x,y,0)]
        for s in range(6): x+=rng.randn()*.025; y+=.05+rng.rand()*.03; pts.append((x,y,rng.randn()*.01))
        for a,b in zip(pts,pts[1:]): sc.add(capsule(a,b,.012*(1-k*.04),.006),m)
    return sc
def shadow_shield():
    sc=Scene(); m=shadowmat()
    kite=[(0,.95),(.5,.78),(.58,.2),(.4,-.35),(0,-.95),(-.4,-.35),(-.58,.2),(-.5,.78)]
    sc.add(smoke(poly(kite,.04,r=.02),.016,5),m)
    inner=[(0,.8),(.4,.66),(.46,.2),(.32,-.28),(0,-.78),(-.32,-.28),(-.46,.2),(-.4,.66)]
    sc.add(smoke(poly(inner,.07,r=.03),.012,6),m)
    sc.add(sphere((0,.1,.07),.2),m)
    eye=Mat(base=(.9,.7,1),metal=0,emis=lambda p:np.array([.8,.4,1.0])*1.6,ao=0)
    sc.add(sphere((0,.1,.2),.075),eye)
    rng=np.random.RandomState(8)
    for k in range(10):
        a=rng.rand()*2*PI; r=.55+rng.rand()*.1; x,y=.55*math.cos(a),.6*math.sin(a)+.05; pts=[(x,y,0)]
        for s in range(6): x+=math.cos(a)*.04+rng.randn()*.02; y+=math.sin(a)*.04+rng.randn()*.02; pts.append((x,y,0))
        for p0,p1 in zip(pts,pts[1:]): sc.add(capsule(p0,p1,.014,.006),m)
    return sc
# ---------- F: Steampunk snake rifle (faces right) ----------
def rifle():
    sc=Scene()
    wood=Mat(base=lambda p:np.array([.32,.15,.06])*(.7+.5*np.sin(p[:,0]*30+np.sin(p[:,1]*20)*3)[:,None]*.4+.3),metal=.2,gloss=70,envk=.4)
    brass=Mat(base=(.78,.5,.18),metal=.95,gloss=60,envk=.8)
    steel=Mat(base=(.5,.54,.6),metal=.95,gloss=90,envk=1.0)
    dkbrass=Mat(base=(.5,.33,.1),metal=.9,gloss=40)
    def scales(p): 
        s=.5+.5*np.sin(p[:,0]*140+np.sin(p[:,1]*60)*2)*np.sin(p[:,2]*140+p[:,0]*40)
        return np.array([.1,.65,.2])[None,:]*(.55+.8*s[:,None])+np.array([.1,.3,.1])*(np.sin(p[:,0]*28)>.7)[:,None]
    snake=Mat(base=scales,metal=.35,gloss=45,envk=.5)
    amber=Mat(base=(1,.8,.2),emis=lambda p:np.array([1,.7,.1])*1.4,ao=0)
    cream=Mat(base=(.95,.92,.8),metal=0,gloss=10)
    red=Mat(base=(.8,.1,.1),emis=lambda p:np.array([.6,.05,.05]),ao=0)
    glass=Mat(base=(.4,.9,1),emis=lambda p:np.array([.2,.7,1.0])*1.0,ao=0)
    sc.add(poly([(-.98,.14),(-.3,.13),(-.3,-.0),(-.46,-.12),(-.62,-.32),(-.92,-.28),(-.99,-.04)],.045,r=.025),wood)
    sc.add(poly([(-.99,.15),(-.94,.15),(-.95,-.3),(-.99,-.05)],.052,r=.006),brass)
    sc.add(rbox((-.14,.08,0),(.2,.11,.05),.03),brass)
    for i in range(2):
        R_=.075-.025*i; cc=C(-.2+.2*i,.09+.06*i*0,.06+.01*i)
        sc.add(cyl(cc,R_,.012,r=.004),dkbrass)
        for t in range(10): a=t*2*PI/10+i; sc.add(sphere(cc+C(math.cos(a)*(R_+.008),math.sin(a)*(R_+.008),0),.012),dkbrass)
        sc.add(sphere(cc+C(0,0,.01),.018),brass)
    # barrel and jacket
    sc.add(cyl((.55,.1,0),.034,.6,roty(PI/2)),steel)
    for x in (.12,.3,.5,.7,.9): sc.add(cyl((x,.1,0),.047,.016,roty(PI/2)),brass)
    sc.add(cyl((.3,-.0,0),.034,.3,roty(PI/2)),dkbrass); 
    for x in (.05,.55): sc.add(cyl((x,0,0),.04,.012,roty(PI/2)),brass)
    # gauge
    sc.add(cyl((.02,.27,0),.065,.025,r=.006),brass); sc.add(cyl((.02,.27,.026),.052,.006),cream)
    sc.add(capsule((.02,.27,.034),(.045,.295,.034),.005),red)
    for a in np.linspace(.3,2.8,7): sc.add(sphere((.02+.045*math.cos(a),.27+.045*math.sin(a),.033),.003),Mat(base=(.1,.1,.1)))
    # pipes
    pts=[(.0,.2,0),(-.02,.16,0),(-.1,.15,0)]; 
    for a,b in zip(pts,pts[1:]): sc.add(capsule(a,b,.014),brass)
    pts=[(.1,.0,0),(.0,-.05,0),(-.1,.0,0),(-.14,.0,0)]
    for a,b in zip(pts,pts[1:]): sc.add(capsule(a,b,.012),dkbrass)
    # scope
    sc.add(cyl((.3,.3,0),.034,.26,roty(PI/2)),Mat(base=(.15,.15,.18),metal=.7,gloss=60)); sc.add(cyl((.3,.3,0),.042,.02,roty(PI/2)),brass); sc.add(cyl((.52,.3,0),.044,.014,roty(PI/2)),brass); sc.add(sphere((.56,.3,0),.03),glass)
    for x in (.12,.4): sc.add(rbox((x,.23,0),(.014,.045,.014),.004),brass)
    # grip, guard (snake shaped)
    sc.add(poly([(-.3,.0),(-.15,.0),(-.2,-.2),(-.3,-.32),(-.38,-.27),(-.34,-.1)],.04,r=.02),wood)
    # snake body spiralling round barrel
    pts=[]
    for u in np.linspace(0,1,70):
        x=.1+.85*u; a=u*PI*7; r=.068; pts.append((x,.1+r*math.sin(a),r*math.cos(a)))
    for i,(a,b) in enumerate(zip(pts,pts[1:])):
        u=i/70; sc.add(capsule(a,b,.03*(1-.2*u)+.003,.03*(1-.2*(i+1)/70)+.003),snake)
    hx,hy,hz=pts[-1]
    sc.add(sphere((hx+.03,hy,hz),.05),snake); sc.add(sphere((hx+.085,hy-.004,hz),.034),snake)
    for s in (-1,1): sc.add(sphere((hx+.045,hy+.024,hz+s*.034),.012),amber)
    sc.add(capsule((hx+.095,hy-.005,hz),(hx+.15,hy-.008,hz),.003),red); sc.add(capsule((hx+.15,hy-.008,hz),(hx+.18,hy+.01,hz),.002),red); sc.add(capsule((hx+.15,hy-.008,hz),(hx+.18,hy-.025,hz),.002),red)
    # snake trigger guard
    gp=[(.04,-.02,.0),(.12,-.06,.0),(.1,-.16,0),(.0,-.2,0),(-.1,-.15,0),(-.14,-.05,0)]
    for a,b in zip(gp,gp[1:]): sc.add(capsule(a,b,.024,.022),snake)
    sc.add(capsule((-.02,-.0,0),(-.04,-.1,0),.01,.006),steel)
    return sc
# ---------- G: Grasping shield ----------
def gshield():
    sc=Scene()
    band=Mat(base=lambda p:np.array([.92,.86,.74])*(.8+.25*np.sin((p[:,0]+p[:,1])*70)[:,None]*.5+.1),metal=0,gloss=14)
    face=Mat(base=lambda p:np.array([.33,.12,.3])*(.7+.5*fbm(p*7))[:,None],metal=.5,gloss=40,envk=.5,rim=(.5,.1,.35))
    eye=Mat(base=(1,.4,.7),emis=lambda p:np.array([1,.3,.6])*1.6,ao=0)
    rose=Mat(base=(.75,.05,.15),metal=.1,gloss=25)
    leaf=Mat(base=(.1,.3,.12),metal=.1,gloss=15)
    thorn=Mat(base=(.1,.2,.1),metal=.2,gloss=20)
    sc.add(cyl((0,0,0),.6,.05,r=.03),face)
    sc.add(torus((0,0,0),.6,.06),band)
    for i in range(24):
        a=i*2*PI/24; sc.add(rbox((.6*math.cos(a),.6*math.sin(a),0),(.03,.03,.075),.01,rotz(a+.5)),band)
    sc.add(sphere((0,0,.02),.2),face); sc.add(torus((0,0,.1),.12,.016),band); sc.add(sphere((0,0,.1),.07),eye)
    for i in range(10):
        a=i*2*PI/10+.15; p=C(math.cos(a),math.sin(a),0); sc.add(cone(p*.66,.025,.12,rotz(a-PI/2)@roty(PI/2)*0+rotz(a)@roty(PI/2)),thorn)
    for a in (.5,2.1,3.7,5.3):
        p=C(math.cos(a),math.sin(a),0)*.57
        for k in range(5): sc.add(sphere(p+C(.02*math.cos(k*1.3),.02*math.sin(k*1.3),.03+.01*k),.04-.004*k),rose)
        sc.add(sphere(p+C(.0,.0,.05),.03),Mat(base=(.5,0,.1),gloss=25)); 
        for s in (-1,1): sc.add(sphere(p+C(s*.06,-.03*s,.01),.025),leaf)
    # grasping hands
    def arm(a0,sw,L):
        base=C(math.cos(a0),math.sin(a0),0)*.5; tang=C(-math.sin(a0),math.cos(a0),0)*sw
        pts=[base+C(math.cos(a0),math.sin(a0),0)*L*u+tang*math.sin(u*PI)*.25*L+C(0,0,.05*math.sin(u*PI)) for u in np.linspace(0,1,12)]
        for i,(a,b) in enumerate(zip(pts,pts[1:])): sc.add(capsule(a,b,.05*(1-.4*i/11),.05*(1-.4*(i+1)/11)),band)
        h=pts[-1]; d=(pts[-1]-pts[-2]); d/=np.linalg.norm(d); sc.add(sphere(h,.058),band)
        for f in range(4):
            off=(f-1.5)*.4; ang=a0+off; fd=C(math.cos(ang),math.sin(ang),.15)
            fp=h+fd*.1; fp2=h+fd*.16+C(0,0,.07); sc.add(capsule(h,fp,.017,.014),band); sc.add(capsule(fp,fp2,.014,.01),band)
    for a0,sw in ((PI/4+.3,1),(3*PI/4-.1,-1),(5*PI/4+.2,1),(7*PI/4-.2,-1)): arm(a0,sw,.38)
    return sc
JOBS={   # builder, angle, scale (smaller = bigger on screen), model-space centre, aura colour, aura strength
 'spear_of_sun':(spear,-PI/4,.8,(0,.02),(1.0,.8,.3),.35),
 'storm_katana':(katana,-PI/4,.8,(.03,.05),(.5,.7,1.0),.35),
 'bone_greatsword':(bone,-PI/4,1.0,(-.03,.05),(.6,1,.9),.2),
 'starry_revolver':(revolver,PI*.05,1.0,(.2,-.08),(.3,.5,1.0),.35),
 'shadow_sword':(shadow_sword,-PI/4,.78,(0,.17),(.55,.25,1.0),.45),
 'shadow_shield':(shadow_shield,0.0,1.0,(0,0),(.55,.25,1.0),.45),
 'snake_rifle':(rifle,PI*.07,1.22,(.1,.05),(.4,.9,.5),.15),
 'grasping_shield':(gshield,0.0,.95,(0,0),(1.0,.3,.6),.4),
}
if __name__=='__main__':
    import time
    import os; NN=int(os.environ.get('NN','256')); names=sys.argv[1:] or list(JOBS)
    for n in names:
        f,ang,scale,mc,ac,ast=JOBS[n]; t=time.time()
        img=render(f(),N=NN,ang=ang,scale=scale,mc=mc); img=bloom(img); img=aura(img,ac,ast,9); save(img,f'/tmp/r3/{n}.png'); print(n,round(time.time()-t,1),flush=True)
