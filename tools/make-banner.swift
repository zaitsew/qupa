// Draws the Android TV launcher banner (320x180 px, xhdpi), the Play Store TV
// banner (1280x720 px), the Play feature graphic (1024x500 px) and the Play
// icon (512x512 px). Run on a Mac: swift tools/make-banner.swift
import AppKit

let tileColors: [NSColor] = [
    NSColor(red: 0xEF / 255.0, green: 0x47 / 255.0, blue: 0x6F / 255.0, alpha: 1),  // A, top left
    NSColor(red: 0x4E / 255.0, green: 0xA8 / 255.0, blue: 0xDE / 255.0, alpha: 1),  // B, top right
    NSColor(red: 0xFF / 255.0, green: 0xD1 / 255.0, blue: 0x66 / 255.0, alpha: 1),  // C, bottom left
    NSColor(red: 0x06 / 255.0, green: 0xD6 / 255.0, blue: 0xA0 / 255.0, alpha: 1),  // D, bottom right
]
let ink = NSColor(red: 0x12 / 255.0, green: 0x0F / 255.0, blue: 0x2B / 255.0, alpha: 1)

/// The mark: four answer tiles around a white disc with a question mark.
/// (cx, cy) is its centre, `size` its side.
func drawMark(cx: CGFloat, cy: CGFloat, size: CGFloat) {
    let gap = size * 0.06
    let tile = (size - gap) / 2
    let origins = [
        CGPoint(x: cx - gap / 2 - tile, y: cy + gap / 2),
        CGPoint(x: cx + gap / 2, y: cy + gap / 2),
        CGPoint(x: cx - gap / 2 - tile, y: cy - gap / 2 - tile),
        CGPoint(x: cx + gap / 2, y: cy - gap / 2 - tile),
    ]
    for (i, o) in origins.enumerated() {
        tileColors[i].setFill()
        NSBezierPath(roundedRect: NSRect(x: o.x, y: o.y, width: tile, height: tile),
                     xRadius: tile * 0.2, yRadius: tile * 0.2).fill()
    }
    let r = size * 0.24
    NSColor.white.setFill()
    NSBezierPath(ovalIn: NSRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)).fill()
    let attrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: r * 1.6, weight: .heavy), .foregroundColor: ink,
    ]
    let q = NSAttributedString(string: "?", attributes: attrs)
    let qs = q.size()
    q.draw(at: NSPoint(x: cx - qs.width / 2, y: cy - qs.height / 2))
}

func render(width: Int, height: Int, iconOnly: Bool = false, to path: String) {
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8,
        samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
        bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

    let bg = NSGradient(
        starting: NSColor(red: 0.07, green: 0.06, blue: 0.17, alpha: 1),
        ending: NSColor(red: 0.20, green: 0.15, blue: 0.42, alpha: 1))!
    bg.draw(in: NSRect(x: 0, y: 0, width: width, height: height), angle: -35)

    if iconOnly {
        drawMark(cx: CGFloat(width) / 2, cy: CGFloat(height) / 2, size: CGFloat(width) * 0.66)
    } else {
        // The layout is drawn on a 320x180 grid, scaled to the height and centred.
        let s = CGFloat(height) / 180.0
        let dx = (CGFloat(width) - 320 * s) / 2
        drawMark(cx: dx + 76 * s, cy: 90 * s, size: 96 * s)
        let title: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 52 * s, weight: .heavy), .foregroundColor: NSColor.white,
        ]
        NSAttributedString(string: "Qupa", attributes: title)
            .draw(at: NSPoint(x: dx + 138 * s, y: 80 * s))
        let sub: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 15 * s, weight: .medium),
            .foregroundColor: NSColor(red: 0.72, green: 0.69, blue: 0.86, alpha: 1),
        ]
        NSAttributedString(string: "Party quiz for TV", attributes: sub)
            .draw(at: NSPoint(x: dx + 141 * s, y: 56 * s))
    }

    NSGraphicsContext.restoreGraphicsState()
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: path))
}

render(width: 320, height: 180, to: "app/src/main/res/drawable-xhdpi/banner.png")
render(width: 1280, height: 720, to: "docs/play/tv-banner-1280x720.png")
render(width: 1024, height: 500, to: "docs/play/feature-graphic-1024x500.png")
render(width: 512, height: 512, iconOnly: true, to: "docs/play/icon-512.png")
