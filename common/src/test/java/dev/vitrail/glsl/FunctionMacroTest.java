package dev.vitrail.glsl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.pack.source.ExpansionStats;
import dev.vitrail.pack.source.IncludeExpander.ExpandedUnit;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FunctionMacroTest {

	private static ExpandedUnit unitOf(String code) {
		List<String> lines = code.lines().toList();
		BitSet live = new BitSet();
		live.set(0, lines.size(), true);
		return new ExpandedUnit("composite14.fsh", lines, "400", ExpansionStats.NONE, live, Map.of());
	}

	@Test
	void doesNotRenameTextureInFunctionAliasMacro() {
		String code = """
				#version 400
				  #define BLOOM_UPSAMPLING_FILTER texture // [texture bicubic_filter]
				uniform sampler2D colortex0;
				layout(location = 0) out vec4 outColor;
				in vec2 uv;
				void main() {
					outColor = BLOOM_UPSAMPLING_FILTER(colortex0, uv);
				}
				""";

		TranslatedUnit translated = GlslTranslator.translate(unitOf(code), ProgramStage.FRAGMENT, "composite14");
		String text = translated.text();

		assertTrue(text.contains("BLOOM_UPSAMPLING_FILTER texture"),
				"Function alias macro must preserve 'texture', got: " + text);
		assertFalse(text.contains("ofTexture"),
				"Must not emit 'ofTexture' when texture is not declared as a variable, got: " + text);
	}

	@Test
	void doesNotRenameTextureInUncalledAliasWithoutDeclaredSampler() {
		String code = """
				#version 400
				#define sample_depth_texture texture
				uniform sampler2D depthtex0;
				layout(location = 0) out vec4 outColor;
				void main() {
					outColor = vec4(1.0);
				}
				""";

		TranslatedUnit translated = GlslTranslator.translate(unitOf(code), ProgramStage.FRAGMENT, "composite14");
		String text = translated.text();

		assertTrue(text.contains("#define sample_depth_texture texture"),
				"Uncalled alias macro without declared sampler must preserve 'texture', got: " + text);
		assertFalse(text.contains("ofTexture"),
				"Must not emit 'ofTexture' when texture is not declared as a variable, got: " + text);
	}

	@Test
	void renamesTextureInMacroWhenDeclaredAsSampler() {
		String code = """
				#version 120
				uniform sampler2D texture;
				#define ALBEDO_TEXTURE_CALL_PREFIX textureGrad(texture,
				void main() {
					gl_FragColor = ALBEDO_TEXTURE_CALL_PREFIX gl_TexCoord[0].st, vec2(0.0), vec2(0.0));
				}
				""";

		TranslatedUnit translated = GlslTranslator.translate(unitOf(code), ProgramStage.FRAGMENT, "gbuffers_terrain");
		String text = translated.text();

		assertTrue(text.contains("ofTexture"),
				"Declared sampler 'texture' in Pegasus-style macro must be renamed to ofTexture, got: " + text);
	}
}

