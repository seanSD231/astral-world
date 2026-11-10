import json,os,shutil,glob,sys
sys.path.insert(0,'/tmp/r3')
import hold, m4
from PIL import Image
R='/home/claude/astral-world/src/main/resources/assets/astral_world'
NAMES=['shadow_sword','shadow_shield','spear_of_sun','storm_katana','bone_greatsword','snake_rifle','chained_bow','twilight_longsword']
disp={
 "thirdperson_righthand":{"rotation":[0,-90,55],"translation":[0,4,.5],"scale":[.85,.85,.85]},
 "thirdperson_lefthand":{"rotation":[0,90,-55],"translation":[0,4,.5],"scale":[.85,.85,.85]},
 "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[.68,.68,.68]},
 "firstperson_lefthand":{"rotation":[0,90,-25],"translation":[1.13,3.2,1.13],"scale":[.68,.68,.68]},
 "gui":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},
 "ground":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[.5,.5,.5]},
 "fixed":{"rotation":[0,180,0],"translation":[0,0,0],"scale":[1,1,1]},
}
shield_disp=dict(disp)
shield_disp.update({
 "thirdperson_righthand":{"rotation":[0,-90,0],"translation":[1,3,1],"scale":[1.0,1.0,1.0]},
 "thirdperson_lefthand":{"rotation":[0,90,0],"translation":[1,3,1],"scale":[1.0,1.0,1.0]},
 "firstperson_righthand":{"rotation":[0,-90,0],"translation":[1.5,3,1.5],"scale":[.8,.8,.8]},
 "firstperson_lefthand":{"rotation":[0,90,0],"translation":[1.5,3,1.5],"scale":[.8,.8,.8]},
})
SC={'starry_sniper':1.5,'shadow_sword':1.2,'shadow_shield':.6,'spear_of_sun':1.5,'storm_katana':1.15,'bone_greatsword':1.35,'snake_rifle':1.45,'chained_bow':1.5}
parts_java=[]
import re
mdir=f'{R}/models/item'
os.makedirs(f'{R}/meshes',exist_ok=True)
for f in glob.glob(f'{mdir}/*_3d.json')+glob.glob(f'{mdir}/*_part_*.json')+glob.glob(f'{R}/textures/item/*_vox.png*'): os.remove(f)
parts_java=[]
for n in NAMES:
    shutil.copy(f'/tmp/mesh/{n}.bin',f'{R}/meshes/{n}.bin')
    dd=json.loads(json.dumps(disp)); dd.update(hold.display(n))
    json.dump({"parent":"minecraft:builtin/entity","gui_light":"front","textures":{"particle":f"astral_world:item/{n}"},"display":dd},open(f'{R}/models/item/{n}.json','w'),indent=1)
    json.dump({"parent":"minecraft:item/generated","textures":{"layer0":f"astral_world:item/{n}"}},open(f'{R}/models/item/{n}_icon.json','w'))
    if n!='twilight_longsword':
        im=Image.open(f'/tmp/r3/n_{n}.png').convert('RGBA').resize((64,64),Image.LANCZOS); im.save(f'{R}/textures/item/{n}.png')
    info=json.load(open(f'/tmp/mesh/{n}.json'))
    ps=[]
    for p in info['parts']:
        t=p['type']
        if t=='spin': ps.append(f'new Part("{p["name"]}","spin",new float[]{{{",".join(str(x)+"f" for x in p["pivot"])}}},{p["speed"]}f)')
        else: ps.append(f'new Part("{p["name"]}","{t}",new float[]{{{",".join(str(x)+"f" for x in p["vec"])}}},0f)')
    parts_java.append(f'        Map.entry("{n}", new Part[]{{{",".join(ps)}}})')
java='''package com.astralspace;

import java.util.Map;

/** Generated: moving-part descriptors for the 3D Uniqueness items (baked 0..1 model units). */
final class UniqAnim {
    private UniqAnim() {}
    record Part(String name, String type, float[] v, float speed) {}
    static final Map<String, Part[]> PARTS = Map.ofEntries(
''' + ",\n".join(parts_java) + '''
    );
}
'''
open('/home/claude/astral-world/src/main/java/com/astralspace/UniqAnim.java','w').write(java)
# remove old items
for old in ['star_cane','cloak_of_shadows','golden_sun','ball_of_storms','feathered_serpent_book','red_pip_die','bandaged_figure']:
    for p in (f'{R}/models/item/{old}.json',f'{R}/textures/item/{old}.png'):
        if os.path.exists(p): os.remove(p)
print(open('/home/claude/astral-world/src/main/java/com/astralspace/UniqAnim.java').read())
