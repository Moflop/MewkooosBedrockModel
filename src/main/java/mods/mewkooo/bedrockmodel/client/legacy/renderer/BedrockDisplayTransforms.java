package mods.mewkooo.bedrockmodel.client.legacy.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.mewkooo.bedrockmodel.api.json.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.api.json.ItemDisplayTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class BedrockDisplayTransforms {
	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

	private BedrockDisplayTransforms() {}

	/**
	 * Applies the context's display transform to the current pose, using defaults
	 * when the context has no overrides.
	 *
	 * @param defaults fallback translation, rotation and scale for this context
	 *
	 * GUI fitting and model-to-item alignment are handled by the caller.
	 */
	public static void applyDisplayTransform(
			PoseStack poseStack,
			ItemDisplayTransforms transforms,
			ItemDisplayContext context,
			ItemDisplayTransform defaults
	) {
		ItemDisplayTransform transform = transforms.getTransform(context);
		if (transform == null) {
			transform = defaults;
		}

		Vector3f translation = transform.getTranslation();
		Vector3f rotation = transform.getRotation();
		Vector3f scale = transform.getScale();

		if (translation == null) translation = defaults.getTranslation();
		if (rotation == null) rotation = defaults.getRotation();
		if (scale == null) scale = defaults.getScale();

		if (translation == null || rotation == null || scale == null) {
			throw new IllegalArgumentException(
					"Display defaults must provide translation, rotation and scale");
		}

		boolean leftHand =
				context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
						|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		float hand = leftHand ? -1.0f : 1.0f;

		Quaternionf quaternion = new Quaternionf().rotationXYZ(
				rotation.x * DEG_TO_RAD,
				hand * rotation.y * DEG_TO_RAD,
				hand * rotation.z * DEG_TO_RAD
		);

		Vector3f rotationPivot = transform.getRotationPivot();
		Vector3f scalePivot = transform.getScalePivot();

		Vector3f offset = new Vector3f(
				hand * translation.x, translation.y, translation.z)
				.div(16.0f)
				.add(rotationPivot)
				.sub(new Vector3f(rotationPivot).rotate(quaternion))
				.add(scalePivot.rotate(quaternion)
						.mul(1 - scale.x, 1 - scale.y, 1 - scale.z));

		poseStack.translate(offset.x, offset.y, offset.z);
		poseStack.mulPose(quaternion);
		poseStack.scale(scale.x, scale.y, scale.z);
	}
}
