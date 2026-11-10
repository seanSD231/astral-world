import numpy as np, math
V=lambda *a: np.array(a,float)
def norm(v): return np.sqrt((v*v).sum(-1))
def rotz(a): c,s=math.cos(a),math.sin(a); return np.array([[c,-s,0],[s,c,0],[0,0,1]])
def rotx(a): c,s=math.cos(a),math.sin(a); return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def roty(a): c,s=math.cos(a),math.sin(a); return np.array([[c,0,s],[0,1,0],[-s,0,c]])
I3=np.eye(3)
def loc(c,R):   # world p -> local
    c=np.asarray(c,float); Ri=R.T
    return lambda p: (p-c)@R   # (p-c) @ R == R^T (p-c)
def sphere(c,r):
    c=np.asarray(c,float); return lambda p: norm(p-c)-r
def capsule(a,b,r0,r1=None):
    a=np.asarray(a,float); b=np.asarray(b,float); r1=r0 if r1 is None else r1; ba=b-a; bb=ba@ba
    def f(p):
        pa=p-a; h=np.clip(pa@ba/bb,0,1); return norm(pa-h[:,None]*ba)-(r0+(r1-r0)*h)
    return f
def rbox(c,h,r=0.0,R=I3):
    T=loc(c,R); h=np.asarray(h,float)
    def f(p):
        q=np.abs(T(p))-h+r; return norm(np.maximum(q,0))+np.minimum(q.max(1),0)-r
    return f
def cyl(c,rad,hh,R=I3,r=0.0):  # axis = local z
    T=loc(c,R)
    def f(p):
        q=T(p); d=np.stack([np.sqrt(q[:,0]**2+q[:,1]**2)-rad+r,np.abs(q[:,2])-hh+r],1)
        return np.minimum(d.max(1),0)+norm(np.maximum(d,0))-r
    return f
def torus(c,R_,r,R=I3):  # in local xy plane
    T=loc(c,R)
    def f(p):
        q=T(p); a=np.sqrt(q[:,0]**2+q[:,1]**2)-R_; return np.sqrt(a*a+q[:,2]**2)-r
    return f
def cone(c,rad,h,R=I3):  # apex at +z*h/2? tip along +z, base at -h/2 (local)
    T=loc(c,R); 
    def f(p):
        q=T(p); z=q[:,2]+h/2; t=np.clip(z/h,0,1); rr=rad*(1-t)
        d1=np.sqrt(q[:,0]**2+q[:,1]**2)-rr; d2=np.maximum(-z,z-h)
        return np.maximum(d1*0.9,d2)
    return f
def polysdf(px,py,Vs):
    n=len(Vs); d=(px-Vs[0][0])**2+(py-Vs[0][1])**2; s=np.ones_like(px); j=n-1
    for i in range(n):
        ex,ey=Vs[j][0]-Vs[i][0],Vs[j][1]-Vs[i][1]; wx,wy=px-Vs[i][0],py-Vs[i][1]
        t=np.clip((wx*ex+wy*ey)/max(ex*ex+ey*ey,1e-12),0,1); bx,by=wx-ex*t,wy-ey*t; d=np.minimum(d,bx*bx+by*by)
        c1=py>=Vs[i][1]; c2=py<Vs[j][1]; c3=ex*wy>ey*wx
        flip=(c1&c2&c3)|((~c1)&(~c2)&(~c3)); s=np.where(flip,-s,s); j=i
    return s*np.sqrt(d)
def poly(Vs,th,c=(0,0,0),R=I3,r=0.0):   # extruded polygon in local xy, half thickness th
    T=loc(c,R)
    def f(p):
        q=T(p); d2=polysdf(q[:,0],q[:,1],Vs)+r; dz=np.abs(q[:,2])-th+r
        w=np.stack([d2,dz],1); return np.minimum(w.max(1),0)+norm(np.maximum(w,0))-r
    return f
def smin(a,b,k): h=np.clip(.5+.5*(b-a)/k,0,1); return b*(1-h)+a*h-k*h*(1-h)
def hash3(p):
    q=np.sin(p@np.array([127.1,311.7,74.7]))*43758.5453; return q-np.floor(q)
def vnoise(p):
    i=np.floor(p); f=p-i; f=f*f*(3-2*f); out=0
    for dx in (0,1):
        for dy in (0,1):
            for dz in (0,1):
                w=(f[:,0] if dx else 1-f[:,0])*(f[:,1] if dy else 1-f[:,1])*(f[:,2] if dz else 1-f[:,2])
                out=out+w*hash3(i+np.array([dx,dy,dz]))
    return out
def fbm(p,o=4): 
    s=0;a=.5
    for k in range(o): s=s+a*vnoise(p); p=p*2.03; a*=.5
    return s
class Mat:
    def __init__(s,base=(.5,.5,.5),metal=0.,gloss=40.,emis=None,rim=None,spec=1.,envk=.5,ao=1.,bump=None,bk=.4): s.__dict__.update(locals())
class Scene:
    def __init__(s): s.objs=[]
    def add(s,fn,mat,k=None): s.objs.append((fn,mat)); return fn
    def eval(s,p):
        best=None; idx=np.zeros(len(p),int)
        for i,(f,m) in enumerate(s.objs):
            d=f(p)
            if best is None: best=d
            else:
                m_=d<best; idx=np.where(m_,i,idx); best=np.minimum(best,d)
        return best,idx
def sky(R,stars=False):
    y=R[:,1]; t=np.clip(y*.5+.5,0,1)[:,None]
    c=(1-t)*np.array([.10,.08,.07])+t*np.array([.45,.62,1.0])
    c=c+np.exp(-((y-.12)/.22)**2)[:,None]*np.array([1.0,.92,.8])*.9
    for L,k,w in (((-.5,.7,.6),10,np.array([2.6,2.5,2.3])),((.8,.2,.5),14,np.array([1.2,1.4,2.0])),((.1,-.3,.9),12,np.array([1.0,.8,.6]))):
        L=np.array(L)/np.linalg.norm(L); c=c+np.exp(k*((R@L)-1))[:,None]*w
    return c
def render(scene,N=256,ang=math.radians(-45),scale=1.0,mc=(0,0),unit=1.0,light=(-.5,.7,.6),bgglow=None,envfn=None,seed=0):
    Rz=rotz(ang); xs=(np.arange(N)+.5)/N*2-1; ys=1-(np.arange(N)+.5)/N*2
    sh=rotz(ang)@np.array([mc[0]*unit,mc[1]*unit,0.]); X,Y=np.meshgrid(xs*scale+sh[0],ys*scale+sh[1]); P0=np.stack([X.ravel(),Y.ravel(),np.full(X.size,3.0)],1)
    pix=2*scale/N
    P=P0.copy(); mind=np.full(len(P),1e9); hit=np.zeros(len(P),bool); active=np.arange(len(P)); t=np.zeros(len(P))
    def S(p):
        d,i=scene.eval((p@Rz)/unit); return d*unit,i
    for it in range(110):
        if len(active)==0: break
        p=P0[active]+np.array([0,0,-1.0])*t[active][:,None]
        d,_=S(p); mind[active]=np.minimum(mind[active],d)
        h=d<0.0025; hit[active[h]]=True
        t[active]+=np.minimum(d,0.35)*.9
        keep=(~h)&(t[active]<6.0); active=active[keep]
    pos=P0+np.array([0,0,-1.0])*t[:,None]
    hi=np.where(hit)[0]
    col=np.zeros((len(P),3)); alpha=np.clip(.5-(mind-0.0025)/pix,0,1); alpha[hit]=1
    if len(hi):
        p=pos[hi]; e=.003
        def D(q): return S(q)[0]
        nx=D(p+[e,0,0])-D(p-[e,0,0]); ny=D(p+[0,e,0])-D(p-[0,e,0]); nz=D(p+[0,0,e])-D(p-[0,0,e])
        n=np.stack([nx,ny,nz],1); n/=np.maximum(norm(n)[:,None],1e-9)
        _,idx=S(p); pm=(p@Rz)/unit                      # model-space position for textures
        nm=n@Rz
        Lw=np.array(light); Lw/=np.linalg.norm(Lw); view=np.array([0,0,1.0])
        shd=np.ones(len(hi)); tt=np.full(len(hi),.02); q0=p+n*.01
        for _ in range(22):
            dd=D(q0+Lw*tt[:,None]); shd=np.minimum(shd,np.clip(9*dd/tt,0,1)); tt=tt+np.clip(dd,.012,.22)
        shd=.28+.72*shd
        out=np.zeros((len(hi),3))
        for mi,(f,m) in enumerate(scene.objs):
            sel=np.where(idx==mi)[0]
            if len(sel)==0: continue
            pp=pm[sel]; nn=n[sel]
            if getattr(m,'bump',None) is not None:
                e2=.02; bf=m.bump; gx=(bf(pp+[e2,0,0])-bf(pp-[e2,0,0]))/(2*e2); gy=(bf(pp+[0,e2,0])-bf(pp-[0,e2,0]))/(2*e2); gz=(bf(pp+[0,0,e2])-bf(pp-[0,0,e2]))/(2*e2)
                gw=(np.stack([gx,gy,gz],1)@Rz.T)/unit; gw=gw-(gw*nn).sum(1,keepdims=True)*nn; nn=nn-m.bk*gw; nn/=np.maximum(norm(nn)[:,None],1e-9)
            base=m.base(pp) if callable(m.base) else np.tile(np.array(m.base,float),(len(sel),1))
            diff=(np.clip(nn@Lw,0,1)*shd[sel])[:,None]; fill=np.clip(nn@np.array([.7,-.2,.3])/1.0,0,1)[:,None]
            Hh=Lw+view; Hh/=np.linalg.norm(Hh); sp=np.clip(nn@Hh,0,1)**m.gloss*(1.2*m.spec)
            ndv=np.clip(nn@view,0,1)[:,None]; fres=(1-ndv)**3
            Rf=2*(nn@view)[:,None]*nn-view
            env=envfn(Rf,pp) if envfn else sky(Rf)
            amb=np.array([.18,.18,.24])
            lit=base*(amb+diff*np.array([1.0,.96,.88])*.9+fill*np.array([.25,.28,.4])*.6)
            metal=m.metal
            spc=(sp[:,None]*np.array([1,.97,.9]))
            c=lit*(1-metal*.75)+ (env*base*metal*m.envk*1.6) + spc*(.35+.65*metal)*(base*metal+(1-metal))+ env*fres*.18*(1-metal)
            if m.rim is not None: c=c+np.array(m.rim)*fres*1.4
            if m.emis is not None:
                em=m.emis(pp) if callable(m.emis) else np.array(m.emis,float)
                c=c+em
            # cheap AO
            if m.ao:
                a1=D(p[sel]+nn*.04); a2=D(p[sel]+nn*.1); ao=(.5+.5*np.clip((a1+a2/2)/.1,0,1))[:,None]; c=c*ao
            out[sel]=c
        col[hi]=out
    col=np.clip(col,0,None)
    img=np.zeros((N,N,4)); 
    c3=col.reshape(N,N,3); a=alpha.reshape(N,N)
    # tone map
    c3=1-np.exp(-c3*1.25); c3=c3**(1/1.1)
    img[...,:3]=c3; img[...,3]=a
    return img
def save(img,path):
    from PIL import Image
    Image.fromarray((np.clip(img,0,1)*255).astype('uint8'),'RGBA').save(path)
