import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

guard CommandLine.arguments.count == 3 else {
  fputs("usage: swift build_app_icon.swift <android-foreground.png> <output.png>\n", stderr)
  exit(2)
}

let sourceURL = URL(fileURLWithPath: CommandLine.arguments[1])
let outputURL = URL(fileURLWithPath: CommandLine.arguments[2])
guard let foreground = NSImage(contentsOf: sourceURL) else {
  fputs("cannot load Android foreground: \(sourceURL.path)\n", stderr)
  exit(1)
}
guard let foregroundImage = foreground.cgImage(forProposedRect: nil, context: nil, hints: nil)
else {
  fputs("cannot decode Android foreground\n", stderr)
  exit(1)
}
let colorSpace = CGColorSpaceCreateDeviceRGB()
guard
  let context = CGContext(
    data: nil,
    width: 1024,
    height: 1024,
    bitsPerComponent: 8,
    bytesPerRow: 1024 * 4,
    space: colorSpace,
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
  )
else {
  fputs("cannot create icon graphics context\n", stderr)
  exit(1)
}
let bounds = CGRect(x: 0, y: 0, width: 1024, height: 1024)
context.setFillColor(red: 0x33 / 255, green: 0x5C / 255, blue: 0x67 / 255, alpha: 1)
context.fill(bounds)
context.interpolationQuality = .high
context.draw(foregroundImage, in: bounds)
guard let icon = context.makeImage() else {
  fputs("cannot render icon image\n", stderr)
  exit(1)
}
try FileManager.default.createDirectory(
  at: outputURL.deletingLastPathComponent(),
  withIntermediateDirectories: true
)
guard
  let destination = CGImageDestinationCreateWithURL(
    outputURL as CFURL,
    UTType.png.identifier as CFString,
    1,
    nil
  )
else {
  fputs("cannot create icon output\n", stderr)
  exit(1)
}
CGImageDestinationAddImage(destination, icon, nil)
guard CGImageDestinationFinalize(destination) else {
  fputs("cannot encode icon PNG\n", stderr)
  exit(1)
}
