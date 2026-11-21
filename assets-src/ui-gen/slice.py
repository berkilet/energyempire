import sys
from PIL import Image, ImageFilter
import numpy as np
from scipy import ndimage
D='/tmp/claude-0/-home-user-energyempire/f2b59388-b5a4-5832-966a-880d0ea0a98d/images/'
OUT='/home/user/energyempire/src/main/resources/ui/'
SC='/tmp/claude-0/-home-user-energyempire/f2b59388-b5a4-5832-966a-880d0ea0a98d/scratchpad/'
SETS={
 3:['fin_summary','fin_schedule','fin_loans','fin_market','fin_capital','fin_mna'],
 4:['layer_normal','layer_wind','layer_sun','layer_coal','layer_gas','layer_water'],
 5:['grp_parent','grp_sub','grp_control','grp_minority','grp_tender','grp_merger','grp_squeeze','grp_cash','grp_dividend','grp_growth','grp_cautious','grp_cashcow'],
 6:['as_plants','as_lines','as_substations','as_opapps','as_tenders','fuel_coal','fuel_gas','fuel_uranium','fuel_wind','fuel_solar','fuel_carbon','fuel_price'],
 7:['st_fault','st_warranty','st_upgrade','st_carbon','st_saturated','st_default','st_sale','st_tender','st_construction','st_public','st_operator','st_info'],
 8:['ui_help','ui_save','ui_load','ui_settings','ui_home','ui_exit','ui_resume','ui_newgame','ui_soundoff','ui_shadows','ui_confirm','ui_cancel'],
 10:['sup_ferrum','sup_taurus','sup_hydrova','sup_alpina','sup_atomtek','sup_polaris','sup_sunpeak','sup_helion','sup_lumina','sup_kuzey','sup_ruzgarsan','sup_aeolus','sup_nordwind','sup_boreal','sup_atlas','sup_meridian'],
 11:['bank_kiyi','bank_yesil','bank_demirtas'],
 12:['org_kamu','org_usi'],
 9:['inv_plant','inv_connect','inv_tender','inv_sale','inv_line','inv_tm154','inv_tm380','inv_won'],
}
COLS={10:4,11:3,12:2,3:3,4:3,5:4,6:4,7:4,8:4,9:4}
def magenta(a):
    r,g,b=a[...,0],a[...,1],a[...,2]
    return (r>60)&(b>60)&(g<r*0.55)&(g<b*0.55)&(abs(r-b)<70)
def alpha_cut(im,erode=3):
    a=np.array(im.convert('RGB')).astype(int)
    m=magenta(a)
    lab,n=ndimage.label(m)
    border=set(lab[0,:])|set(lab[-1,:])|set(lab[:,0])|set(lab[:,-1]); border.discard(0)
    bg=np.isin(lab,list(border))
    fg=Image.fromarray(((~bg)*255).astype('uint8')).filter(ImageFilter.MinFilter(2*erode+1)).filter(ImageFilter.GaussianBlur(1.0))
    r=im.convert('RGBA'); r.putalpha(fg); return r
ONLY=[int(x) for x in sys.argv[1:]]
for n,names in SETS.items():
    if ONLY and n not in ONLY: continue
    im=Image.open(D+'%d.jpg'%n).convert('RGB'); a=np.array(im).astype(int)
    bg=magenta(a); lab,k=ndimage.label(~bg)
    sizes=ndimage.sum(~bg,lab,range(1,k+1)); objs=ndimage.find_objects(lab)
    tiles=[(objs[i],sizes[i]) for i in range(k) if sizes[i]>30000]
    assert len(tiles)==len(names),(n,len(tiles),len(names))
    cols=COLS[n]
    boxes=sorted([(s[0].start,s[1].start,s[0].stop,s[1].stop) for s,_ in tiles])
    rows=[boxes[i:i+cols] for i in range(0,len(boxes),cols)]
    ordered=[b for r in rows for b in sorted(r,key=lambda t:t[1])]
    for name,(y0,x0,y1,x1) in zip(names,ordered):
        t=alpha_cut(im.crop((x0-4,y0-4,x1+4,y1+4)))
        t=t.crop(t.getbbox()); w,h=t.size
        # kutuyu kareye yakin tut: golge/alt tasma varsa ust kenardan kare kes
        if h>w*1.08: t=t.crop((0,0,w,round(w*1.0)))
        w,h=t.size; sc=min(136/w,144/h)
        r=t.resize((round(w*sc),round(h*sc)),Image.LANCZOS)
        c=Image.new('RGBA',(136,144),(0,0,0,0)); c.alpha_composite(r,((136-r.width)//2,(144-r.height)//2))
        c.save(OUT+'icon_%s.png'%name)
    print(n,len(names),'ok')
