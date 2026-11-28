package dev.vitrail.render.storage;

import dev.vitrail.mixin.access.CommandEncoderAccessor;
import dev.vitrail.mixin.access.VulkanCommandEncoderAccessor;
import dev.vitrail.render.GraphicsApi;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

/**
 * Recording helpers for storage work that cannot legally sit inside a dynamic render pass.
 */
public final class GpuRecording {

	private GpuRecording() {
	}

	/**
	 * Ends the encoder's open pass, if any, so a clear, fill or dispatch can be recorded.
	 * <p>
	 * The level's pass first, through its own suspend: on 26.3 the level draws through one
	 * {@code LevelPass}, and a backend pass ended here behind its back leaves it holding a pass the
	 * backend no longer has. Its next suspend, the first clear after the targets are sized again
	 * on a window resize, then submits a pass that was never started, and the family drawing at
	 * that instant stops for the session. Suspended this way, the level's next draw opens its pass
	 * again as after any other gap. On 26.2 there is no such pass and the call does nothing.
	 */
	public static void endPass(CommandEncoder encoder) {
		GraphicsApi.suspendLevelPass();
		CommandEncoderBackend backend = ((CommandEncoderAccessor) encoder).vitrail$backend();
		if (!(backend instanceof VulkanCommandEncoder vulkan)) {
			return;
		}

		if (((VulkanCommandEncoderAccessor) vulkan).vitrail$currentRenderPass() != null) {
			vulkan.submitRenderPass();
		}
	}

	/**
	 * Runs a destruction only once the GPU is done with the frames that may still reference the
	 * resource, through the game's own two-deep queue. Destroying inline is the shape this
	 * backend cannot forgive: it records continuously, up to two submissions are in flight, and
	 * a handle freed under one of them is a device loss with a stack that names nobody. When no
	 * Vulkan encoder is there to queue on, the device is gone and took every handle with it, so
	 * the destruction is dropped rather than run against a device that no longer exists.
	 */
	public static void destroyLater(Runnable destruction) {
		GpuDevice device = RenderSystem.tryGetDevice();
		CommandEncoderBackend backend = device == null
				? null
				: ((CommandEncoderAccessor) device.createCommandEncoder()).vitrail$backend();
		if (backend instanceof VulkanCommandEncoder vulkan) {
			vulkan.queueForDestroy(destruction::run);
		}
	}

	/**
	 * Makes what shaders and transfers did to an image before this point visible to a transfer
	 * about to rewrite it or read it. Without it the clear races the previous frame's sampled reads
	 * and the previous dispatch's stores on the same volume.
	 * <p>
	 * The read half is not spare. A volume moved onto this frame's anchor is COPIED OUT of first,
	 * and what it is copied out of is exactly the previous frame's shadow geometry stores.
	 */
	static void beforeTransfer(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
				| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
				| VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
				| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
				| VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
				| VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT
				| VK13.VK_ACCESS_2_TRANSFER_READ_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}

	/**
	 * Makes a {@code vkCmdClearColorImage} / {@code vkCmdFillBuffer} visible to shaders. The
	 * game's compute-to-compute barrier does not wait for transfer writes; leaving it as the
	 * only fence after a 3D clear is how the GPU died two seconds later (Windows TDR).
	 */
	static void afterTransfer(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT
				| VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
				| VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
				| VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}

	/**
	 * Orders one copy against the next, which neither of the two above does: they carry a transfer
	 * on one side only, and a volume copied out and back reads on the transfer side what the
	 * transfer side has just written.
	 */
	static void betweenTransfers(VkCommandBuffer commands, MemoryStack stack) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.srcAccessMask(VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		barrier.dstStageMask(VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT);
		barrier.dstAccessMask(VK13.VK_ACCESS_2_TRANSFER_READ_BIT
				| VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT);
		VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
		dependency.pMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commands, dependency);
	}
}
