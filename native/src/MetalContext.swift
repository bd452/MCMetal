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
private let kDemoVertexFunctionName = "mcmetal_vertex_main"
private let kDemoFragmentFunctionName = "mcmetal_fragment_main"
private let kDemoShaderSource = """
#include <metal_stdlib>
using namespace metal;

struct VertexOut {
    float4 position [[position]];
};

vertex VertexOut mcmetal_vertex_main(uint vertexId [[vertex_id]]) {
    const float2 positions[3] = {
        float2(-0.75, -0.75),
        float2(0.0, 0.75),
        float2(0.75, -0.75)
    };

    VertexOut out;
    out.position = float4(positions[vertexId % 3], 0.0, 1.0);
    return out;
}

fragment float4 mcmetal_fragment_main() {
    return float4(0.9, 0.9, 0.95, 1.0);
}
"""

private final class MetalContextState {
    let window: NSWindow
    let contentView: NSView
    let layer: CAMetalLayer
    let device: MTLDevice
    let commandQueue: MTLCommandQueue
    let shaderLibrary: MTLLibrary
    let vertexFunction: MTLFunction
    let fragmentFunction: MTLFunction
    let debugFlags: Int32
    let renderStateTracker: RenderStateTracker

    var width: Int32
    var height: Int32
    var scaleFactor: CGFloat
    var fullscreen: Bool
    var pipelineCache: [PipelineKey: MTLRenderPipelineState] = [:]
    var depthStencilCache: [DepthStencilKey: MTLDepthStencilState] = [:]

    init(
        window: NSWindow,
        contentView: NSView,
        layer: CAMetalLayer,
        device: MTLDevice,
        commandQueue: MTLCommandQueue,
        shaderLibrary: MTLLibrary,
        vertexFunction: MTLFunction,
        fragmentFunction: MTLFunction,
        renderStateTracker: RenderStateTracker,
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
        self.shaderLibrary = shaderLibrary
        self.vertexFunction = vertexFunction
        self.fragmentFunction = fragmentFunction
        self.renderStateTracker = renderStateTracker
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

private func withContextState(_ body: (MetalContextState) -> Int32) -> Int32 {
    stateLock.lock()
    defer { stateLock.unlock() }

    guard let context = contextState else {
        return kStatusInitializationFailed
    }
    return body(context)
}

private func mapBlendFactor(_ glFactor: Int32) -> MTLBlendFactor {
    switch glFactor {
    case 0x0:
        return .zero
    case 0x1:
        return .one
    case 0x0300:
        return .sourceColor
    case 0x0301:
        return .oneMinusSourceColor
    case 0x0302:
        return .sourceAlpha
    case 0x0303:
        return .oneMinusSourceAlpha
    case 0x0304:
        return .destinationAlpha
    case 0x0305:
        return .oneMinusDestinationAlpha
    case 0x0306:
        return .destinationColor
    case 0x0307:
        return .oneMinusDestinationColor
    case 0x0308:
        return .sourceAlphaSaturated
    case 0x8001:
        return .blendColor
    case 0x8002:
        return .oneMinusBlendColor
    case 0x8003:
        return .blendAlpha
    case 0x8004:
        return .oneMinusBlendAlpha
    default:
        return .one
    }
}

private func mapBlendOperation(_ glEquation: Int32) -> MTLBlendOperation {
    switch glEquation {
    case 0x8006:
        return .add
    case 0x800A:
        return .subtract
    case 0x800B:
        return .reverseSubtract
    case 0x8007:
        return .min
    case 0x8008:
        return .max
    default:
        return .add
    }
}

private func mapCompareFunction(_ glCompare: Int32) -> MTLCompareFunction {
    switch glCompare {
    case 0x0200:
        return .never
    case 0x0201:
        return .less
    case 0x0202:
        return .equal
    case 0x0203:
        return .lessEqual
    case 0x0204:
        return .greater
    case 0x0205:
        return .notEqual
    case 0x0206:
        return .greaterEqual
    case 0x0207:
        return .always
    default:
        return .lessEqual
    }
}

private func mapCullMode(_ glCullMode: Int32) -> MTLCullMode {
    switch glCullMode {
    case 0x0404:
        return .front
    case 0x0405:
        return .back
    default:
        return .none
    }
}

private func mapStencilOperation(_ glStencilOperation: Int32) -> MTLStencilOperation {
    switch glStencilOperation {
    case 0x0:
        return .zero
    case 0x1E00:
        return .keep
    case 0x1E01:
        return .replace
    case 0x1E02:
        return .incrementClamp
    case 0x1E03:
        return .decrementClamp
    case 0x150A:
        return .invert
    case 0x8507:
        return .incrementWrap
    case 0x8508:
        return .decrementWrap
    default:
        return .keep
    }
}

private func createPipelineState(context: MetalContextState, key: PipelineKey) -> MTLRenderPipelineState? {
    if let cachedPipeline = context.pipelineCache[key] {
        return cachedPipeline
    }

    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = context.vertexFunction
    descriptor.fragmentFunction = context.fragmentFunction
    descriptor.sampleCount = Int(max(key.sampleCount, 1))

    guard let colorAttachment = descriptor.colorAttachments[0] else {
        return nil
    }

    let pixelFormatRawValue = UInt(max(key.colorPixelFormat, 0))
    colorAttachment.pixelFormat = MTLPixelFormat(rawValue: pixelFormatRawValue)

    if key.blendEnabled {
        colorAttachment.isBlendingEnabled = true
        colorAttachment.sourceRGBBlendFactor = mapBlendFactor(key.blendSrcRGB)
        colorAttachment.destinationRGBBlendFactor = mapBlendFactor(key.blendDstRGB)
        colorAttachment.sourceAlphaBlendFactor = mapBlendFactor(key.blendSrcAlpha)
        colorAttachment.destinationAlphaBlendFactor = mapBlendFactor(key.blendDstAlpha)
        colorAttachment.rgbBlendOperation = mapBlendOperation(key.blendEquationRGB)
        colorAttachment.alphaBlendOperation = mapBlendOperation(key.blendEquationAlpha)
    } else {
        colorAttachment.isBlendingEnabled = false
    }

    guard let pipelineState = try? context.device.makeRenderPipelineState(descriptor: descriptor) else {
        return nil
    }

    context.pipelineCache[key] = pipelineState
    return pipelineState
}

private func createDepthStencilState(context: MetalContextState, key: DepthStencilKey) -> MTLDepthStencilState? {
    if let cachedDepthStencilState = context.depthStencilCache[key] {
        return cachedDepthStencilState
    }

    let descriptor = MTLDepthStencilDescriptor()
    descriptor.depthCompareFunction = key.depthTestEnabled ? mapCompareFunction(key.depthCompareFunction) : .always
    descriptor.isDepthWriteEnabled = key.depthTestEnabled && key.depthWriteEnabled

    if key.stencilEnabled {
        let stencilDescriptor = MTLStencilDescriptor()
        stencilDescriptor.stencilCompareFunction = mapCompareFunction(key.stencilFunction)
        stencilDescriptor.readMask = UInt32(bitPattern: key.stencilCompareMask)
        stencilDescriptor.writeMask = UInt32(bitPattern: key.stencilWriteMask)
        stencilDescriptor.stencilFailureOperation = mapStencilOperation(key.stencilSFail)
        stencilDescriptor.depthFailureOperation = mapStencilOperation(key.stencilDpFail)
        stencilDescriptor.depthStencilPassOperation = mapStencilOperation(key.stencilDpPass)
        descriptor.frontFaceStencil = stencilDescriptor
        descriptor.backFaceStencil = stencilDescriptor
    }

    guard let depthStencilState = context.device.makeDepthStencilState(descriptor: descriptor) else {
        return nil
    }

    context.depthStencilCache[key] = depthStencilState
    return depthStencilState
}

private func configureEncoderState(
    context: MetalContextState,
    encoder: MTLRenderCommandEncoder,
    primitiveType: Int32
) -> Int32 {
    let snapshot = context.renderStateTracker.snapshot
    let pipelineKey = context.renderStateTracker.makePipelineKey(
        colorPixelFormat: Int32(bitPattern: UInt32(context.layer.pixelFormat.rawValue)),
        sampleCount: 1,
        primitiveType: primitiveType
    )
    let depthStencilKey = context.renderStateTracker.makeDepthStencilKey()

    guard let pipelineState = createPipelineState(context: context, key: pipelineKey) else {
        return kStatusInitializationFailed
    }
    guard let depthStencilState = createDepthStencilState(context: context, key: depthStencilKey) else {
        return kStatusInitializationFailed
    }

    encoder.setRenderPipelineState(pipelineState)
    encoder.setDepthStencilState(depthStencilState)

    if snapshot.raster.cullEnabled {
        encoder.setCullMode(mapCullMode(snapshot.raster.cullMode))
    } else {
        encoder.setCullMode(.none)
    }
    encoder.setFrontFacing(.counterClockwise)

    if snapshot.scissor.enabled {
        let scissorRect = MTLScissorRect(
            x: max(Int(snapshot.scissor.x), 0),
            y: max(Int(snapshot.scissor.y), 0),
            width: max(Int(snapshot.scissor.width), 1),
            height: max(Int(snapshot.scissor.height), 1)
        )
        encoder.setScissorRect(scissorRect)
    }

    let viewport = MTLViewport(
        originX: Double(snapshot.viewport.x),
        originY: Double(snapshot.viewport.y),
        width: Double(max(snapshot.viewport.width, 1)),
        height: Double(max(snapshot.viewport.height, 1)),
        znear: Double(snapshot.viewport.minDepth),
        zfar: Double(snapshot.viewport.maxDepth)
    )
    encoder.setViewport(viewport)

    if depthStencilKey.stencilEnabled {
        encoder.setStencilReferenceValue(UInt32(bitPattern: depthStencilKey.stencilReference))
    }

    return kStatusOk
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

        guard let shaderLibrary = try? device.makeLibrary(source: kDemoShaderSource, options: nil) else {
            return nil
        }
        guard let vertexFunction = shaderLibrary.makeFunction(name: kDemoVertexFunctionName) else {
            return nil
        }
        guard let fragmentFunction = shaderLibrary.makeFunction(name: kDemoFragmentFunctionName) else {
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

        let tracker = RenderStateTracker()
        _ = tracker.setViewport(
            x: 0,
            y: 0,
            width: clampDimension(width),
            height: clampDimension(height),
            minDepth: 0.0,
            maxDepth: 1.0
        )

        let state = MetalContextState(
            window: window,
            contentView: contentView,
            layer: layer,
            device: device,
            commandQueue: commandQueue,
            shaderLibrary: shaderLibrary,
            vertexFunction: vertexFunction,
            fragmentFunction: fragmentFunction,
            renderStateTracker: tracker,
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

        let setupStatus = configureEncoderState(context: context, encoder: encoder, primitiveType: 0x0004)
        if setupStatus != kStatusOk {
            encoder.endEncoding()
            return setupStatus
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

@_cdecl("mcmetal_swift_set_blend_enabled")
public func mcmetal_swift_set_blend_enabled(_ enabled: Int32) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setBlendEnabled(enabled != 0)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_blend_func")
public func mcmetal_swift_set_blend_func(
    _ srcRGB: Int32,
    _ dstRGB: Int32,
    _ srcAlpha: Int32,
    _ dstAlpha: Int32
) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setBlendFunc(
            srcRGB: srcRGB,
            dstRGB: dstRGB,
            srcAlpha: srcAlpha,
            dstAlpha: dstAlpha
        )
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_blend_equation")
public func mcmetal_swift_set_blend_equation(
    _ rgbEquation: Int32,
    _ alphaEquation: Int32
) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setBlendEquation(rgb: rgbEquation, alpha: alphaEquation)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_depth_state")
public func mcmetal_swift_set_depth_state(
    _ depthTestEnabled: Int32,
    _ depthWriteEnabled: Int32,
    _ depthCompareFunction: Int32
) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setDepthState(
            testEnabled: depthTestEnabled != 0,
            writeEnabled: depthWriteEnabled != 0,
            compareFunction: depthCompareFunction
        )
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_stencil_state")
public func mcmetal_swift_set_stencil_state(
    _ stencilEnabled: Int32,
    _ stencilFunction: Int32,
    _ stencilReference: Int32,
    _ stencilCompareMask: Int32,
    _ stencilWriteMask: Int32,
    _ stencilSFail: Int32,
    _ stencilDpFail: Int32,
    _ stencilDpPass: Int32
) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setStencilState(
            enabled: stencilEnabled != 0,
            function: stencilFunction,
            reference: stencilReference,
            compareMask: stencilCompareMask,
            writeMask: stencilWriteMask,
            sfail: stencilSFail,
            dpfail: stencilDpFail,
            dppass: stencilDpPass
        )
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_cull_state")
public func mcmetal_swift_set_cull_state(
    _ cullEnabled: Int32,
    _ cullMode: Int32
) -> Int32 {
    return withContextState { context in
        _ = context.renderStateTracker.setCullState(enabled: cullEnabled != 0, mode: cullMode)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_scissor_state")
public func mcmetal_swift_set_scissor_state(
    _ scissorEnabled: Int32,
    _ x: Int32,
    _ y: Int32,
    _ width: Int32,
    _ height: Int32
) -> Int32 {
    if scissorEnabled != 0 && (width <= 0 || height <= 0) {
        return kStatusInvalidArgument
    }

    return withContextState { context in
        _ = context.renderStateTracker.setScissor(
            enabled: scissorEnabled != 0,
            x: x,
            y: y,
            width: width,
            height: height
        )
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_viewport_state")
public func mcmetal_swift_set_viewport_state(
    _ x: Int32,
    _ y: Int32,
    _ width: Int32,
    _ height: Int32,
    _ minDepth: Float,
    _ maxDepth: Float
) -> Int32 {
    if width <= 0 || height <= 0 {
        return kStatusInvalidArgument
    }

    return withContextState { context in
        _ = context.renderStateTracker.setViewport(
            x: x,
            y: y,
            width: width,
            height: height,
            minDepth: minDepth,
            maxDepth: maxDepth
        )
        return kStatusOk
    }
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
