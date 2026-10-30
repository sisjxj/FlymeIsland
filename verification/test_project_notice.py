from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = (root / "app/src/main/java/io/github/flymeisland/diagnostic/MainActivity.java").read_text()
notice = (root / "app/src/main/java/io/github/flymeisland/diagnostic/ProjectNotice.java").read_text()
gradle = (root / "app/build.gradle").read_text()
license_text = (root / "LICENSE").read_text()
assert "ProjectNotice.showOnce(this)" in app
assert "ProjectNotice.SUMMARY" in app
assert "ProjectNotice.show(this)" in app
assert "https://github.com/sisjxj/FlymeIsland" in notice
assert "不收取下载费、激活费或使用费" in notice
assert "GPL-3.0-only" in notice
assert "其他人可以在符合条款的前提下复制、修改和再分发" in notice
assert "versionCode 67" in gradle
assert "0.62.0-free-source-notice" in gradle
assert "GNU GENERAL PUBLIC LICENSE" in license_text
print("Free-project notice, license wording and entry points verified")
