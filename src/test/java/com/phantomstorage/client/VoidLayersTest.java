package com.phantomstorage.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks {@link VoidLayers#layer} against the End portal shader run literally: GLSL's column-major
 * {@code mat4(...)} constructors, its row-vector {@code vec4 * mat4}, and {@code textureProj}'s divide.
 */
class VoidLayersTest {
    @ParameterizedTest
    @CsvSource({
            "1, 0.0, 0.5, 0.5, 1.0",
            "1, 0.37, 0.1, 0.9, 2.5",
            "4, 0.81, 0.73, 0.2, 0.4",
            "9, 0.05, 0.0, 1.0, 1.0",
            "15, 0.99, 0.33, 0.66, 7.0",
            "15, 0.5, 1.0, 0.0, 0.06",
    })
    void matchesTheShader(int layer, float gameTime, float x, float y, float w) {
        // texProj0 for a fragment at screen point (x, y) with clip w: xy are scaled by w, as the vertex shader leaves them.
        double[] texProj0 = {x * w, y * w, 0.3, w};
        double[] p = rowTimes(texProj0, endPortalLayer(layer, gameTime));
        float[] t = new float[6];
        VoidLayers.layer(layer, gameTime, t);
        assertEquals(p[0] / p[3], t[0] * x + t[1] * y + t[2], 1e-3);
        assertEquals(p[1] / p[3], t[3] * x + t[4] * y + t[5], 1e-3);
    }

    @ParameterizedTest
    @CsvSource({"0, 1.0, 0xFF06191C", "15, 1.0, 0xFF1550A9", "15, 0.0, 0xFF000000"})
    void colors(int index, float factor, String argb) {
        assertEquals(Integer.parseUnsignedInt(argb.substring(2), 16), VoidLayers.color(index, factor));
    }

    /** The shader's end_portal_layer, as GLSL builds it. */
    private static double[][] endPortalLayer(int layer, double gameTime) {
        double[][] translate = glMat4(
                1, 0, 0, 17.0 / layer,
                0, 1, 0, (2.0 + layer / 1.5) * (gameTime * 1.5),
                0, 0, 1, 0,
                0, 0, 0, 1);
        double a = Math.toRadians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
        // mat2_rotate_z: mat2(cos, -sin, sin, cos), column-major; mat2(s) is s on the diagonal.
        double[][] rotate = {{Math.cos(a), Math.sin(a)}, {-Math.sin(a), Math.cos(a)}};
        double s = (4.5 - layer / 4.0) * 2.0;
        double[][] scaleRotate = new double[4][4];
        for (int r = 0; r < 4; r++) {
            scaleRotate[r][r] = 1.0;
        }
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                scaleRotate[r][c] = s * rotate[r][c];
            }
        }
        double[][] scaleTranslate = glMat4(
                0.5, 0, 0, 0.25,
                0, 0.5, 0, 0.25,
                0, 0, 1, 0,
                0, 0, 0, 1);
        return times(times(scaleRotate, translate), scaleTranslate);
    }

    /** GLSL's mat4(16 values): column by column, returned as [row][column]. */
    private static double[][] glMat4(double... v) {
        double[][] m = new double[4][4];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                m[r][c] = v[c * 4 + r];
            }
        }
        return m;
    }

    private static double[][] times(double[][] a, double[][] b) {
        double[][] m = new double[4][4];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                for (int k = 0; k < 4; k++) {
                    m[r][c] += a[r][k] * b[k][c];
                }
            }
        }
        return m;
    }

    /** GLSL's vec4 * mat4: the vector as a row. */
    private static double[] rowTimes(double[] v, double[][] m) {
        double[] out = new double[4];
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 4; k++) {
                out[c] += v[k] * m[k][c];
            }
        }
        return out;
    }
}
