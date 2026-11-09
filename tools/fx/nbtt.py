import gzip, struct
def _rd(b,p,t):
    if t==1: return b[p],p+1
    if t==2: return struct.unpack('>h',b[p:p+2])[0],p+2
    if t==3: return struct.unpack('>i',b[p:p+4])[0],p+4
    if t==4: return struct.unpack('>q',b[p:p+8])[0],p+8
    if t==5: return struct.unpack('>f',b[p:p+4])[0],p+4
    if t==6: return struct.unpack('>d',b[p:p+8])[0],p+8
    if t==7:
        n=struct.unpack('>i',b[p:p+4])[0]; return bytes(b[p+4:p+4+n]),p+4+n
    if t==8:
        n=struct.unpack('>H',b[p:p+2])[0]; return b[p+2:p+2+n].decode('utf8'),p+2+n
    if t==9:
        et=b[p]; n=struct.unpack('>i',b[p+1:p+5])[0]; p+=5; out=[]
        for _ in range(n): v,p=_rd(b,p,et); out.append(v)
        return (et,out),p
    if t==10:
        out={}
        while True:
            tt=b[p]; p+=1
            if tt==0: return out,p
            n=struct.unpack('>H',b[p:p+2])[0]; k=b[p+2:p+2+n].decode(); p+=2+n
            v,p=_rd(b,p,tt); out[k]=(tt,v)
    if t==11:
        n=struct.unpack('>i',b[p:p+4])[0]; return list(struct.unpack('>%di'%n,b[p+4:p+4+4*n])),p+4+4*n
    if t==12:
        n=struct.unpack('>i',b[p:p+4])[0]; return list(struct.unpack('>%dq'%n,b[p+4:p+4+8*n])),p+4+8*n
def load(path):
    b=gzip.open(path).read(); t=b[0]; n=struct.unpack('>H',b[1:3])[0]; v,_=_rd(b,3+n,t); return (t,v)
def _wr(t,v,o):
    if t==1: o.append(struct.pack('>B',v&255))
    elif t==2: o.append(struct.pack('>h',v))
    elif t==3: o.append(struct.pack('>i',v))
    elif t==4: o.append(struct.pack('>q',v))
    elif t==5: o.append(struct.pack('>f',v))
    elif t==6: o.append(struct.pack('>d',v))
    elif t==7: o.append(struct.pack('>i',len(v))); o.append(bytes(v))
    elif t==8: e=v.encode('utf8'); o.append(struct.pack('>H',len(e))); o.append(e)
    elif t==9:
        et,l=v; o.append(struct.pack('>Bi',et,len(l)))
        for x in l: _wr(et,x,o)
    elif t==10:
        for k,(tt,vv) in v.items():
            o.append(struct.pack('>B',tt)); e=k.encode(); o.append(struct.pack('>H',len(e))); o.append(e); _wr(tt,vv,o)
        o.append(b'\0')
    elif t==11: o.append(struct.pack('>i',len(v))); o.append(struct.pack('>%di'%len(v),*v))
    elif t==12: o.append(struct.pack('>i',len(v))); o.append(struct.pack('>%dq'%len(v),*v))
def save(path,root):
    o=[struct.pack('>BH',root[0],0)]; _wr(root[0],root[1],o)
    gzip.open(path,'wb').write(b''.join(o))
def py(n):
    t,v=n
    if t==10: return {k:py(x) for k,x in v.items()}
    if t==9: return [py((v[0],x)) for x in v[1]]
    return v
def show(n,path='',out=None,skip=()):
    t,v=n
    if t==10:
        for k,x in v.items():
            if any(s in path+'/'+k for s in skip): continue
            show(x,path+'/'+k,out,skip)
    elif t==9 and v[1] and v[0] in(9,10):
        for i,x in enumerate(v[1]): show((v[0],x),path+'/%d'%i,out,skip)
    else: print(path,t,str(v)[:100])
