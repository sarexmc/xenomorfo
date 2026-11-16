#!/usr/bin/env python3
"""Convierte el glTF de Blockbench (xenomorph-runner) a xeno_model.json para el renderer del mod.
Uso: python3 convert_gltf.py model.gltf salida.json"""
import json,base64,sys,math,numpy as np
src,dst=sys.argv[1],sys.argv[2]
g=json.load(open(src)); buf=base64.b64decode(g['buffers'][0]['uri'].split(',')[1])
def acc(i):
    a=g['accessors'][i];bv=g['bufferViews'][a['bufferView']]
    n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];dt={5126:'f4',5123:'u2',5125:'u4'}[a['componentType']]
    return np.frombuffer(buf,dtype=dt,count=a['count']*n,offset=bv.get('byteOffset',0)+a.get('byteOffset',0)).reshape(a['count'],n)
N=g['nodes'];par={}
for i,n in enumerate(N):
    for c in n.get('children',[]): par[c]=i
def qmat(q):
    x,y,z,w=q;return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def local(n,t=None,r=None):
    M=np.eye(4);M[:3,:3]=qmat(r or n.get('rotation',[0,0,0,1]));M[:3,3]=t or n.get('translation',[0,0,0]);return M
world={}
def W(i):
    if i not in world: world[i]=(W(par[i]) if i in par else np.eye(4))@local(N[i])
    return world[i]
# vértices mundo en reposo -> orientación y altura
pts=[];nm={}
for i,n in enumerate(N):
    nm.setdefault(n.get('name'),i)
    if 'mesh' in n:
        P=acc(g['meshes'][n['mesh']]['primitives'][0]['attributes']['POSITION']);pts.append((np.c_[P,np.ones(len(P))]@W(i).T)[:,:3])
pts=np.vstack(pts);mn,mx=pts.min(0),pts.max(0)
head=W(nm['head'])[:3,3];tail=W(nm['tail6'])[:3,3];d=head-tail
# yaw para que la cabeza apunte a -Z del modelo (frente de las entidades de Minecraft)
yaw=math.degrees(math.atan2(d[0],-d[2]))  # rotación Y que lleva d a -Z
print('bbox',mn.round(2),mx.round(2),'head-tail',d.round(2),'yaw',round(yaw,1))
out={'pre':{'yaw':yaw},'nodes':[],'cubes':[],'anims':{}}
for i,n in enumerate(N):
    o={'n':n.get('name') or '','p':par.get(i,-1),'t':n.get('translation',[0,0,0]),'r':n.get('rotation',[0,0,0,1])}
    if 'mesh' in n:
        p=g['meshes'][n['mesh']]['primitives'][0];P=acc(p['attributes']['POSITION']);Nn=acc(p['attributes']['NORMAL']);U=acc(p['attributes']['TEXCOORD_0']);I=acc(p['indices']).flatten()
        o['m']=len(out['cubes'])
        out['cubes'].append({'v':[round(float(x),5) for k in range(len(P)) for x in (*P[k],*Nn[k],*U[k])],'i':[int(x) for x in I]})
    out['nodes'].append(o)
for a in g['animations']:
    tr=[]
    for ch in a['channels']:
        s=a['samplers'][ch['sampler']];tr.append({'n':ch['target']['node'],'p':ch['target']['path'][0],'k':[round(float(x),4) for x in acc(s['input']).flatten()],'v':[round(float(x),5) for x in acc(s['output']).flatten()]})
    out['anims'][a['name']]={'len':max(max(t['k']) for t in tr),'tr':tr}
# altura mínima del modelo tras yaw, con la animación 'idle' en t=0 (aprox: reposo)
c,s=math.cos(math.radians(yaw)),math.sin(math.radians(yaw));ys=pts[:,1];out['pre']['dy']=float(-ys.min());out['pre']['dz']=float(-W(nm['body'])[2,3])
w=max(mx[0]-mn[0],mx[2]-mn[2]);print('alto',round(mx[1]-mn[1],2),'dy',round(out['pre']['dy'],3))
json.dump(out,open(dst,'w'),separators=(',',':'));print('ok',len(json.dumps(out))//1024,'KB')
