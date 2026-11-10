import sys, json, math, os, struct, time
sys.path.insert(0,'/tmp/r3')
import numpy as np
import m3, m2, m4, m5
from eng import rotz
from skimage import measure
from scipy import ndimage
import vox
G=240; GZ=54
OUT='/tmp/mesh'
KCODE={None:0,'twinkle':1,'pulse':2,'flash':3,'flame':4}

def eval_group(sc,sel,pm,chunk=200000):
    d=np.full(len(pm),1e9); idx=np.full(len(pm),-1,int)
    for s in range(0,len(pm),chunk):
        p=pm[s:s+chunk]; dd=np.full(len(p),1e9); ii=np.full(len(p),-1,int)
        for i in sel:
            fn=sc.objs[i][0]; di=fn(p); di=np.where(np.isfinite(di),di,1e9); m_=di<dd; ii=np.where(m_,i,ii); dd=np.minimum(dd,di)
        d[s:s+chunk]=dd; idx[s:s+chunk]=ii
    return d,idx

def colour(sc,idx,pm,name):
    col=np.zeros((len(pm),3)); em=np.zeros(len(pm))
    for mi in np.unique(idx):
        if mi<0: continue
        sel=np.where(idx==mi)[0]; mat=sc.objs[mi][1]; pp=pm[sel]
        b=mat.base(pp) if callable(mat.base) else np.tile(np.array(mat.base,float),(len(sel),1))
        b=np.nan_to_num(b)
        c=np.clip(b*(1+.55*mat.metal)+.04+vox.LIFT.get(name,0),0,1)
        if mat.emis is not None:
            e=mat.emis(pp) if callable(mat.emis) else np.array(mat.emis,float)
            e=np.nan_to_num(np.asarray(e,float))
            if e.ndim==1: e=np.tile(e,(len(sel),1))
            mag=e.max(1); em[sel]=mag; c=np.clip(c+e*.6,0,1)
            hot=mag>.5
            c[hot]=np.clip(np.maximum(c[hot],e[hot]/np.maximum(e[hot].max(1,keepdims=True),1e-6)*.95),0,1)
        col[sel]=c
    return col,em


from eng import sky
def bake(sc,sel,idx,pmv,Pw,nrm,Rz,unit,name):
    def D(q):
        d,_=eval_group(sc,sel,(q@Rz)/unit); return d*unit
    n=nrm; Lw=np.array([-.5,.7,.6]); Lw/=np.linalg.norm(Lw); view=np.array([0,0,1.0])
    shd=np.ones(len(Pw)); tt=np.full(len(Pw),.02); q0=Pw+n*.01
    for _ in range(18):
        dd=D(q0+Lw*tt[:,None]); shd=np.minimum(shd,np.clip(9*dd/tt,0,1)); tt=tt+np.clip(dd,.012,.22)
    shd=.35+.65*shd
    out=np.zeros((len(Pw),3)); emv=np.zeros(len(Pw))
    for mi in np.unique(idx):
        if mi<0: continue
        sel_=np.where(idx==mi)[0]; m=sc.objs[mi][1]; pp=pmv[sel_]; nn=n[sel_]
        if getattr(m,'bump',None) is not None:
            e2=.02; bf=m.bump
            g=np.stack([(bf(pp+[e2,0,0])-bf(pp-[e2,0,0]))/(2*e2),(bf(pp+[0,e2,0])-bf(pp-[0,e2,0]))/(2*e2),(bf(pp+[0,0,e2])-bf(pp-[0,0,e2]))/(2*e2)],1)
            gw=(g@Rz.T)/unit; gw=gw-(gw*nn).sum(1,keepdims=True)*nn; nn=nn-m.bk*gw; nn=nn/np.maximum(np.linalg.norm(nn,axis=1,keepdims=True),1e-9)
        base=m.base(pp) if callable(m.base) else np.tile(np.array(m.base,float),(len(sel_),1))
        base=np.nan_to_num(base)+vox.LIFT.get(name,0)*.5
        diff=(np.clip(nn@Lw,0,1)*shd[sel_])[:,None]; fill=np.clip(nn@np.array([.7,-.2,.3]),0,1)[:,None]
        Hh=Lw+view; Hh/=np.linalg.norm(Hh); sp=np.clip(nn@Hh,0,1)**m.gloss*(1.2*m.spec)
        ndv=np.clip(nn@view,0,1)[:,None]; fres=(1-ndv)**3
        Rf=2*(nn@view)[:,None]*nn-view; env=sky(Rf)
        amb=np.array([.18,.18,.24])
        lit=base*(amb+diff*np.array([1.0,.96,.88])*.9+fill*np.array([.25,.28,.4])*.6)
        mt=m.metal; spc=(sp[:,None]*np.array([1,.97,.9]))
        c=lit*(1-mt*.75)+(env*base*mt*m.envk*1.6)+spc*(.35+.65*mt)*(base*mt+(1-mt))+env*fres*.18*(1-mt)
        if m.rim is not None: c=c+np.array(m.rim)*fres*1.4
        if m.emis is not None:
            em=m.emis(pp) if callable(m.emis) else np.array(m.emis,float)
            em=np.nan_to_num(np.asarray(em,float)); 
            if em.ndim==1: em=np.tile(em,(len(sel_),1))
            c=c+em; emv[sel_]=em.max(1)
        if m.ao:
            a1=D(Pw[sel_]+nn*.04); a2=D(Pw[sel_]+nn*.1); ao=(.5+.5*np.clip((a1+a2/2)/.1,0,1))[:,None]; c=c*ao
        out[sel_]=c
    out=1-np.exp(-np.clip(out,0,None)*1.25); out=out**(1/1.1)
    return out,emv

def mesh_group(name,sc,sel,Rz,unit,sh,vs,wv,ekind,level):
    ii,jj,kk=np.meshgrid(np.arange(G),np.arange(G),np.arange(GZ),indexing='ij')
    X=((ii+.5)/G*2-1)*vs+sh[0]; Y=((jj+.5)/G*2-1)*vs+sh[1]; Z=(kk+.5-GZ/2)*wv
    P=np.stack([X.ravel(),Y.ravel(),Z.ravel()],1); pm=(P@Rz)/unit
    d,_=eval_group(sc,sel,pm)
    D=(d*1.0).reshape(G,G,GZ)/unit   # model units
    vm=wv/unit
    D=np.pad(D,1,constant_values=1e3)
    v,f,_,_=measure.marching_cubes(D,level=level*vm,spacing=(1,1,1))
    v=v-1
    # smooth normals from field gradient
    gx,gy,gz=np.gradient(ndimage.gaussian_filter(D,.6))
    gv=np.stack([ndimage.map_coordinates(g,(v+1).T,order=1) for g in (gx,gy,gz)],1)
    nrm=gv/np.maximum(np.linalg.norm(gv,axis=1,keepdims=True),1e-9)
    X=((v[:,0]+.5)/G*2-1)*vs+sh[0]; Y=((v[:,1]+.5)/G*2-1)*vs+sh[1]; Zc=(v[:,2]+.5-GZ/2)*wv
    pmv=(np.stack([X,Y,Zc],1)@Rz)/unit
    _,idx=eval_group(sc,sel,pmv)
    col0,em0=colour(sc,idx,pmv,name)
    Pw=np.stack([X,Y,Zc],1)
    col,em=bake(sc,sel,idx,pmv,Pw,nrm,Rz,unit,name)
    em=em0
    mc=np.stack([(v[:,0]+.5)*16/G,(v[:,1]+.5)*16/G,8+(v[:,2]+.5-GZ/2)*16/G],1)
    return mc,nrm,col,em,f,pmv,idx

def flames(mc,idx,pmv,blade_idx,rng,Rz,unit,sh,vs):
    """green flame tongues rooted on the blade surface streaming toward the tip (+y in model space)."""
    sel=np.where(idx==blade_idx)[0]
    if len(sel)==0: return None
    pick=rng.choice(sel,size=min(260,len(sel)),replace=False)
    V=[];N=[];C=[];F=[];K=[]
    def to_mc(pt):
        w=Rz@(np.asarray(pt)*unit); return np.array([((w[0]-sh[0])/vs+1)*8,((w[1]-sh[1])/vs+1)*8,8+8*w[2]/vs])
    for n,i in enumerate(pick):
        p=pmv[i]; L=rng.uniform(2.5,7.0); wd=rng.uniform(.45,.9); ph=rng.randint(0,255); tint=rng.randint(0,3)
        lean=np.array([rng.randn()*.25,1,rng.randn()*.15]); lean/=np.linalg.norm(lean)
        tip=p+lean*L; base=[p+np.array([wd*math.cos(a),0,wd*math.sin(a)]) for a in (0,2.094,4.188)]
        mid=p+lean*L*.45+np.array([rng.randn()*.2,0,rng.randn()*.2])
        pts=[to_mc(b) for b in base]+[to_mc(mid),to_mc(tip)]
        # two stacked pyramids: base ring -> mid -> tip (as triangles)
        base_i=len(V)
        for q in pts: V.append(q); N.append(np.array([0,0,1.])); C.append([.1,.8,.25]); K.append((4,ph,tint))
        for a in range(3):
            b=(a+1)%3; F.append((base_i+a,base_i+b,base_i+3)); F.append((base_i+3,base_i+a,base_i+4)) if False else None
            F.append((base_i+a,base_i+b,base_i+3)); F.append((base_i+a,base_i+3,base_i+4))
    return np.array(V),np.array(C),np.array(F,int),K

def build(name):
    t0=time.time()
    f_,ang,vs,mc_,unit,ac,ast=m2.JOBS[name]; vs=vox.VS.get(name,vs)
    sc=f_(); Rz=rotz(ang); sh=Rz@np.array([mc_[0]*unit,mc_[1]*unit,0.]); wv=2*vs/G
    marks=getattr(sc,'marks',{}); pivots=getattr(sc,'pivots',{}); pn=list(marks.keys())
    n_obj=len(sc.objs); part_sets=[list(range(*marks[p])) for p in pn]
    body=[i for i in range(n_obj) if not any(i in s for s in part_sets)]
    ekind=vox.KIND.get(name,'pulse'); rng=np.random.RandomState(7)
    groups=[body]+part_sets
    out=[]
    for gi,sel in enumerate(groups):
        mc,nrm,col,em,f,pmv,idx=mesh_group(name,sc,sel,Rz,unit,sh,vs,wv,ekind,.12)
        kind=np.where(em>.5,KCODE[ekind],0); phase=(np.floor(pmv*2.2).astype(int)@np.array([73,151,31])%256) if ekind=='twinkle' else (((pmv[:,1]/55.0+pmv[:,0]/200.0)*256).astype(int)%256)
        verts=dict(pos=mc/16.0,nrm=nrm,col=col,kind=kind,phase=phase,tint=np.zeros(len(mc),int),f=f)
        if gi==0 and name=='bone_greatsword':
            fl=flames(mc,idx,pmv,0,rng,Rz,unit,sh,vs)
            if fl is not None:
                V,C,F,K=fl; n0=len(verts['pos'])
                verts['pos']=np.concatenate([verts['pos'],V/16.0]); verts['nrm']=np.concatenate([verts['nrm'],np.tile([0,1.,0],(len(V),1))])
                verts['col']=np.concatenate([verts['col'],C]); verts['kind']=np.concatenate([verts['kind'],[k[0] for k in K]])
                verts['phase']=np.concatenate([verts['phase'],[k[1] for k in K]]); verts['tint']=np.concatenate([verts['tint'],[k[2] for k in K]])
                verts['f']=np.concatenate([f,F+n0])
        out.append(verts)
    os.makedirs(OUT,exist_ok=True)
    with open(f'{OUT}/{name}.bin','wb') as fh:
        fh.write(struct.pack('>i',len(out)))
        for v in out:
            n=len(v['pos']); m=len(v['f']); fh.write(struct.pack('>ii',n,m))
            for i in range(n):
                fh.write(struct.pack('>6f',*v['pos'][i],*v['nrm'][i]))
                c=np.clip(v['col'][i]*255,0,255).astype(int)
                fh.write(struct.pack('>5B',int(c[0]),int(c[1]),int(c[2]),int(v['kind'][i]),int(v['phase'][i])))
                fh.write(struct.pack('>B',int(v['tint'][i])))
            fh.write(np.asarray(v['f'],'>i4').tobytes())
    # moving part descriptors (blocks)
    def mpt(pt):
        w=Rz@(np.array(pt,float)*unit); return [((w[0]-sh[0])/vs+1)*8/16,((w[1]-sh[1])/vs+1)*8/16,(8+8*w[2]/vs)/16]
    def mvec(v):
        w=Rz@(np.array(v,float)*unit); return list(w*8/vs/16)
    info={'parts':[]}
    for p in pn:
        d={'name':p}
        if p in('wheel','sun'): d.update(type='spin',pivot=[round(x,5) for x in mpt(pivots[p])],speed=1.2 if p=='wheel' else -.9)
        elif p=='bolt': d.update(type='slide',vec=[round(x,5) for x in mvec((-5.5,0,0))])
        elif p=='string': d.update(type='pull',vec=[round(x,5) for x in mvec((-9.0,0,0))])
        info['parts'].append(d)
    # extent along the item's long axis for scaling
    import hold
    g,dv,uv,kind=hold.HOLD[name]; ax=Rz@np.array(dv,float); ax/=np.linalg.norm(ax)
    P=out[0]['pos']*16; proj=(P-8)@ax; info['len']=float(proj.max()-proj.min())
    json.dump(info,open(f'{OUT}/{name}.json','w'))
    print(name,[len(v['f']) for v in out],'tris',round(time.time()-t0),'s',info['len'],flush=True)
if __name__=='__main__':
    for n in sys.argv[1:]: build(n)
