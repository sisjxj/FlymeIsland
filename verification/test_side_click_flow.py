from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/main/java/io/github/flymeisland/diagnostic/SystemUiAicyIsland.java'
s=p.read_text(encoding='utf8');a=s.index('((CameraIslandLayout) root).setSide(key, icon, side == null ? null : () -> {');b=s.index('        });',a);block=s[a:b]
required=['deferSideBirth();','bindNotice(selected);','updateSide();','morph(true);']
positions=[block.index(x) for x in required]
assert positions==sorted(positions)
print('PASS: production side-click defers secondary bubble before primary swap and expansion')
