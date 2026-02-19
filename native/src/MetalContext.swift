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
private let kBufferUsageStatic: Int32 = 0
private let kBufferUsageDynamic: Int32 = 1
private let kDynamicBufferSlotCount: Int = 3
private let kDynamicBufferAlignment: Int = 256
private let kUploadStagingInitialSize: Int = 4 * 1024 * 1024
private let kPackedVertexDescriptorIntsPerAttribute: Int = 7
private let kVertexUsagePosition: Int32 = 0
private let kVertexUsageNormal: Int32 = 1
private let kVertexUsageColor: Int32 = 2
private let kVertexUsageUV: Int32 = 3
private let kVertexUsageGeneric: Int32 = 4
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

private struct NativeBufferRecord {
    var usage: Int32
    var size: Int
    var slotSize: Int
    var slotCount: Int
    var lastWriteOffset: Int
    let metalBuffer: MTLBuffer
}

private struct NativeVertexDescriptorElement {
    let attributeIndex: Int32
    let usage: Int32
    let componentType: Int32
    let componentCount: Int32
    let offset: Int32
    let normalized: Int32
    let uvIndex: Int32
}

private struct NativeVertexDescriptorRecord {
    var stride: Int
    var elements: [NativeVertexDescriptorElement]
    var descriptor: MTLVertexDescriptor
}

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
    var nextBufferHandle: Int64 = 1
    var nativeBuffers: [Int64: NativeBufferRecord] = [:]
    var nextVertexDescriptorHandle: Int64 = 1
    var nativeVertexDescriptors: [Int64: NativeVertexDescriptorRecord] = [:]
    var frameSerial: UInt64 = 0
    var uploadStagingBuffer: MTLBuffer?
    var uploadStagingCapacity: Int = 0
    var uploadStagingWriteOffset: Int = 0
    var uploadStagingFrame: UInt64 = 0

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

private func alignUp(_ value: Int, alignment: Int) -> Int {
    let validAlignment = max(alignment, 1)
    let remainder = value % validAlignment
    if remainder == 0 {
        return value
    }
    return value + (validAlignment - remainder)
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

private func withContextStateValue<T>(
    _ fallback: T,
    _ body: (MetalContextState) -> T
) -> T {
    stateLock.lock()
    defer { stateLock.unlock() }

    guard let context = contextState else {
        return fallback
    }
    return body(context)
}

private func ensureUploadStagingBuffer(
    context: MetalContextState,
    minimumCapacity: Int
) -> MTLBuffer? {
    let requestedCapacity = max(minimumCapacity, kUploadStagingInitialSize)
    let alignedCapacity = alignUp(requestedCapacity, alignment: kDynamicBufferAlignment)
    if let existing = context.uploadStagingBuffer, context.uploadStagingCapacity >= alignedCapacity {
        return existing
    }

    guard let stagingBuffer = context.device.makeBuffer(length: alignedCapacity, options: .storageModeShared) else {
        return nil
    }
    if (context.debugFlags & kDebugFlagLabels) != 0 {
        stagingBuffer.label = "MCMetal Upload Staging Ring"
    }

    context.uploadStagingBuffer = stagingBuffer
    context.uploadStagingCapacity = alignedCapacity
    context.uploadStagingWriteOffset = 0
    context.uploadStagingFrame = context.frameSerial
    return stagingBuffer
}

private func reserveUploadStagingRange(
    context: MetalContextState,
    byteCount: Int
) -> (buffer: MTLBuffer, offset: Int)? {
    if byteCount <= 0 {
        return nil
    }

    let alignedLength = alignUp(byteCount, alignment: kDynamicBufferAlignment)
    if context.uploadStagingFrame != context.frameSerial {
        context.uploadStagingFrame = context.frameSerial
        context.uploadStagingWriteOffset = 0
    }

    guard let stagingBuffer = ensureUploadStagingBuffer(
        context: context,
        minimumCapacity: alignedLength
    ) else {
        return nil
    }

    if context.uploadStagingWriteOffset + alignedLength > context.uploadStagingCapacity {
        context.uploadStagingWriteOffset = 0
        context.uploadStagingFrame = context.frameSerial
    }

    let rangeOffset = context.uploadStagingWriteOffset
    context.uploadStagingWriteOffset += alignedLength
    return (stagingBuffer, rangeOffset)
}

private func markFrameSubmitted() {
    stateLock.lock()
    defer { stateLock.unlock() }
    guard let context = contextState else {
        return
    }
    context.frameSerial &+= 1
}

private func shouldLogStateTransitions(_ context: MetalContextState) -> Bool {
    return (context.debugFlags & kDebugFlagLabels) != 0 || (context.debugFlags & kDebugFlagValidation) != 0
}

private func logStateTransition(
    context: MetalContextState,
    operation: String,
    changed: Bool
) {
    guard changed, shouldLogStateTransitions(context) else {
        return
    }

    let pipelineKey = context.renderStateTracker.makePipelineKey(
        colorPixelFormat: Int32(bitPattern: UInt32(context.layer.pixelFormat.rawValue)),
        sampleCount: 1,
        primitiveType: 0x0004
    )
    let depthStencilKey = context.renderStateTracker.makeDepthStencilKey()

    NSLog(
        "event=metal_phase2 phase=state_transition operation=%@ revision=%llu pipeline_key_hash=%016llx depth_stencil_key_hash=%016llx",
        operation,
        context.renderStateTracker.revision,
        pipelineKey.stableHash,
        depthStencilKey.stableHash
    )
}

private func validateSnapshotForEncoder(_ snapshot: RenderStateSnapshot) -> Bool {
    if snapshot.viewport.width <= 0 || snapshot.viewport.height <= 0 {
        assertionFailure("Viewport dimensions must be positive before encoder setup.")
        return false
    }

    if snapshot.viewport.minDepth > snapshot.viewport.maxDepth {
        assertionFailure("Viewport depth range is invalid (minDepth > maxDepth).")
        return false
    }

    if snapshot.scissor.enabled && (snapshot.scissor.width <= 0 || snapshot.scissor.height <= 0) {
        assertionFailure("Scissor dimensions must be positive when enabled.")
        return false
    }

    return true
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

private func mapPrimitiveType(_ glMode: Int32) -> MTLPrimitiveType? {
    switch glMode {
    case 0x0000:
        return .point
    case 0x0001:
        return .line
    case 0x0003:
        return .lineStrip
    case 0x0004:
        return .triangle
    case 0x0005:
        return .triangleStrip
    default:
        return nil
    }
}

private func mapVertexElementFormat(
    componentType: Int32,
    componentCount: Int32,
    normalized: Bool
) -> MTLVertexFormat? {
    switch (componentType, componentCount, normalized) {
    case (0x1406, 1, _):
        return .float
    case (0x1406, 2, _):
        return .float2
    case (0x1406, 3, _):
        return .float3
    case (0x1406, 4, _):
        return .float4

    case (0x1401, 2, false):
        return .uchar2
    case (0x1401, 3, false):
        return .uchar3
    case (0x1401, 4, false):
        return .uchar4
    case (0x1401, 2, true):
        return .uchar2Normalized
    case (0x1401, 3, true):
        return .uchar3Normalized
    case (0x1401, 4, true):
        return .uchar4Normalized

    case (0x1400, 2, false):
        return .char2
    case (0x1400, 3, false):
        return .char3
    case (0x1400, 4, false):
        return .char4
    case (0x1400, 2, true):
        return .char2Normalized
    case (0x1400, 3, true):
        return .char3Normalized
    case (0x1400, 4, true):
        return .char4Normalized

    case (0x1403, 2, false):
        return .ushort2
    case (0x1403, 3, false):
        return .ushort3
    case (0x1403, 4, false):
        return .ushort4
    case (0x1403, 2, true):
        return .ushort2Normalized
    case (0x1403, 3, true):
        return .ushort3Normalized
    case (0x1403, 4, true):
        return .ushort4Normalized

    case (0x1402, 2, false):
        return .short2
    case (0x1402, 3, false):
        return .short3
    case (0x1402, 4, false):
        return .short4
    case (0x1402, 2, true):
        return .short2Normalized
    case (0x1402, 3, true):
        return .short3Normalized
    case (0x1402, 4, true):
        return .short4Normalized

    case (0x1405, 1, _):
        return .uint
    case (0x1405, 2, _):
        return .uint2
    case (0x1405, 3, _):
        return .uint3
    case (0x1405, 4, _):
        return .uint4

    case (0x1404, 1, _):
        return .int
    case (0x1404, 2, _):
        return .int2
    case (0x1404, 3, _):
        return .int3
    case (0x1404, 4, _):
        return .int4
    default:
        return nil
    }
}

private func createPipelineState(context: MetalContextState, key: PipelineKey) -> MTLRenderPipelineState? {
    if let cachedPipeline = context.pipelineCache[key] {
        return cachedPipeline
    }

    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = context.vertexFunction
    descriptor.fragmentFunction = context.fragmentFunction
    descriptor.rasterSampleCount = Int(max(key.sampleCount, 1))

    guard let colorAttachment = descriptor.colorAttachments[0] else {
        return nil
    }

    let pixelFormatRawValue = UInt32(bitPattern: key.colorPixelFormat)
    guard let colorPixelFormat = MTLPixelFormat(rawValue: UInt(pixelFormatRawValue)) else {
        return nil
    }
    colorAttachment.pixelFormat = colorPixelFormat

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
    if !validateSnapshotForEncoder(snapshot) {
        return kStatusInvalidArgument
    }

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
        markFrameSubmitted()
        return kStatusOk
    }

    return renderStatus
}

@_cdecl("mcmetal_swift_set_blend_enabled")
public func mcmetal_swift_set_blend_enabled(_ enabled: Int32) -> Int32 {
    return withContextState { context in
        let changed = context.renderStateTracker.setBlendEnabled(enabled != 0)
        logStateTransition(context: context, operation: "set_blend_enabled", changed: changed)
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
        let changed = context.renderStateTracker.setBlendFunc(
            srcRGB: srcRGB,
            dstRGB: dstRGB,
            srcAlpha: srcAlpha,
            dstAlpha: dstAlpha
        )
        logStateTransition(context: context, operation: "set_blend_func", changed: changed)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_blend_equation")
public func mcmetal_swift_set_blend_equation(
    _ rgbEquation: Int32,
    _ alphaEquation: Int32
) -> Int32 {
    return withContextState { context in
        let changed = context.renderStateTracker.setBlendEquation(rgb: rgbEquation, alpha: alphaEquation)
        logStateTransition(context: context, operation: "set_blend_equation", changed: changed)
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
        let changed = context.renderStateTracker.setDepthState(
            testEnabled: depthTestEnabled != 0,
            writeEnabled: depthWriteEnabled != 0,
            compareFunction: depthCompareFunction
        )
        logStateTransition(context: context, operation: "set_depth_state", changed: changed)
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
        let changed = context.renderStateTracker.setStencilState(
            enabled: stencilEnabled != 0,
            function: stencilFunction,
            reference: stencilReference,
            compareMask: stencilCompareMask,
            writeMask: stencilWriteMask,
            sfail: stencilSFail,
            dpfail: stencilDpFail,
            dppass: stencilDpPass
        )
        logStateTransition(context: context, operation: "set_stencil_state", changed: changed)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_set_cull_state")
public func mcmetal_swift_set_cull_state(
    _ cullEnabled: Int32,
    _ cullMode: Int32
) -> Int32 {
    return withContextState { context in
        let changed = context.renderStateTracker.setCullState(enabled: cullEnabled != 0, mode: cullMode)
        logStateTransition(context: context, operation: "set_cull_state", changed: changed)
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
        assertionFailure("Scissor dimensions must be positive when enabled.")
        return kStatusInvalidArgument
    }

    return withContextState { context in
        let changed = context.renderStateTracker.setScissor(
            enabled: scissorEnabled != 0,
            x: x,
            y: y,
            width: width,
            height: height
        )
        logStateTransition(context: context, operation: "set_scissor_state", changed: changed)
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
        assertionFailure("Viewport dimensions must be positive.")
        return kStatusInvalidArgument
    }
    if minDepth > maxDepth {
        assertionFailure("Viewport depth range is invalid (minDepth > maxDepth).")
        return kStatusInvalidArgument
    }

    return withContextState { context in
        let changed = context.renderStateTracker.setViewport(
            x: x,
            y: y,
            width: width,
            height: height,
            minDepth: minDepth,
            maxDepth: maxDepth
        )
        logStateTransition(context: context, operation: "set_viewport_state", changed: changed)
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_draw")
public func mcmetal_swift_draw(
    _ mode: Int32,
    _ first: Int32,
    _ count: Int32
) -> Int32 {
    if first < 0 || count <= 0 {
        assertionFailure("Draw range must be valid.")
        return kStatusInvalidArgument
    }

    guard let primitiveType = mapPrimitiveType(mode) else {
        assertionFailure("Unsupported GL primitive mode.")
        return kStatusInvalidArgument
    }

    stateLock.lock()
    guard let context = contextState else {
        stateLock.unlock()
        return kStatusInitializationFailed
    }
    let layer = context.layer
    let commandQueue = context.commandQueue
    let debugFlags = context.debugFlags
    stateLock.unlock()

    let drawStatus: Int32 = autoreleasepool {
        guard let drawable = layer.nextDrawable() else {
            return kStatusInitializationFailed
        }

        let renderPass = MTLRenderPassDescriptor()
        guard let colorAttachment = renderPass.colorAttachments[0] else {
            return kStatusInitializationFailed
        }
        colorAttachment.texture = drawable.texture
        colorAttachment.loadAction = .load
        colorAttachment.storeAction = .store

        guard let commandBuffer = commandQueue.makeCommandBuffer() else {
            return kStatusInitializationFailed
        }
        if (debugFlags & kDebugFlagLabels) != 0 {
            commandBuffer.label = "MCMetal Draw Command Buffer"
        }

        guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPass) else {
            return kStatusInitializationFailed
        }

        let setupStatus = configureEncoderState(context: context, encoder: encoder, primitiveType: mode)
        if setupStatus != kStatusOk {
            encoder.endEncoding()
            return setupStatus
        }

        if (debugFlags & kDebugFlagLabels) != 0 {
            encoder.label = "MCMetal Draw Encoder"
            encoder.pushDebugGroup("MCMetal Draw (Non-Indexed)")
        }

        encoder.drawPrimitives(
            type: primitiveType,
            vertexStart: Int(first),
            vertexCount: max(Int(count), 1)
        )

        if (debugFlags & kDebugFlagLabels) != 0 {
            encoder.popDebugGroup()
        }

        encoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()
        markFrameSubmitted()
        return kStatusOk
    }

    return drawStatus
}

@_cdecl("mcmetal_swift_draw_indexed")
public func mcmetal_swift_draw_indexed(
    _ mode: Int32,
    _ count: Int32,
    _ indexType: Int32
) -> Int32 {
    if count <= 0 {
        assertionFailure("Draw count must be positive.")
        return kStatusInvalidArgument
    }

    if indexType != 0x1401 && indexType != 0x1403 && indexType != 0x1405 {
        assertionFailure("Unsupported GL index type.")
        return kStatusInvalidArgument
    }

    guard let primitiveType = mapPrimitiveType(mode) else {
        assertionFailure("Unsupported GL primitive mode.")
        return kStatusInvalidArgument
    }

    stateLock.lock()
    guard let context = contextState else {
        stateLock.unlock()
        return kStatusInitializationFailed
    }
    let layer = context.layer
    let commandQueue = context.commandQueue
    let debugFlags = context.debugFlags
    stateLock.unlock()

    let drawStatus: Int32 = autoreleasepool {
        guard let drawable = layer.nextDrawable() else {
            return kStatusInitializationFailed
        }

        let renderPass = MTLRenderPassDescriptor()
        guard let colorAttachment = renderPass.colorAttachments[0] else {
            return kStatusInitializationFailed
        }
        colorAttachment.texture = drawable.texture
        colorAttachment.loadAction = .load
        colorAttachment.storeAction = .store

        guard let commandBuffer = commandQueue.makeCommandBuffer() else {
            return kStatusInitializationFailed
        }

        if (debugFlags & kDebugFlagLabels) != 0 {
            commandBuffer.label = "MCMetal Draw Command Buffer"
        }

        guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: renderPass) else {
            return kStatusInitializationFailed
        }

        let setupStatus = configureEncoderState(context: context, encoder: encoder, primitiveType: mode)
        if setupStatus != kStatusOk {
            encoder.endEncoding()
            return setupStatus
        }

        if (debugFlags & kDebugFlagLabels) != 0 {
            encoder.label = "MCMetal Draw Encoder"
            encoder.pushDebugGroup("MCMetal Draw Indexed (Simple)")
        }

        encoder.drawPrimitives(
            type: primitiveType,
            vertexStart: 0,
            vertexCount: max(Int(count), 1)
        )

        if (debugFlags & kDebugFlagLabels) != 0 {
            encoder.popDebugGroup()
        }

        encoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()
        markFrameSubmitted()
        return kStatusOk
    }

    return drawStatus
}

private func createNativeBufferRecord(
    context: MetalContextState,
    usage: Int32,
    size: Int32,
    initialData: UnsafeRawPointer?,
    initialDataLength: Int32
) -> NativeBufferRecord? {
    if size <= 0 || initialDataLength < 0 || initialDataLength > size {
        return nil
    }
    if initialDataLength > 0 && initialData == nil {
        return nil
    }
    if usage != kBufferUsageStatic && usage != kBufferUsageDynamic {
        return nil
    }

    let logicalSize = Int(size)
    let dynamicUsage = usage == kBufferUsageDynamic
    let slotCount = dynamicUsage ? kDynamicBufferSlotCount : 1
    let slotSize = dynamicUsage
        ? alignUp(logicalSize, alignment: kDynamicBufferAlignment)
        : logicalSize
    let allocationLength = slotSize * slotCount

    guard let metalBuffer = context.device.makeBuffer(length: allocationLength, options: .storageModeShared) else {
        return nil
    }
    if (context.debugFlags & kDebugFlagLabels) != 0 {
        metalBuffer.label = dynamicUsage ? "MCMetal Dynamic Buffer" : "MCMetal Static Buffer"
    }

    if let initialData, initialDataLength > 0 {
        for slot in 0..<slotCount {
            let destination = metalBuffer.contents().advanced(by: slot * slotSize)
            destination.copyMemory(from: initialData, byteCount: Int(initialDataLength))
            if !dynamicUsage {
                break
            }
        }
    }

    return NativeBufferRecord(
        usage: usage,
        size: logicalSize,
        slotSize: slotSize,
        slotCount: slotCount,
        lastWriteOffset: 0,
        metalBuffer: metalBuffer
    )
}

@_cdecl("mcmetal_swift_create_buffer")
public func mcmetal_swift_create_buffer(
    _ usage: Int32,
    _ size: Int32,
    _ initialData: UnsafeRawPointer?,
    _ initialDataLength: Int32
) -> Int64 {
    if size <= 0 || (usage != kBufferUsageStatic && usage != kBufferUsageDynamic) {
        assertionFailure("Buffer size must be positive.")
        return 0
    }

    return withContextStateValue(0) { context in
        guard let record = createNativeBufferRecord(
            context: context,
            usage: usage,
            size: size,
            initialData: initialData,
            initialDataLength: initialDataLength
        ) else {
            return 0
        }

        let handle = context.nextBufferHandle
        context.nextBufferHandle = handle &+ 1
        context.nativeBuffers[handle] = record
        return handle
    }
}

@_cdecl("mcmetal_swift_update_buffer")
public func mcmetal_swift_update_buffer(
    _ handle: Int64,
    _ offset: Int32,
    _ data: UnsafeRawPointer?,
    _ dataLength: Int32
) -> Int32 {
    if handle <= 0 || offset < 0 || dataLength < 0 {
        assertionFailure("Invalid buffer update arguments.")
        return kStatusInvalidArgument
    }
    if dataLength > 0 && data == nil {
        assertionFailure("Expected non-null update data for non-empty update.")
        return kStatusInvalidArgument
    }

    return withContextState { context in
        guard var record = context.nativeBuffers[handle] else {
            return kStatusInvalidArgument
        }

        let updateOffset = Int(offset)
        let updateLength = Int(dataLength)
        if updateOffset + updateLength > record.size {
            assertionFailure("Buffer update range exceeds buffer size.")
            return kStatusInvalidArgument
        }

        let slotBaseOffset: Int
        if record.usage == kBufferUsageDynamic {
            let slot = Int(context.frameSerial % UInt64(max(record.slotCount, 1)))
            slotBaseOffset = slot * record.slotSize
        } else {
            slotBaseOffset = 0
        }
        let destinationOffset = slotBaseOffset + updateOffset
        if destinationOffset + updateLength > record.metalBuffer.length {
            assertionFailure("Buffer update writes beyond allocated Metal buffer.")
            return kStatusInvalidArgument
        }

        if let data, updateLength > 0 {
            guard let stagingRange = reserveUploadStagingRange(context: context, byteCount: updateLength) else {
                return kStatusInitializationFailed
            }
            let stagingPointer = stagingRange.buffer.contents().advanced(by: stagingRange.offset)
            stagingPointer.copyMemory(from: data, byteCount: updateLength)
            let destination = record.metalBuffer.contents().advanced(by: destinationOffset)
            destination.copyMemory(from: stagingPointer, byteCount: updateLength)
        }
        record.lastWriteOffset = slotBaseOffset

        context.nativeBuffers[handle] = record
        return kStatusOk
    }
}

@_cdecl("mcmetal_swift_destroy_buffer")
public func mcmetal_swift_destroy_buffer(_ handle: Int64) -> Int32 {
    if handle <= 0 {
        return kStatusInvalidArgument
    }

    return withContextState { context in
        guard context.nativeBuffers.removeValue(forKey: handle) != nil else {
            return kStatusInvalidArgument
        }
        return kStatusOk
    }
}

private func parseVertexDescriptorElements(
    attributeCount: Int32,
    packedElements: UnsafePointer<Int32>,
    packedIntCount: Int32
) -> [NativeVertexDescriptorElement]? {
    let elementCount = Int(attributeCount)
    let expectedIntCount = elementCount * kPackedVertexDescriptorIntsPerAttribute
    if elementCount <= 0 || packedIntCount < expectedIntCount {
        return nil
    }

    var elements: [NativeVertexDescriptorElement] = []
    elements.reserveCapacity(elementCount)
    for i in 0..<elementCount {
        let base = i * kPackedVertexDescriptorIntsPerAttribute
        let usage = packedElements[base + 1]
        if usage != kVertexUsagePosition
            && usage != kVertexUsageNormal
            && usage != kVertexUsageColor
            && usage != kVertexUsageUV
            && usage != kVertexUsageGeneric {
            return nil
        }

        elements.append(
            NativeVertexDescriptorElement(
                attributeIndex: packedElements[base + 0],
                usage: usage,
                componentType: packedElements[base + 2],
                componentCount: packedElements[base + 3],
                offset: packedElements[base + 4],
                normalized: packedElements[base + 5],
                uvIndex: packedElements[base + 6]
            )
        )
    }

    return elements
}

@_cdecl("mcmetal_swift_register_vertex_descriptor")
public func mcmetal_swift_register_vertex_descriptor(
    _ strideBytes: Int32,
    _ attributeCount: Int32,
    _ packedElements: UnsafePointer<Int32>?,
    _ packedIntCount: Int32
) -> Int64 {
    if strideBytes <= 0 || attributeCount <= 0 || packedIntCount <= 0 || packedElements == nil {
        assertionFailure("Invalid vertex descriptor registration arguments.")
        return 0
    }

    return withContextStateValue(0) { context in
        guard let packedElements else {
            return 0
        }
        guard let elements = parseVertexDescriptorElements(
            attributeCount: attributeCount,
            packedElements: packedElements,
            packedIntCount: packedIntCount
        ) else {
            return 0
        }

        let descriptor = MTLVertexDescriptor()
        for element in elements {
            if element.attributeIndex < 0 {
                return 0
            }
            let index = Int(element.attributeIndex)
            guard let attributeDescriptor = descriptor.attributes[index] else {
                return 0
            }
            guard let format = mapVertexElementFormat(
                componentType: element.componentType,
                componentCount: element.componentCount,
                normalized: element.normalized != 0
            ) else {
                return 0
            }

            attributeDescriptor.format = format
            attributeDescriptor.offset = Int(element.offset)
            attributeDescriptor.bufferIndex = 0
        }

        guard let layoutDescriptor = descriptor.layouts[0] else {
            return 0
        }
        layoutDescriptor.stride = Int(strideBytes)
        layoutDescriptor.stepFunction = .perVertex
        layoutDescriptor.stepRate = 1

        let handle = context.nextVertexDescriptorHandle
        context.nextVertexDescriptorHandle = handle &+ 1
        context.nativeVertexDescriptors[handle] = NativeVertexDescriptorRecord(
            stride: Int(strideBytes),
            elements: elements,
            descriptor: descriptor
        )
        return handle
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
