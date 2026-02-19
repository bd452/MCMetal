import Foundation

struct BlendState: Equatable {
    var enabled: Bool = false
    var srcRGB: Int32 = 1
    var dstRGB: Int32 = 0
    var srcAlpha: Int32 = 1
    var dstAlpha: Int32 = 0
    var equationRGB: Int32 = 0x8006
    var equationAlpha: Int32 = 0x8006
}

struct DepthState: Equatable {
    var testEnabled: Bool = false
    var writeEnabled: Bool = true
    var compareFunction: Int32 = 0x0203
}

struct StencilState: Equatable {
    var enabled: Bool = false
    var function: Int32 = 0x0207
    var reference: Int32 = 0
    var compareMask: Int32 = 0xFF
    var writeMask: Int32 = 0xFF
    var sfail: Int32 = 0x1E00
    var dpfail: Int32 = 0x1E00
    var dppass: Int32 = 0x1E00
}

struct RasterState: Equatable {
    var cullEnabled: Bool = true
    var cullMode: Int32 = 0x0405
}

struct ScissorState: Equatable {
    var enabled: Bool = false
    var x: Int32 = 0
    var y: Int32 = 0
    var width: Int32 = 1
    var height: Int32 = 1
}

struct ViewportState: Equatable {
    var x: Int32 = 0
    var y: Int32 = 0
    var width: Int32 = 1
    var height: Int32 = 1
    var minDepth: Float = 0.0
    var maxDepth: Float = 1.0
}

struct RenderStateSnapshot: Equatable {
    var blend = BlendState()
    var depth = DepthState()
    var stencil = StencilState()
    var raster = RasterState()
    var scissor = ScissorState()
    var viewport = ViewportState()

    func makePipelineKey(colorPixelFormat: Int32, sampleCount: Int32, primitiveType: Int32) -> PipelineKey {
        return PipelineKey(
            blendEnabled: blend.enabled,
            blendSrcRGB: blend.srcRGB,
            blendDstRGB: blend.dstRGB,
            blendSrcAlpha: blend.srcAlpha,
            blendDstAlpha: blend.dstAlpha,
            blendEquationRGB: blend.equationRGB,
            blendEquationAlpha: blend.equationAlpha,
            cullEnabled: raster.cullEnabled,
            cullMode: raster.cullMode,
            colorPixelFormat: colorPixelFormat,
            sampleCount: max(sampleCount, 1),
            primitiveType: primitiveType
        )
    }

    func makeDepthStencilKey() -> DepthStencilKey {
        return DepthStencilKey(
            depthTestEnabled: depth.testEnabled,
            depthWriteEnabled: depth.writeEnabled,
            depthCompareFunction: depth.compareFunction,
            stencilEnabled: stencil.enabled,
            stencilFunction: stencil.function,
            stencilReference: stencil.reference,
            stencilCompareMask: stencil.compareMask,
            stencilWriteMask: stencil.writeMask,
            stencilSFail: stencil.sfail,
            stencilDpFail: stencil.dpfail,
            stencilDpPass: stencil.dppass
        )
    }
}

struct PipelineKey: Hashable {
    let blendEnabled: Bool
    let blendSrcRGB: Int32
    let blendDstRGB: Int32
    let blendSrcAlpha: Int32
    let blendDstAlpha: Int32
    let blendEquationRGB: Int32
    let blendEquationAlpha: Int32
    let cullEnabled: Bool
    let cullMode: Int32
    let colorPixelFormat: Int32
    let sampleCount: Int32
    let primitiveType: Int32

    var stableHash: UInt64 {
        var seed: UInt64 = 0xCBF29CE484222325
        seed = mixStableHash(seed, blendEnabled ? 1 : 0)
        seed = mixStableHash(seed, Int64(blendSrcRGB))
        seed = mixStableHash(seed, Int64(blendDstRGB))
        seed = mixStableHash(seed, Int64(blendSrcAlpha))
        seed = mixStableHash(seed, Int64(blendDstAlpha))
        seed = mixStableHash(seed, Int64(blendEquationRGB))
        seed = mixStableHash(seed, Int64(blendEquationAlpha))
        seed = mixStableHash(seed, cullEnabled ? 1 : 0)
        seed = mixStableHash(seed, Int64(cullMode))
        seed = mixStableHash(seed, Int64(colorPixelFormat))
        seed = mixStableHash(seed, Int64(sampleCount))
        seed = mixStableHash(seed, Int64(primitiveType))
        return seed
    }
}

struct DepthStencilKey: Hashable {
    let depthTestEnabled: Bool
    let depthWriteEnabled: Bool
    let depthCompareFunction: Int32
    let stencilEnabled: Bool
    let stencilFunction: Int32
    let stencilReference: Int32
    let stencilCompareMask: Int32
    let stencilWriteMask: Int32
    let stencilSFail: Int32
    let stencilDpFail: Int32
    let stencilDpPass: Int32

    var stableHash: UInt64 {
        var seed: UInt64 = 0x84222325CBF29CE4
        seed = mixStableHash(seed, depthTestEnabled ? 1 : 0)
        seed = mixStableHash(seed, depthWriteEnabled ? 1 : 0)
        seed = mixStableHash(seed, Int64(depthCompareFunction))
        seed = mixStableHash(seed, stencilEnabled ? 1 : 0)
        seed = mixStableHash(seed, Int64(stencilFunction))
        seed = mixStableHash(seed, Int64(stencilReference))
        seed = mixStableHash(seed, Int64(stencilCompareMask))
        seed = mixStableHash(seed, Int64(stencilWriteMask))
        seed = mixStableHash(seed, Int64(stencilSFail))
        seed = mixStableHash(seed, Int64(stencilDpFail))
        seed = mixStableHash(seed, Int64(stencilDpPass))
        return seed
    }
}

private func mixStableHash(_ seed: UInt64, _ value: Int64) -> UInt64 {
    let prime: UInt64 = 0x100000001B3
    let valueBits = UInt64(bitPattern: value)
    return (seed ^ valueBits) &* prime
}

final class RenderStateTracker {
    private(set) var snapshot = RenderStateSnapshot()
    private(set) var revision: UInt64 = 0

    @discardableResult
    func setBlendEnabled(_ enabled: Bool) -> Bool {
        return updateSnapshot { next in
            next.blend.enabled = enabled
        }
    }

    @discardableResult
    func setBlendFunc(srcRGB: Int32, dstRGB: Int32, srcAlpha: Int32, dstAlpha: Int32) -> Bool {
        return updateSnapshot { next in
            next.blend.srcRGB = srcRGB
            next.blend.dstRGB = dstRGB
            next.blend.srcAlpha = srcAlpha
            next.blend.dstAlpha = dstAlpha
        }
    }

    @discardableResult
    func setBlendEquation(rgb: Int32, alpha: Int32) -> Bool {
        return updateSnapshot { next in
            next.blend.equationRGB = rgb
            next.blend.equationAlpha = alpha
        }
    }

    @discardableResult
    func setDepthState(testEnabled: Bool, writeEnabled: Bool, compareFunction: Int32) -> Bool {
        return updateSnapshot { next in
            next.depth.testEnabled = testEnabled
            next.depth.writeEnabled = writeEnabled
            next.depth.compareFunction = compareFunction
        }
    }

    @discardableResult
    func setStencilState(
        enabled: Bool,
        function: Int32,
        reference: Int32,
        compareMask: Int32,
        writeMask: Int32,
        sfail: Int32,
        dpfail: Int32,
        dppass: Int32
    ) -> Bool {
        return updateSnapshot { next in
            next.stencil.enabled = enabled
            next.stencil.function = function
            next.stencil.reference = reference
            next.stencil.compareMask = compareMask
            next.stencil.writeMask = writeMask
            next.stencil.sfail = sfail
            next.stencil.dpfail = dpfail
            next.stencil.dppass = dppass
        }
    }

    @discardableResult
    func setCullState(enabled: Bool, mode: Int32) -> Bool {
        return updateSnapshot { next in
            next.raster.cullEnabled = enabled
            next.raster.cullMode = mode
        }
    }

    @discardableResult
    func setScissor(enabled: Bool, x: Int32, y: Int32, width: Int32, height: Int32) -> Bool {
        return updateSnapshot { next in
            next.scissor.enabled = enabled
            next.scissor.x = x
            next.scissor.y = y
            next.scissor.width = max(width, 1)
            next.scissor.height = max(height, 1)
        }
    }

    @discardableResult
    func setViewport(x: Int32, y: Int32, width: Int32, height: Int32, minDepth: Float, maxDepth: Float) -> Bool {
        return updateSnapshot { next in
            next.viewport.x = x
            next.viewport.y = y
            next.viewport.width = max(width, 1)
            next.viewport.height = max(height, 1)
            next.viewport.minDepth = min(max(minDepth, 0.0), 1.0)
            next.viewport.maxDepth = min(max(maxDepth, 0.0), 1.0)
        }
    }

    func makePipelineKey(colorPixelFormat: Int32, sampleCount: Int32, primitiveType: Int32) -> PipelineKey {
        return snapshot.makePipelineKey(
            colorPixelFormat: colorPixelFormat,
            sampleCount: sampleCount,
            primitiveType: primitiveType
        )
    }

    func makeDepthStencilKey() -> DepthStencilKey {
        return snapshot.makeDepthStencilKey()
    }

    private func updateSnapshot(_ mutate: (inout RenderStateSnapshot) -> Void) -> Bool {
        var next = snapshot
        mutate(&next)
        if next == snapshot {
            return false
        }
        snapshot = next
        revision &+= 1
        return true
    }
}
