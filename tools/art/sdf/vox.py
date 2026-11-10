import sys, json, math, os
sys.path.insert(0,'/tmp/r3')
import numpy as np
import m3, m2, m4, m5
from eng import rotz, norm
from PIL import Image

G=144; GZ=32; F=8
VMC=16.0/G            # Minecraft units per voxel
OUT='/tmp/vox'

CONF={ # name: (emissive kind, fire?)
 'starry_sniper':{'twinkle'},
 'shadow_sword':{'pulse'}, 'shadow_shield':{'pulse'},
 'spear_of_sun':{'pulse'}, 'storm_katana':{'flash'},
 'bone_greatsword':{'pulse'}, 'snake_rifle':{'pulse'}, "chained_bow":{"pulse"},"twilight_longsword":{"pulse"},
}
VS={'storm_katana':.78,'spear_of_sun':.92,'bone_greatsword':.9,'shadow_sword':.9,'chained_bow':.95,'starry_sniper':.8,'snake_rifle':.8}
LIFT={'starry_sniper':.06,'shadow_shield':.12,'shadow_sword':.1}
KIND={'starry_sniper':'twinkle','storm_katana':'flash'}

def voxelize(name):
    f,ang,vs,mc,unit,ac,ast=m2.JOBS[name]
    vs=VS.get(name,vs)
    sc=f(); Rz=rotz(ang)
    sh=Rz@np.array([mc[0]*unit,mc[1]*unit,0.])
    wv=2*vs/G
    ii,jj,kk=np.meshgrid(np.arange(G),np.arange(G),np.arange(GZ),indexing='ij')
    X=((ii+.5)/G*2-1)*vs+sh[0]; Y=((jj+.5)/G*2-1)*vs+sh[1]; Z=(kk+.5-GZ/2)*wv
    P=np.stack([X.ravel(),Y.ravel(),Z.ravel()],1)
    pm=(P@Rz)/unit
    vm=wv/unit
    d=np.full(len(P),1e9); idx=np.zeros(len(P),int)
    for i,(fn,mat) in enumerate(sc.objs):
        di=fn(pm); di=np.where(np.isfinite(di),di,1e9); m_=di<d; idx=np.where(m_,i,idx); d=np.minimum(d,di)
    solid=d<.5*vm
    # colour
    col=np.zeros((len(P),3)); em=np.zeros(len(P))
    for mi,(fn,mat) in enumerate(sc.objs):
        sel=np.where(solid&(idx==mi))[0]
        if len(sel)==0: continue
        pp=pm[sel]
        b=mat.base(pp) if callable(mat.base) else np.tile(np.array(mat.base,float),(len(sel),1))
        b=np.nan_to_num(b)
        c=np.clip(b*(1+.55*mat.metal)+.04+LIFT.get(name,0),0,1)
        if mat.emis is not None:
            e=mat.emis(pp) if callable(mat.emis) else np.array(mat.emis,float)
            e=np.nan_to_num(np.asarray(e,float)); 
            if e.ndim==1: e=np.tile(e,(len(sel),1))
            mag=e.max(1); em[sel]=mag
            c=np.clip(c+e*.6,0,1)
            hot=mag>.5
            c[hot]=np.clip(np.maximum(c[hot],e[hot]/np.maximum(e[hot].max(1,keepdims=True),1e-6)*.95),0,1)
        col[sel]=c
    shape=(G,G,GZ)
    S=solid.reshape(shape); IDX=idx.reshape(shape); COL=col.reshape(shape+(3,)); EM=em.reshape(shape)
    # world<->grid helpers
    def to_grid(model_pt):
        w=Rz@(np.array(model_pt,float)*unit)
        return np.array([((w[0]-sh[0])/vs+1)/2*G-.5,((w[1]-sh[1])/vs+1)/2*G-.5,w[2]/wv+GZ/2-.5])
    def vec_mc(model_vec):
        w=Rz@(np.array(model_vec,float)*unit); return w/wv*VMC
    return sc,S,IDX,COL,EM,to_grid,vec_mc,(Rz,unit)

def add_fire(S,IDX,COL,EM,blade_idx):
    """green flames streaming from the blade toward the tip (screen diagonal). returns dict of extra voxels {(i,j,k):(rgb,kind,tint)}"""
    rng=np.random.RandomState(5); extra={}
    pts=np.argwhere(S)
    for (i,j,k) in pts:
        if IDX[i,j,k]!=blade_idx: continue
        if rng.rand()>.07: continue
        L=rng.randint(3,8); dk=rng.randint(-1,2); wob=rng.rand()*6.28
        for t in range(1,L+1):
            ii=i+t+int(round(math.sin(wob+t*.8)*.6)); jj=j+t+int(round(math.cos(wob+t*.7)*.6)); kk=min(max(k+dk,0),GZ-1)
            if not(0<=ii<G and 0<=jj<G): break
            if S[ii,jj,kk]: continue
            tint=0 if t<=L//3 else (1 if t<=2*L//3 else 2)
            extra[(ii,jj,kk)]=(None,'flame',tint)
    return extra

def build(name):
    sc,S,IDX,COL,EM,to_grid,vec_mc,(Rz,unit)=voxelize(name)
    marks=getattr(sc,'marks',{}); pivots=getattr(sc,'pivots',{})
    part_of=np.full(IDX.shape,-1,int)       # -1 body, else part index
    pnames=list(marks.keys())
    for pi,pn in enumerate(pnames):
        a,b=marks[pn]; part_of[(IDX>=a)&(IDX<b)&S]=pi
    kinds=CONF[name]; ekind=KIND.get(name,'pulse')
    extra={}
    if name=='bone_greatsword':
        # blade object is the first big poly (index 0) built by bone2
        extra=add_fire(S,IDX,COL,EM,0)
    # entries
    entries={}   # key -> id
    ent_list=[]
    def entry(key):
        if key not in entries: entries[key]=len(ent_list); ent_list.append(key)
        return entries[key]
    solid_pts=np.argwhere(S)
    cols=np.array([COL[tuple(p)] for p in solid_pts]) if len(solid_pts) else np.zeros((0,3))
    for NQ in (88,72,60,48,40,32,24):
        entries={}; ent_list=[]
        def entry(key):
            if key not in entries: entries[key]=len(ent_list); ent_list.append(key)
            return entries[key]
        qimg=Image.fromarray((np.clip(cols,0,1)*255).astype('uint8').reshape(1,-1,3))
        pal=qimg.quantize(colors=NQ,method=Image.MEDIANCUT).convert('RGB')
        qcols=np.array(pal).reshape(-1,3)
        rng=np.random.RandomState(9)
        vox={}
        for n,p in enumerate(solid_pts):
            i,j,k=map(int,p); c=tuple(int(x) for x in qcols[n]); em=EM[i,j,k]
            if em>.5:
                ph=int(rng.randint(0,4)); key=(c,ekind,ph,0); emi=True
            else:
                key=(c,None,0,0); emi=False
            vox[(i,j,k)]=(entry(key),int(part_of[i,j,k]),emi)
        for (i,j,k),(c,kind,tint) in extra.items():
            ph=int(rng.randint(0,4)); key=((0,0,0),'flame',ph,tint); vox[(i,j,k)]=(entry(key),-1,True)
        if len(ent_list)<=256: break
    if len(ent_list)>256: raise SystemExit('too many palette entries '+str(len(ent_list)))
    # palette texture (16 x 16F)
    tex=np.zeros((16*F,16,4),np.uint8)
    FL=[[(.12,.9,.25),(.55,1.0,.55)],[(.2,.85,.3),(.85,1.0,.8)],[(.05,.45,.15),(.2,.8,.3)]]
    for eid,(c,kind,ph,tint) in enumerate(ent_list):
        x=eid%16; y=eid//16
        base=np.array(c)/255.
        for fr in range(F):
            t=(fr/F+ph/4.)%1.0; s=math.sin(2*math.pi*t)
            if kind is None: out=base
            elif kind=='twinkle': out=np.clip(base*(.22+1.0*max(0,s)**3)+np.array([.1,.12,.2])*max(0,s),0,1)
            elif kind=='pulse': out=np.clip(base*(.72+.4*s),0,1)
            elif kind=='flash': out=np.clip(base*(1.25 if (fr+2*ph)%F in (0,1) else .35),0,1)
            else:
                a,b=FL[tint]; w=.5+.5*s; out=np.array(a)*(1-w)+np.array(b)*w
            tex[fr*16+y,x]=list((np.clip(out,0,1)*255).astype(int))+[255]
    Image.fromarray(tex,'RGBA').save(f'{OUT}/{name}_vox.png')
    json.dump({"animation":{"frametime":3,"interpolate":True}},open(f'{OUT}/{name}_vox.png.mcmeta','w'))
    # greedy quads per part
    def quads_for(part):
        solid_p=np.zeros((G,G,GZ),bool); eid=np.zeros((G,G,GZ),int); emis=np.zeros((G,G,GZ),bool)
        for (i,j,k),(e,pt,emi) in vox.items():
            if pt==part: solid_p[i,j,k]=True; eid[i,j,k]=e; emis[i,j,k]=emi
        out=[]
        dirs=[('east',0,1),('west',0,-1),('up',1,1),('down',1,-1),('south',2,1),('north',2,-1)]
        for dname,ax,sg in dirs:
            n=[G,G,GZ][ax]
            for s in range(n):
                # mask of exposed faces at slice s
                idxs=[slice(None)]*3; idxs[ax]=s
                cur=solid_p[tuple(idxs)]
                nb_i=s+sg
                if 0<=nb_i<n:
                    idxs2=[slice(None)]*3; idxs2[ax]=nb_i; nb=solid_p[tuple(idxs2)]
                else: nb=np.zeros_like(cur)
                face=cur&~nb
                if not face.any(): continue
                ids=np.where(face,eid[tuple(idxs)]*2+emis[tuple(idxs)],-1)
                done=np.zeros_like(face)
                A,B=ids.shape
                for a in range(A):
                    for b in range(B):
                        if done[a,b] or ids[a,b]<0: continue
                        v=ids[a,b]; w=1
                        while b+w<B and not done[a,b+w] and ids[a,b+w]==v: w+=1
                        h=1; ok=True
                        while a+h<A and ok:
                            for bb in range(b,b+w):
                                if done[a+h,bb] or ids[a+h,bb]!=v: ok=False;break
                            if ok: h+=1
                        done[a:a+h,b:b+w]=True
                        out.append((dname,ax,s,sg,a,b,h,w,int(v//2),bool(v%2)))
        return out
    def quad_elements(q, ref):
        res=[]
        for (dname,ax,s,sg,a,b,h,w,e,emi) in q:
            ranges=[None,None,None]; others=[x for x in range(3) if x!=ax]
            lo=[0,0,0]; hi=[0,0,0]
            lo[ax]=hi[ax]=(s+(1 if sg>0 else 0))
            lo[others[0]]=a; hi[others[0]]=a+h; lo[others[1]]=b; hi[others[1]]=b+w
            def mc(v,axn): return round(v*VMC+(8-GZ*VMC/2 if axn==2 else 0),4)
            frm=[mc(lo[0],0),mc(lo[1],1),mc(lo[2],2)]; to=[mc(hi[0],0),mc(hi[1],1),mc(hi[2],2)]
            x=e%16; y=e//16
            face={"uv":[x+.15,y+.15,x+.85,y+.85],"texture":"#0"}
            if emi: face["neoforge_data"]={"block_light":15,"sky_light":15}
            res.append({"from":frm,"to":to,"faces":{dname:face}})
        return res
    disp={
     "thirdperson_righthand":{"rotation":[0,-90,55],"translation":[0,4,.5],"scale":[.85,.85,.85]},
     "thirdperson_lefthand":{"rotation":[0,90,-55],"translation":[0,4,.5],"scale":[.85,.85,.85]},
     "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[.68,.68,.68]},
     "firstperson_lefthand":{"rotation":[0,90,-25],"translation":[1.13,3.2,1.13],"scale":[.68,.68,.68]},
    }
    info={'parts':[]}
    for part in [-1]+list(range(len(pnames))):
        q=quads_for(part); els=quad_elements(q,None)
        fn=f'{name}_3d' if part<0 else f'{name}_part_{pnames[part]}'
        model={"textures":{"0":f"astral_world:item/{name}_vox","particle":f"astral_world:item/{name}_vox"},"elements":els}
        json.dump(model,open(f'{OUT}/{fn}.json','w'),separators=(',',':'))
        print(name,fn,len(els),'quads')
    # moving part descriptors in baked (0..1) coordinates
    for pi,pn in enumerate(pnames):
        pv=pivots.get(pn)
        d={'name':pn}
        if pn in ('wheel','sun'):
            g=to_grid(pv); piv=[g[0]*VMC/16,g[1]*VMC/16,(8+(g[2]+.5-GZ/2)*VMC)/16]
            d.update(type='spin',pivot=[round(x,5) for x in piv],axis=[0,0,1],speed=1.2 if pn=='wheel' else -0.9)
        elif pn=='bolt':
            v=vec_mc((-5.5,0,0)); d.update(type='slide',vec=[round(x/16,5) for x in v])
        elif pn=='string':
            v=vec_mc((-9.0,0,0)); d.update(type='pull',vec=[round(x/16,5) for x in v])
        info['parts'].append(d)
    json.dump(info,open(f'{OUT}/{name}_parts.json','w'))
    return info

if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    for n in sys.argv[1:]: build(n)
