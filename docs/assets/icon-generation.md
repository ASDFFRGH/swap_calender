# FX trader launcher icon

Created with the built-in image generation tool using the supplied character image.
The square preview is `fx-trader-icon.png`; the transparent foreground used by
Android is `../../app/src/main/res/drawable-nodpi/ic_launcher_artwork.png`.

The launcher uses an adaptive icon with a pale blue background and a 21dp inset
on the 108dp foreground canvas to keep the character and chart inside launcher
masks. Both standard and round launcher icons use this resource. The app's
minimum Android API level is 26, so no legacy launcher fallback is needed.

## Generation prompt

Use case: identity-preserve
Asset type: square Android launcher app icon, full-bleed 1024x1024 illustration.
Input image 1: edit target and exact character/face reference.
Primary request: depict this same chibi character trading FX at a compact laptop, preserving her original facial expression exactly.
Subject invariants: same blonde hair and bangs, big rounded purple eyes with original highlights, thick black arched eyebrows in exactly the same position, pink cheeks, tiny straight horizontal neutral mouth, original calm blank expression. Do not smile, frown, squint, change gaze, or add excitement. Keep her original black-and-white wide brim hat, blue cross hair clip, white black blue costume and bold dark outlines.
Scene: replace street with a clean pale blue background. Character faces directly forward, visible from hat through upper body, her hands on a laptop keyboard. Laptop below her face, with its screen angled to the viewer showing a simple clearly visible FX candlestick chart with a few green and red bars and small currency symbols $ and ¥. No extra characters, no coins or clutter.
Composition: icon composition, character's face dominates center and remains unobstructed. Entire hat and laptop must fit within the central circular 66% safe area of the square canvas, with generous plain pale-blue padding to all four edges so Android circular cropping preserves face, hat and chart. Face large enough to read at small size. Flat polished chibi illustration consistent with reference, thick crisp dark outlines, original colors. Solid opaque full-square background, no baked-in rounded corners, border, labels, title, watermark or photorealistic street.

## Transparent foreground prompt

Use case: background-extraction. Input image 1 is the edit target: the generated chibi FX trader icon. Remove ONLY the pale blue background and make it fully transparent. Preserve the entire character, hat, face, facial expression, eyes, eyebrows, tiny straight neutral mouth, colors, hair, proportions, laptop with currency symbols and candlestick chart, all outlines and every foreground pixel as closely as possible. Do not redraw or reinterpret the subject. Keep exact same square composition and existing padding. No shadow, no glow, no checkerboard drawn into image. Actual alpha transparency.

