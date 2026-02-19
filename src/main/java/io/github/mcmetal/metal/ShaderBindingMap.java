package io.github.mcmetal.metal;

import java.util.List;

/**
 * Reflection output describing shader resource bindings.
 */
public record ShaderBindingMap(
    List<UniformBinding> uniforms,
    List<TextureBinding> textures,
    List<SamplerBinding> samplers
) {
    public record UniformBinding(String name, int set, int binding) {
    }

    public record TextureBinding(String name, int set, int binding) {
    }

    public record SamplerBinding(String name, int set, int binding) {
    }
}
