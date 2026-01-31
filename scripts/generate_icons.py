import os
from PIL import Image

# Configuration
SOURCE_IMAGE = r'd:\Antigravity\ComfyUI frontend\.gsd\phases\72\icon_style_1_minimalist.png'
RES_DIR = r'd:\Antigravity\ComfyUI frontend\app\src\main\res'

# Densities and sizes (Legacy standard icon size is 48dp)
SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

def generate_icons():
    if not os.path.exists(SOURCE_IMAGE):
        print(f"Error: Source image not found at {SOURCE_IMAGE}")
        return

    with Image.open(SOURCE_IMAGE) as img:
        # High quality resize
        for folder, size in SIZES.items():
            folder_path = os.path.join(RES_DIR, folder)
            if not os.path.exists(folder_path):
                os.makedirs(folder_path)
            
            # Legacy icon (standard)
            out_img = img.resize((size, size), Image.Resampling.LANCZOS)
            out_img.save(os.path.join(folder_path, 'ic_launcher.png'))
            out_img.save(os.path.join(folder_path, 'ic_launcher_round.png'))
            print(f"Generated icons for {folder} ({size}x{size})")

if __name__ == "__main__":
    generate_icons()
