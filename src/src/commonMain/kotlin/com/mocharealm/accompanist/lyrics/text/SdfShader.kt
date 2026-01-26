package com.mocharealm.accompanist.lyrics.text

/**
 * AGSL (Android Graphics Shading Language) shader for SDF text rendering.
 * 
 * This shader takes an SDF texture (stored in alpha channel) and renders
 * crisp text at any scale using signed distance field techniques.
 */
object SdfShader {
    
    /**
     * AGSL shader source for SDF text rendering.
     * 
     * Uniforms:
     * - sdfTexture: The SDF atlas texture (SDF value in alpha channel)
     * - textColor: The color to render the text in (vec4)
     * - smoothing: The smoothing factor for anti-aliasing (typically 0.1-0.25)
     * - threshold: The SDF threshold for edge detection (typically 0.5)
     */
    const val SDF_SHADER_SRC = """
        uniform shader sdfTexture;
        uniform float4 textColor;
        uniform float smoothing;
        uniform float threshold;
        
        // SDF text rendering
        // The SDF value is stored in the alpha channel
        // Values > 0.5 are inside the glyph, < 0.5 are outside
        half4 main(float2 fragCoord) {
            // Sample the SDF texture
            half4 sample = sdfTexture.eval(fragCoord);
            
            // Get the SDF value from alpha channel (0-1 range)
            float sdfValue = sample.a;
            
            // Apply smoothstep for anti-aliasing
            // threshold is typically 0.5 (the edge)
            // smoothing controls the anti-aliasing width
            float alpha = smoothstep(threshold - smoothing, threshold + smoothing, sdfValue);
            
            // Return the text color with computed alpha
            return half4(textColor.rgb * alpha, alpha);
        }
    """
    
    /**
     * AGSL shader source for SDF text with outline effect.
     * 
     * Additional uniforms:
     * - outlineColor: The color for the outline
     * - outlineWidth: Width of the outline in SDF units (0.0-0.2)
     */
    const val SDF_OUTLINE_SHADER_SRC = """
        uniform shader sdfTexture;
        uniform float4 textColor;
        uniform float4 outlineColor;
        uniform float smoothing;
        uniform float threshold;
        uniform float outlineWidth;
        
        half4 main(float2 fragCoord) {
            half4 sample = sdfTexture.eval(fragCoord);
            float sdfValue = sample.a;
            
            // Fill alpha (inside the glyph)
            float fillAlpha = smoothstep(threshold - smoothing, threshold + smoothing, sdfValue);
            
            // Outline alpha (slightly outside the glyph edge)
            float outlineThreshold = threshold - outlineWidth;
            float outlineAlpha = smoothstep(outlineThreshold - smoothing, outlineThreshold + smoothing, sdfValue);
            
            // Combine: outline where there's no fill
            float3 color = mix(outlineColor.rgb, textColor.rgb, fillAlpha);
            float alpha = outlineAlpha;
            
            return half4(color * alpha, alpha);
        }
    """
    
    /**
     * AGSL shader for SDF with drop shadow.
     * 
     * Additional uniforms:
     * - shadowColor: The shadow color
     * - shadowOffset: The shadow offset in pixels (vec2)
     * - shadowSoftness: How soft the shadow is (0.0-1.0)
     */
    const val SDF_SHADOW_SHADER_SRC = """
        uniform shader sdfTexture;
        uniform float4 textColor;
        uniform float4 shadowColor;
        uniform float2 shadowOffset;
        uniform float smoothing;
        uniform float threshold;
        uniform float shadowSoftness;
        
        half4 main(float2 fragCoord) {
            // Sample main text
            half4 mainSample = sdfTexture.eval(fragCoord);
            float mainSdf = mainSample.a;
            float textAlpha = smoothstep(threshold - smoothing, threshold + smoothing, mainSdf);
            
            // Sample shadow (offset position)
            half4 shadowSample = sdfTexture.eval(fragCoord - shadowOffset);
            float shadowSdf = shadowSample.a;
            float shadowSmoothing = smoothing + shadowSoftness;
            float shadowAlpha = smoothstep(threshold - shadowSmoothing, threshold + shadowSmoothing, shadowSdf);
            
            // Combine: shadow behind text
            float3 color = mix(shadowColor.rgb, textColor.rgb, textAlpha);
            float alpha = max(shadowAlpha * shadowColor.a, textAlpha);
            
            return half4(color * alpha, alpha);
        }
    """
}
