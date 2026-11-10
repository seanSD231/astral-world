import numpy as np, math
from PIL import Image
from scipy.ndimage import gaussian_filter
S=256; F=8; rng=np.random.default_rng(3)
Y,X=np.mgrid[0:S,0:S].astype(float)
n1=gaussian_filter(rng.random((S,S)),3); n1=(n1-n1.min())/(n1.max()-n1.min())
n2=gaussian_filter(rng.random((S,S)),1.2); n2=(n2-n2.min())/(n2.max()-n2.min())
L=np.array([-.5,-.6,.62]); L/=np.linalg.norm(L)
def ball(img,cx,cy,R,t,tilt):
    ph=t/F*6.2832
    dx=(X-cx)/R; dy=(Y-cy)/R; r2=dx*dx+dy*dy; ins=r2<1
    dz=np.sqrt(np.clip(1-r2,0,1))
    N=np.stack([dx,dy,dz],-1)
    lam=np.clip(N@L,0,1)
    # silver sclera: brushed metal bands + soft gradient
    band=.5+.5*np.sin((dy*6+dx*2)+n1*3)
    base=np.array([.62,.67,.72])
    col=base[None,None,:]*(.35+.75*lam[...,None])*(.9+.12*band[...,None])
    # environment reflection (cool sky above, dark ground below)
    env=np.clip(.5-dy*.5,0,1)[...,None]*np.array([.15,.2,.3])
    col+=env*(1-dz[...,None])*1.2
    spec=np.clip(N@((L+np.array([0,0,1]))/np.linalg.norm(L+np.array([0,0,1]))),0,1)**40
    col+=spec[...,None]*.9
    # iris: faces front but shifted along the sphere toward the viewer-right
    ic=(R*.18*tilt[0],R*.18*tilt[1])
    ix=X-(cx+ic[0]); iy=Y-(cy+ic[1]); ir=np.hypot(ix,iy)/R
    iris=ir<.52
    rr=np.clip(ir/.52,0,1); ang=np.arctan2(iy,ix)
    fib=.5+.5*np.sin(ang*14+n1*5+ph*1.0)
    ic_=np.zeros((S,S,3))
    ic_[...,0]=.05+.25*fib*(1-rr)+.05*rr
    ic_[...,1]=.32+.45*(1-rr)**1.2+.15*fib*(1-rr)
    ic_[...,2]=.78+.22*(1-rr*.5)
    ic_*= (.7+.4*n2[...,None])
    ic_[rr>.82]*=.5            # dark limbal ring
    ic_*= (.55+.6*lam[...,None])
    # starry twinkles in the iris
    for k in range(26):
        a=rng.random()*6.28; rad=rng.random()*.44; sp=rng.random()*6.28
        if .5+.5*math.sin(ph+sp)>.6:
            px=cx+ic[0]+math.cos(a)*rad*R; py=cy+ic[1]+math.sin(a)*rad*R
            g=np.exp(-((X-px)**2+(Y-py)**2)/(2*1.3**2)); ic_+=g[...,None]*np.array([.7,.85,1])
    col=np.where(iris[...,None],ic_,col)
    # keyhole pupil
    pr=np.hypot(ix,iy)/R
    pup=(pr<.1)|((np.abs(ix)<R*.035)&(iy>0)&(iy<R*.2))
    col=np.where(pup[...,None],np.array([.01,.02,.05]),col)
    # moving glint on the metal
    gl=np.exp(-(((dx*.8+dy*.6)-(-.9+1.8*(t/F)))**2)/.01)*.35*(1-dz)
    col+=gl[...,None]
    # rim darkening
    col*= (1-.35*np.clip(r2,0,1)**3)[...,None]
    a=np.clip((1-r2)*R/1.5,0,1)   # antialias edge
    out=img.copy()
    for c in range(3): out[...,c]=np.where(a>0,col[...,c]*a+img[...,c]*(1-a),img[...,c])
    out[...,3]=np.maximum(img[...,3],a)
    return out
def render(t):
    img=np.zeros((S,S,4))
    # soft blue glow behind
    for cx,cy in((92,112),(166,150)):
        g=np.exp(-(((X-cx)**2+(Y-cy)**2)/(2*62**2)))*.22
        img[...,2]=np.maximum(img[...,2],g); img[...,1]=np.maximum(img[...,1],g*.6); img[...,3]=np.maximum(img[...,3],g)
    img=ball(img,92,112,72,t,(.9,.75))        # back eye
    img=ball(img,166,150,72,t+3,(-.6,.9))    # front eye, overlapping it
    img[...,:3]=np.clip(img[...,:3],0,1)
    return (np.clip(img,0,1)*255).astype(np.uint8)
fr=[Image.fromarray(render(i),'RGBA').resize((128,128),Image.LANCZOS) for i in range(F)]
strip=Image.new('RGBA',(128,128*F))
for i,f in enumerate(fr): strip.paste(f,(0,128*i))
strip.save('eyes_strip.png'); fr[0].save('eyes_still.png')
