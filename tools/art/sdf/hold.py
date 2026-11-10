import sys, math, json, numpy as np
sys.path.insert(0,'/tmp/r3')
import m3, m2
from eng import rotz
import vox

def unit(v): v=np.array(v,float); return v/np.linalg.norm(v)
def basis(a,b):
    a=unit(a); b=np.array(b,float); b=unit(b-a*(a@b)); return np.stack([a,b,np.cross(a,b)],1)   # columns
def rot_from(a,b,A,B):
    """rotation matrix M with M a = A, M b = B (orthonormalised)"""
    return basis(A,B)@basis(a,b).T
def Rx(t): c,s=math.cos(t),math.sin(t); return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def Ry(t): c,s=math.cos(t),math.sin(t); return np.array([[c,0,s],[0,1,0],[-s,0,c]])
def Rz_(t): c,s=math.cos(t),math.sin(t); return np.array([[c,-s,0],[s,c,0],[0,0,1]])
def compose(r): a,b,c=[math.radians(x) for x in r]; return Rx(a)@Ry(b)@Rz_(c)
def euler_xyz(M):
    b=math.asin(max(-1,min(1,M[0][2])))
    if abs(M[0][2])>0.9999:
        c=0.0; a=math.atan2(M[1][0]*(1 if M[0][2]>0 else -1)*0+M[2][1],M[1][1]) if False else None
        best=None
        for aa in range(-180,181):
            e=np.abs(Rx(math.radians(aa))@Ry(b)-M).sum()
            if best is None or e<best[0]: best=(e,aa)
        a=math.radians(best[1])
    else:
        a=math.atan2(-M[1][2],M[2][2]); c=math.atan2(-M[0][1],M[0][0])
    return [math.degrees(a),math.degrees(b),math.degrees(c)]

# name: (grip point in scene coords, long axis, 'up' axis, kind)
HOLD={
 'starry_sniper':((-3.0,-2.2,0),(1,0,0),(0,1,0),'gun'),
 'snake_rifle':((-2.9,-2.8,0),(1,0,0),(0,1,0),'gun'),
 'shadow_sword':((0,-7,0),(0,1,0),(-1,0,0),'blade'),
 'storm_katana':((0,-6,0),(0,1,0),(-1,0,0),'blade'),
 'bone_greatsword':((0,-15,0),(0,1,0),(-1,0,0),'blade'),
 'spear_of_sun':((0,-12,0),(0,1,0),(1,0,0),'spear'),
 'chained_bow':((0,0,0),(0,1,0),(1,0,0),'bow'),
 'shadow_shield':((0,-.5,0),(0,1,0),(0,0,1),'shield'),
 'twilight_longsword':((0,-4,0),(0,1,0),(-1,0,0),'blade'),
}
SC={'starry_sniper':1.9,'shadow_sword':1.5,'shadow_shield':1.0,'spear_of_sun':1.7,'storm_katana':1.5,'bone_greatsword':1.5,'snake_rifle':1.9,'chained_bow':1.7,'twilight_longsword':1.5}
FS={'gun':.7,'blade':.62,'spear':.7,'bow':.8,'shield':.75}
# third person: arm frame (x=player's right, y=forward when arm hangs, z=toward the shoulder). Guns and bow are held with the arm RAISED
# (crossbow / bow pose): there the arm's -z points forward and +y points up.
TP={'starry_sniper':2.0,'snake_rifle':2.0,'shadow_sword':2.0,'storm_katana':2.0,'bone_greatsword':2.4,'spear_of_sun':3.0,'chained_bow':1.8,'shadow_shield':1.2,'twilight_longsword':1.6}
FP={'starry_sniper':.72,'snake_rifle':.72,'shadow_sword':.72,'storm_katana':.72,'bone_greatsword':.95,'spear_of_sun':1.2,'chained_bow':.75,'shadow_shield':.5,'twilight_longsword':.8}
def want(kind,person):
    if person==3:
        return {'gun':((0,0,-1),(0,1,0)),'blade':((0,.82,.57),(0,-.57,.82)),'spear':((0,.93,.37),(1,0,0)),
                'bow':((0,1,0),(0,0,-1)),'shield':((0,0,1),(0,1,0))}[kind]
    return {'gun':((-.05,.12,-.99),(0,.99,.12)),'blade':((-.1,.78,-.62),(-.05,.62,.78)),'spear':((.1,.45,-.89),(0,.89,.45)),
            'bow':((0,1,0),(0,0,-1)),'shield':((0,1,0),(0,0,-1))}[kind]

def hold(n):
    f,ang,vs,mc,un,ac,ast=m2.JOBS[n]; vs=vox.VS.get(n,vs)
    Rz=rotz(ang); sh=Rz@np.array([mc[0]*un,mc[1]*un,0.])
    g,d,u,kind=HOLD[n]
    w=Rz@(np.array(g,float)*un)
    gmc=np.array([((w[0]-sh[0])/vs+1)*8,((w[1]-sh[1])/vs+1)*8,8+8*w[2]/vs])
    dv=Rz@np.array(d,float); uv=Rz@np.array(u,float)
    if kind=='shield': a_,b_=dv,uv           # long axis up, face normal toward viewer
    elif kind=='bow': a_,b_=dv,uv            # limb axis, belly
    else: a_,b_=dv,uv
    S=SC[n]
    out={}
    for person in (3,1):
        A,B=want(kind,person)
        M=rot_from(a_,b_,A,B)
        rx,ry,rz=euler_xyz(M)
        assert np.abs(compose((rx,ry,rz))-M).max()<0.02,(n,person,rx,ry,rz)
        assert np.allclose(M@a_/np.linalg.norm(a_),unit(A),atol=1e-6)
        L=json.load(open(f'/tmp/mesh/{n}.json'))['len']; s=(TP if person==3 else FP)[n]*16/L
        tr=-s*(M@(gmc-8))
        out[person]=(rx,ry,rz,tr,s)
    return out

def display(n):
    o=hold(n)
    def R(v): return [round(x,2) for x in v]
    d={}
    for person,key in ((3,'thirdperson'),(1,'firstperson')):
        rx,ry,rz,tr,s=o[person]
        d[key+'_righthand']={"rotation":R([rx,ry,rz]),"translation":R(tr),"scale":[round(s,3)]*3}
        d[key+'_lefthand']={"rotation":R([rx,-ry,-rz]),"translation":R([-tr[0],tr[1],tr[2]]),"scale":[round(s,3)]*3}
    return d
if __name__=='__main__':
    import json
    for n in HOLD: print(n,json.dumps(display(n)['thirdperson_righthand']),json.dumps(display(n)['firstperson_righthand']))
