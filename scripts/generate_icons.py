
import os
from PIL import Image

def generate_icons(source_path, res_dir):
    """
    Generates Android mipmap icons from a source image.
    """
    
    # Define sizes for each density
    densities = {
        'mipmap-mdpi': (48, 48),
        'mipmap-hdpi': (72, 72),
        'mipmap-xhdpi': (96, 96),
        'mipmap-xxhdpi': (144, 144),
        'mipmap-xxxhdpi': (192, 192)
    }
    
    try:
        # Load source image
        img = Image.open(source_path)
        print(f"Loaded source image: {source_path} ({img.size})")

        # Process standard launcher icon
        for folder, size in densities.items():
            target_dir = os.path.join(res_dir, folder)
            os.makedirs(target_dir, exist_ok=True)
            
            # Resize
            resized_img = img.resize(size, Image.Resampling.LANCZOS)
            
            # Save standard icon
            target_path = os.path.join(target_dir, "ic_launcher.png")
            resized_img.save(target_path)
            print(f"Saved {target_path}")

            # Save round icon (using same image for now as design is rounded square)
            # In a real scenario, we might want to mask it to a circle
            target_path_round = os.path.join(target_dir, "ic_launcher_round.png")
            resized_img.save(target_path_round)
            print(f"Saved {target_path_round}")
            
        print("Icon generation complete.")
        
    except Exception as e:
        print(f"Error generating icons: {e}")

if __name__ == "__main__":
    source = r"d:\Antigravity\ComfyUI frontend\app\src\main\ic_launcher-web.png"
    res = r"d:\Antigravity\ComfyUI frontend\app\src\main\res"
    generate_icons(source, res)
