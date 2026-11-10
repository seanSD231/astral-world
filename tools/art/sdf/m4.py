import sys; sys.path.insert(0,'/tmp/r3')
from m3 import *
import m2
from m2 import *
def longsword():
    sc=Scene()
    BL=17.0
    def hw(y):
        y=np.asarray(y,float); return np.where(y<BL-4,2.1*(1-.1*y/BL),2.1*.9*np.maximum(.02,(BL-y)/4)**.55)
    ys=np.concatenate([np.linspace(0,BL-4,24),np.linspace(BL-3.8,BL,12)])
    def twil(p):
        t=np.clip(p[:,1]/BL,0,1)[:,None]
        a=np.array([.78,.2,.08]); b=np.array([1.0,.5,.15]); c=np.array([1.0,.78,.4])
        col=np.where(t<.65,a*(1-t/.65)+b*(t/.65),b*(1-(t-.65)/.35)+c*((t-.65)/.35))
        return col*(.88+.12*fbm(p*1.5)[:,None])
    glow=lambda p:twil(p)*(np.clip(1-np.abs(p[:,0])/(hw(np.clip(p[:,1],0,BL))*.5+1e-3),0,1)**1.2)[:,None]*1.7
    blade=Mat(base=twil,metal=.7,gloss=100,envk=.7,emis=glow)
    L=[(-float(hw(y)),y) for y in ys]; Rr=[(float(hw(y)),y) for y in ys[::-1]]
    sc.add(poly(L+Rr,.3,r=.08),blade)
    sc.add(poly([(-.7,y) for y in ys[:24]]+[(.7,y) for y in ys[:24][::-1]],.42,r=.1),blade)     # raised centre
    gold=Mat(base=(.95,.7,.22),metal=1.0,gloss=100,envk=1.0)
    dark=Mat(base=(.3,.08,.07),metal=.3,gloss=40,bump=lambda p:.04*fbm(p*6),bk=.5)
    sc.add(rbox((0,-.5,0),(3.6,.5,.55),r=.2),gold)                                              # crossguard
    for s_ in (-1,1): sc.add(sphere((s_*3.7,-.5,0),.62),gold)
    chain(sc,[C(0,-1.0,0),C(0,-4.0,0),C(0,-6.8,0)],.62,.58,dark)
    for yy in (-1.6,-3.2,-4.8,-6.2): sc.add(torus((0,yy,0),.66,.1,rotx(PI/2)),gold)
    sc.add(sphere((0,-7.6,0),1.05),gold)
    return sc
m2.JOBS['twilight_longsword']=(longsword,-PI/4,1.0,(0,5),.06,(1.0,.6,.25),.3)
if __name__=='__main__':
    img=render(longsword(),N=320,ang=-PI/4,scale=1.0,mc=(0,5),unit=.06); img=bloom(img); save(img,'/tmp/r3/n_twilight_longsword.png')
