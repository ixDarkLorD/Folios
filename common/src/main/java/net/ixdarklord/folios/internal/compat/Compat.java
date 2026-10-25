package net.ixdarklord.folios.internal.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

// What later versions have and 1.20.1 doesn't, small enough to be one method each.
public final class Compat {
    private Compat() {}

    /** Codec.listOf(min, max): a list of at least {@code min} and at most {@code max} elements. */
    public static <T> Codec<List<T>> listOf(Codec<T> codec, int min, int max) {
        return codec.listOf().flatXmap(list -> sized(list, min, max), list -> sized(list, min, max));
    }

    /** Codec.withAlternative: the first codec, or when it can't read a value, the second. */
    public static <T> Codec<T> withAlternative(Codec<T> codec, Codec<? extends T> alternative) {
        return Codec.either(codec, alternative).xmap(either -> either.map(value -> value, value -> value), com.mojang.datafixers.util.Either::left);
    }

    private static <T> DataResult<List<T>> sized(List<T> list, int min, int max) {
        if (list.size() < min) return DataResult.error(() -> "List is too short: " + list.size() + ", expected at least " + min);
        if (list.size() > max) return DataResult.error(() -> "List is too long: " + list.size() + ", expected at most " + max);
        return DataResult.success(list);
    }

    /** PoseStack.mulPose(Matrix4f): the matrix, its rotation turning the normals too. */
    public static void mulPose(PoseStack pose, Matrix4f matrix) {
        PoseStack.Pose last = pose.last();
        last.pose().mul(matrix);
        last.normal().mul(new Matrix3f(matrix).normal());
    }

    /**
     * PoseStack.scale as later versions do it. 1.20.1's takes a cube root by a trick that breaks on a negative
     * product, so mirroring one axis (y, to turn page pixels upright) left the normals garbage and every face lit
     * at the ambient floor.
     */
    public static void scale(PoseStack pose, float x, float y, float z) {
        PoseStack.Pose last = pose.last();
        last.pose().scale(x, y, z);
        if (Math.abs(x) == Math.abs(y) && Math.abs(y) == Math.abs(z)) {
            if (x < 0 || y < 0 || z < 0) last.normal().scale(Math.signum(x), Math.signum(y), Math.signum(z));
            return;
        }
        float size = (float) Math.cbrt(Math.abs(x * y * z));
        last.normal().scale(size / x, size / y, size / z);
    }

    /** PoseStack.Pose.transformNormal: a direction turned by the pose, normalized. */
    public static Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, Vector3f out) {
        return pose.normal().transform(out.set(x, y, z)).normalize();
    }

    /** PoseStack.Pose.copy: a pose of its own, holding the same matrices. */
    public static PoseStack.Pose copy(PoseStack.Pose pose) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose.pose());
        stack.last().normal().set(pose.normal());
        return stack.last();
    }
}
