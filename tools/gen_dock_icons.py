import sys
from PIL import Image, ImageDraw
out=sys.argv[1]
S=4; W,H=68*S,72*S
GOLD=(236,196,120,255); GOLD_D=(170,120,60,255); CREAM=(245,225,185,255); RED=(225,95,80,255)
def base():
    im=Image.new('RGBA',(W,H),(0,0,0,0))
    grad=Image.new('RGBA',(W,H))
    g=ImageDraw.Draw(grad)
    for y in range(H):
        t=y/H; c=tuple(int(a+(b-a)*t) for a,b in zip((33,58,66),(20,35,45)))+(255,)
        g.line([(0,y),(W,y)],fill=c)
    mask=Image.new('L',(W,H),0)
    ImageDraw.Draw(mask).rounded_rectangle([2*S,2*S,W-2*S,H-2*S],radius=12*S,fill=255)
    im.paste(grad,(0,0),mask)
    d=ImageDraw.Draw(im)
    d.rounded_rectangle([2*S,2*S,W-2*S,H-2*S],radius=12*S,outline=(52,82,92,255),width=2*S)
    return im,d
def save(im,n):
    im.resize((68,72),Image.LANCZOS).save(f'{out}/{n}.png')
def P(pts): return [(x*S,y*S) for x,y in pts]
# cursor
im,d=base()
pts=P([(22,14),(22,54),(32,45),(39,60),(46,57),(39,42),(52,42)])
d.polygon(pts,fill=CREAM,outline=GOLD_D); d.line(pts+[pts[0]],fill=GOLD_D,width=2*S)
save(im,'icon_cursor')
# demolish: wrecking ball + crack / X
im,d=base()
d.line(P([(18,14),(44,14)]),fill=GOLD,width=4*S)
d.line(P([(40,14),(28,38)]),fill=(190,190,190,255),width=2*S)
d.ellipse(P([(18,34),(38,54)]),fill=(70,78,86,255),outline=(150,160,170,255),width=2*S)
d.line(P([(40,40),(54,58)]),fill=RED,width=5*S); d.line(P([(54,40),(40,58)]),fill=RED,width=5*S)
save(im,'icon_demolish')
# layers: stacked diamonds
im,d=base()
cols=[(80,170,220,255),(110,200,120,255),GOLD]
for i,c in enumerate(cols):
    y=46-i*10
    d.polygon(P([(34,y-10),(54,y),(34,y+10),(14,y)]),fill=c,outline=(20,30,36,255))
save(im,'icon_layers')
# build: crane-ish hammer + plus
im,d=base()
d.rounded_rectangle(P([(16,48),(52,58)]),radius=2*S,fill=GOLD_D)
d.rectangle(P([(22,30),(30,48)]),fill=CREAM); d.rectangle(P([(34,22),(44,48)]),fill=CREAM)
d.line(P([(48,14),(48,30)]),fill=GOLD,width=5*S); d.line(P([(40,22),(56,22)]),fill=GOLD,width=5*S)
save(im,'icon_build')

# assets: liste/pano
im,d=base()
d.rounded_rectangle(P([(18,14),(50,60)]),radius=3*S,fill=CREAM,outline=GOLD_D,width=2*S)
d.rectangle(P([(27,10),(41,18)]),fill=GOLD)
for k,y in enumerate([26,36,46]):
    d.rectangle(P([(23,y),(28,y+5)]),fill=(80,170,220,255) if k!=1 else RED)
    d.rectangle(P([(32,y+1),(45,y+4)]),fill=GOLD_D)
save(im,'icon_assets')
