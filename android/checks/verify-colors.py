#!/usr/bin/env python3
"""Check active text roles from the actual Android resource files."""
from pathlib import Path
import xml.etree.ElementTree as ET


def luminance(color):
    values = [int(color[i:i+2], 16) / 255 for i in (1, 3, 5)]
    values = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in values]
    return sum(a * b for a, b in zip(values, (.2126, .7152, .0722)))


root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
for theme in ('values', 'values-night'):
    colors = {n.attrib['name']: n.text for n in ET.parse(root / theme / 'colors.xml').getroot()}
    for foreground, background in [('text', 'background'), ('text', 'surface'),
                                   ('text_secondary', 'background'), ('text_secondary', 'surface'),
                                   ('button', 'surface'), ('on_button', 'button')]:
        first, second = sorted([luminance(colors['app_' + foreground]), luminance(colors['app_' + background])])
        ratio = (second + .05) / (first + .05)
        assert ratio >= 4.5, (theme, foreground, background, ratio)
        print(f'{theme}: {foreground}/{background} {ratio:.2f}:1')
