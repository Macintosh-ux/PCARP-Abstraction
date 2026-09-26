from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageOps, ImageEnhance
import numpy as np
import io

# Create a blank RGB image
img = Image.new('RGB', (200, 200), color='white')

# Draw shapes and text
draw = ImageDraw.Draw(img)
draw.rectangle([20, 20, 180, 60], fill='blue', outline='black')
draw.ellipse([50, 80, 150, 180], fill='red', outline='black')
draw.line([0, 0, 200, 200], fill='green', width=3)
draw.text((10, 170), "Hello PIL", fill='black')

# Apply filters
blurred = img.filter(ImageFilter.BLUR)
sharpened = img.filter(ImageFilter.SHARPEN)
edges = img.filter(ImageFilter.FIND_EDGES)

# Convert to grayscale and invert
gray = ImageOps.grayscale(img)
inverted = ImageOps.invert(gray)

# Enhance brightness and contrast
enhancer_brightness = ImageEnhance.Brightness(img)
bright_img = enhancer_brightness.enhance(1.5)

enhancer_contrast = ImageEnhance.Contrast(img)
contrast_img = enhancer_contrast.enhance(2.0)

# Resize, crop, rotate, transpose
resized = img.resize((100, 100))
cropped = img.crop((50, 50, 150, 150))
rotated = img.rotate(45)
flipped = img.transpose(Image.FLIP_LEFT_RIGHT)

# Convert to different modes
rgba_img = img.convert("RGBA")
l_img = img.convert("L")

# Create image from numpy array
array = np.zeros((100, 100, 3), dtype=np.uint8)
array[25:75, 25:75] = [255, 0, 0]  # red square
np_img = Image.fromarray(array)

# Save to in-memory buffer (not to disk)
buffer = io.BytesIO()
img.save(buffer, format='PNG')
buffer.seek(0)
loaded_img = Image.open(buffer)

# Show image info (without displaying)
print("Original image mode:", img.mode)
print("Size:", img.size)
print("Format (in-memory):", loaded_img.format)
