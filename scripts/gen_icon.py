"""
Generate the Cortex app icon — a futuristic blue/cyan/green orb on dark background.
Outputs adaptive-icon-ready PNGs at all required densities.
"""
import os
from PIL import Image, ImageDraw, ImageFilter

OUT_BASE = "/home/z/my-project/cortex/app/src/main/res"
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def make_orb(size: int) -> Image.Image:
    """Draw a futuristic orb: dark bg + soft blue/cyan/green glow + bright core."""
    S = size * 4
    img = Image.new("RGBA", (S, S), (7, 8, 12, 255))
    cx, cy = S // 2, S // 2
    max_r = int(S * 0.42)

    # Outer halo (soft glow)
    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    halo_d = ImageDraw.Draw(halo)
    for r in range(int(S * 0.55), max_r, -1):
        t = (r - max_r) / (S * 0.55 - max_r)
        alpha = int(80 * (1 - t) ** 2)
        if t > 0.66:
            col = (59, 130, 246, alpha)
        elif t > 0.33:
            col = (6, 182, 212, alpha)
        else:
            col = (16, 185, 129, alpha)
        halo_d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    halo = halo.filter(ImageFilter.GaussianBlur(S // 60))
    img = Image.alpha_composite(img, halo)

    # Main orb
    orb = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    orb_d = ImageDraw.Draw(orb)
    for r in range(max_r, 0, -1):
        t = r / max_r
        if t > 0.66:
            col = (59, 130, 246, int(255 * (1 - (t - 0.66) / 0.34 * 0.3)))
        elif t > 0.33:
            mix = (t - 0.33) / 0.33
            r_col = int(6 + (59 - 6) * mix)
            g_col = int(182 + (130 - 182) * mix)
            b_col = int(212 + (246 - 212) * mix)
            col = (r_col, g_col, b_col, 255)
        else:
            mix = t / 0.33
            r_col = int(150 + (6 - 150) * mix)
            g_col = int(230 + (182 - 230) * mix)
            b_col = int(180 + (212 - 180) * mix)
            col = (r_col, g_col, b_col, 255)
        orb_d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    img = Image.alpha_composite(img, orb)

    # Specular highlight (top-left)
    spec = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    spec_d = ImageDraw.Draw(spec)
    spec_cx, spec_cy = cx - int(S * 0.12), cy - int(S * 0.14)
    spec_r = int(S * 0.18)
    for r in range(spec_r, 0, -1):
        t = r / spec_r
        alpha = int(150 * (1 - t) ** 2)
        spec_d.ellipse([spec_cx - r, spec_cy - r, spec_cx + r, spec_cy + r],
                       fill=(255, 255, 255, alpha))
    spec = spec.filter(ImageFilter.GaussianBlur(S // 80))
    img = Image.alpha_composite(img, spec)

    # Ring around orb
    ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ring_d = ImageDraw.Draw(ring)
    ring_r = int(S * 0.46)
    ring_d.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r],
                   outline=(6, 182, 212, 80), width=max(2, S // 200))
    ring = ring.filter(ImageFilter.GaussianBlur(S // 200))
    img = Image.alpha_composite(img, ring)

    img = img.resize((size, size), Image.LANCZOS)
    return img


def make_foreground(size: int) -> Image.Image:
    """Adaptive icon foreground — orb on transparent."""
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx, cy = S // 2, S // 2
    max_r = int(S * 0.32)

    orb = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    orb_d = ImageDraw.Draw(orb)
    for r in range(max_r, 0, -1):
        t = r / max_r
        if t > 0.66:
            col = (59, 130, 246, int(255 * (1 - (t - 0.66) / 0.34 * 0.25)))
        elif t > 0.33:
            mix = (t - 0.33) / 0.33
            r_col = int(6 + (59 - 6) * mix)
            g_col = int(182 + (130 - 182) * mix)
            b_col = int(212 + (246 - 212) * mix)
            col = (r_col, g_col, b_col, 255)
        else:
            mix = t / 0.33
            r_col = int(150 + (6 - 150) * mix)
            g_col = int(230 + (182 - 230) * mix)
            b_col = int(180 + (212 - 180) * mix)
            col = (r_col, g_col, b_col, 255)
        orb_d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    img = Image.alpha_composite(img, orb)

    spec = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    spec_d = ImageDraw.Draw(spec)
    spec_cx, spec_cy = cx - int(S * 0.10), cy - int(S * 0.12)
    spec_r = int(S * 0.14)
    for r in range(spec_r, 0, -1):
        t = r / spec_r
        alpha = int(170 * (1 - t) ** 2)
        spec_d.ellipse([spec_cx - r, spec_cy - r, spec_cx + r, spec_cy + r],
                       fill=(255, 255, 255, alpha))
    spec = spec.filter(ImageFilter.GaussianBlur(S // 100))
    img = Image.alpha_composite(img, spec)

    ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ring_d = ImageDraw.Draw(ring)
    ring_r = int(S * 0.36)
    ring_d.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r],
                   outline=(6, 182, 212, 100), width=max(2, S // 250))
    img = Image.alpha_composite(img, ring)
    img = img.resize((size, size), Image.LANCZOS)
    return img


def main():
    for density, size in DENSITIES.items():
        img = make_orb(size)
        img.save(f"{OUT_BASE}/{density}/ic_launcher.png", "PNG")
        img.save(f"{OUT_BASE}/{density}/ic_launcher_round.png", "PNG")
        print(f"Wrote {density} ({size}x{size})")

    fg = make_foreground(432)
    for density, scale in [("mipmap-mdpi", 108),
                            ("mipmap-hdpi", 162),
                            ("mipmap-xhdpi", 216),
                            ("mipmap-xxhdpi", 324),
                            ("mipmap-xxxhdpi", 432)]:
        sized = fg.resize((scale, scale), Image.LANCZOS)
        sized.save(f"{OUT_BASE}/{density}/ic_launcher_foreground.png", "PNG")
    print("Wrote foreground layers")

    with open(f"{OUT_BASE}/drawable/ic_launcher_background.xml", "w") as f:
        f.write("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#07080C" />
</shape>
""")

    with open(f"{OUT_BASE}/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
        f.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
""")
    with open(f"{OUT_BASE}/mipmap-anydpi-v26/ic_launcher_round.xml", "w") as f:
        f.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
""")
    print("Wrote adaptive icon XML")
    print("Done.")


if __name__ == "__main__":
    main()
