from PIL import Image

src = r"C:\Users\Home\.minimax\v2\assets\2026\11\12\16-18-58-421-asset_20261112-161858-421_3bd96aaa94a1_0d59d635-6316341350210671721.jpg"
im = Image.open(src).convert("RGB")
print("size", im.size)
w, h = im.size
# the media button row sits around y=255..305 in the 1280-tall screenshot
crop = im.crop((140, 240, 530, 315)).resize((390 * 3, 75 * 3), Image.LANCZOS)
crop.save(r"E:\Spartial Music\.tmp_buttons.png")
print("saved")
