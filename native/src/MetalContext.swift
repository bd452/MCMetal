import AppKit
import Foundation
import Metal
import QuartzCore
import Darwin

private let kStatusOk: Int32 = 0
private let kStatusAlreadyInitialized: Int32 = 1
private let kStatusInvalidArgument: Int32 = 2
private let kStatusInitializationFailed: Int32 = 3

private let kDebugFlagValidation: Int32 = 1 << 0
private let kDebugFlagLabels: Int32 = 1 << 1

private final class MetalContextState {
    let window: NSWindow
    let contentView: NSView
    let layer: CAMetalLayer
    let device: MTLDevice
    let commandQueue: MTLCommandQueue
    let debugFlags: Int32

    var width: Int32
    var height: Int32
    var scaleFactor: CGFloat
    var fullscreen: Bool

    init(
        window: NSWindow,
        contentView: NSView,
        layer: CAMetalLayer,
        device: MTLDevice,
        commandQueue: MTLCommandQueue,
        width: Int32,
        height: Int32,
        scaleFactor: CGFloat,
        fullscreen: Bool,
        debugFlags: Int32
    ) {
        self.window = window
        self.contentView = contentView
        self.layer = layer
        self.device = device
        self.commandQueue = commandQueue
        self.width = width
        self.height = height
        self.scaleFactor = scaleFactor
        self.fullscreen = fullscreen
        self.debugFlags = debugFlags
    }
}

private let stateLock = NSLock()
private var contextState: MetalContextState?

private func runOnMainThread<T>(_ body: () -> T) -> T {
    if Thread.isMainThread {
        return body()
    }
    return DispatchQueue.main.sync(execute: body)
}

private func clampDimension(_ value: Int32) -> Int32 {
    return max(value, 1)
}

private func clampScale(_ value: Float) -> CGFloat {
    if value > 0 {
        return CGFloat(value)
    }
    return 1.0
}

private func clampColor(_ value: Float) -> Double {
    return min(max(Double(value), 0.0), 1.0)
}

private func applyValidationEnvironment(debugFlags: Int32) {
    if (debugFlags & kDebugFlagValidation) == 0 {
        return
    }

    _ = setenv("MTL_DEBUG_LAYER", "1", 0)
    _ = setenv("METAL_DEVICE_WRAPPER_TYPE", "1", 0)
}

private func applyLayerGeometry(_ context: MetalContextState) {
    context.layer.frame = context.contentView.bounds
    context.layer.contentsScale = context.scaleFactor
    context.layer.drawableSize = CGSize(
        width: Int(clampDimension(context.width)),
        height: Int(clampDimension(context.height))
    )
}

private func applyDebugLabels(_ context: MetalContextState) {
    if (context.debugFlags & kDebugFlagLabels) == 0 {
        return
    }

    context.layer.name = "MCMetal Main Layer"
    context.commandQueue.label = "MCMetal Main Queue"
}

@_cdecl("mcmetal_swift_initialize")
public func mcmetal_swift_initialize(
    _ cocoaWindowHandle: Int64,
    _ width: Int32,
    _ height: Int32,
    _ debugFlags: Int32
) -> Int32 {
    stateLock.lock()
    defer { stateLock.unlock() }

    if contextState != nil {
        return kStatusAlreadyInitialized
    }

    if cocoaWindowHandle == 0 || width <= 0 || height <= 0 {
        return kStatusInvalidArgument
    }

    applyValidationEnvironment(debugFlags: debugFlags)

    let initializedState: MetalContextState? = runOnMainThread {
        guard let windowRawPointer = UnsafeRawPointer(bitPattern: UInt(cocoaWindowHandle)) else {
            return nil
        }

        let window = Unmanaged<NSWindow>.fromOpaque(windowRawPointer).takeUnretainedValue()
        guard let contentView = window.contentView else {
            return nil
        }

        guard let device = MTLCreateSystemDefaultDevice() else {
            return nil
        }

        guard let commandQueue = device.makeCommandQueue() else {
            return nil
        }

        let layer = CAMetalLayer()
        layer.device = device
        layer.pixelFormat = .bgra8Unorm
        layer.framebufferOnly = true
        layer.isOpaque = true
        layer.presentsWithTransaction = false
        layer.allowsNextDrawableTimeout = false
        layer.displaySyncEnabled = true

        contentView.wantsLayer = true
        contentView.layer = layer

        let windowScaleFactor = window.backingScaleFactor > 0 ? window.backingScaleFactor : 1.0

        let state = MetalContextState(
            window: window,
            contentView: contentView,
            layer: layer,
            device: device,
            commandQueue: commandQueue,
            width: clampDimension(width),
            height: clampDimension(height),
            scaleFactor: CGFloat(windowScaleFactor),
            fullscreen: window.styleMask.contains(.fullScreen),
            debugFlags: debugFlags
        )
        applyLayerGeometry(state)
        applyDebugLabels(state)
        return state
    }

    guard let initializedState else {
        return kStatusInitializationFailed
    }

    contextState = initializedState
    return kStatusOk
}

@_cdecl("mcmetal_swift_resize")
public func mcmetal_swift_resize(
    _ width: Int32,
    _ height: Int32,
    _ scaleFactor: Float,
    _ fullscreen: Int32
) -> Int32 {
    stateLock.lock()
    defer { stateLock.unlock() }

    guard let context = contextState else {
        return kStatusInitializationFailed
    }

    if width <= 0 || height <= 0 {
        return kStatusInvalidArgument
    }

    context.width = clampDimension(width)
    context.height = clampDimension(height)
    context.scaleFactor = clampScale(scaleFactor)
    context.fullscreen = fullscreen != 0

    runOnMainThread {
        applyLayerGeometry(context)
    }

    return kStatusOk
}

@_cdecl("mcmetal_swift_render_demo_frame")
public func mcmetal_swift_render_demo_frame(
    _ red: Float,
    _ green: Float,
    _ blue: Float,
    _ alpha: Float
) -> Int32 {
    stateLock.lock()
    guard let context = contextState else {
        stateLock.unlock()
        return kStatusInitializationFailed
    }
    let layer = context.layer
    let commandQueue = context.commandQueue
    let debugFlags = context.debugFlags
    stateLock.unlock()

    let renderStatus: Int32 = autoreleasepool {
        guard let drawable = layer.nextDrawable() else {
            return kStatusInitializationFailed
        }

        let renderPass = MTLRenderPassDescriptor()
        guard let colorAttachment = renderPass.colorAttachments[0] else {
            return kStatusInitializationFailed
        }
        colorAttachment.texture = drawable.texture
        colorAttachment.loadAction = .clear
        colorAttachment.storeAction = .store
        colorAttachment.clearColor = MTLClearColor(
            red: clampColor(red),
            green: clampColor(green),
            blue: clampColor(blue),
            alpha: clampColor(alpha)
        )

        guard let commandBuffer = commandQueue.makeCommandBuffer() else {
            return kStatusInitializationFailed
        }

        if (debugFlags & kDebugFlagLabels) != 0 {
            commandBuffer.label = "MCMetal Demo Command Buffer"
        }

        guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPass) else {
            return kStatusInitializationFailed
        }

        if (debugFlags & kDebugFlagLabels) != 0 {
            encoder.label = "MCMetal Demo Clear Encoder"
            encoder.pushDebugGroup("MCMetal Demo Clear")
            encoder.popDebugGroup()
        }

        encoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()
        return kStatusOk
    }

    return renderStatus
}

@_cdecl("mcmetal_swift_shutdown")
public func mcmetal_swift_shutdown() {
    stateLock.lock()
    let context = contextState
    contextState = nil
    stateLock.unlock()

    guard let context else {
        return
    }

    runOnMainThread {
        if context.contentView.layer === context.layer {
            context.contentView.layer = nil
        }
    }
}
