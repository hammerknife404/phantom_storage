package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Render types for drawing a translucent model as one clean shell: first {@link #depthOnly} lays down
 * the nearest surface's depth without any colour, then {@link #ghost} draws translucent colour that
 * only passes where it is that nearest surface. Without the first pass the faces behind (the chest
 * body's top under the lid, the back walls) show through at low opacity.
 *
 * <p>Both are unsorted, so quads draw in submission order; and as different render types the depth
 * batch is flushed before the ghost batch starts. Extends {@link RenderType} only to reach its
 * protected state shards; it is never instantiated.
 */
public final class PhantomRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> DEPTH_ONLY = Util.memoize(texture -> create(
            "phantomstorage_depth_only", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(DEPTH_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> GHOST = Util.memoize(texture -> create(
            "phantomstorage_ghost", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private PhantomRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** Writes depth only; draw the model with this first, at full alpha. */
    public static RenderType depthOnly(ResourceLocation texture) {
        return DEPTH_ONLY.apply(texture);
    }

    /** Translucent, back faces culled, unsorted; draw the same model with this second. */
    public static RenderType ghost(ResourceLocation texture) {
        return GHOST.apply(texture);
    }
}
