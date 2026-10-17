package dev.vitrail.glsl;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkFadeShaderTest {
	@Test
	void displacesSectionLocalPositionBeforeRegionTranslation() {
		String source = "#version 460\n// ofLocal = fake;\nvoid ofSodiumVertex() {\n"
				+ "vec3 ofLocal = decodePosition();\nof_Vertex = vec4(ofLocal + of_RegionOffset, 1.0);\n}\n";
		String patched = ChunkFadeShader.vertex(source, "uniform samplerBuffer cfi_u_FadeData;", "ofLocal += chunkFadeData.xyz;");
		assertTrue(patched.indexOf("uniform samplerBuffer") > patched.indexOf("#version"));
		assertTrue(patched.indexOf("ofLocal += chunkFadeData.xyz;") > patched.indexOf("decodePosition();"));
		assertTrue(patched.indexOf("ofLocal += chunkFadeData.xyz;") < patched.indexOf("of_Vertex ="));
	}

	@Test
	void fadesAfterAnEarlyReturnAndKeepsTheTranslatedAlphaTest() {
		String source = "#version 460\nvoid main() { if (flag) return; if (alpha < 0.1) discard; }\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "applyFade();");
		assertTrue(patched.contains("#define main ofCfiMain"));
		assertTrue(patched.contains("if (alpha < 0.1) discard;"));
		assertTrue(patched.contains("ofCfiMain();\napplyFade();"));
	}

	@Test
	void refusesAnUnknownPrologueInsteadOfSilentlyClaimingSupport() {
		assertThrows(IllegalArgumentException.class, () -> ChunkFadeShader.vertex("void main() {}", "", "init();"));
	}
	@Test
	void wrapsBothConditionalEntryPointsAndTheirPrototype() {
		String source = "#version 460\nvoid main();\n#if MODE\nvoid main() {}\n#else\nvoid main() {}\n#endif\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "applyFade();");
		assertTrue(patched.indexOf("#define main ofCfiMain") < patched.indexOf("void main();"));
		assertTrue(patched.indexOf("#undef main") > patched.indexOf("#endif"));
		assertEquals("ofFragData0", ChunkFadeShader.firstColour("layout(location = 0) out vec4 ofFragData0;"));
	}

	@Test
	void preservesExtensionsBeforeGeneratedDeclarations() {
		String source = "#version 460\n#extension GL_EXT_shader_explicit_arithmetic_types_float16 : require\n"
				+ "layout(location = 0) out vec4 colour;\nvoid main() { colour = vec4(float16_t(1.0)); }\n";
		String patched = ChunkFadeShader.fragment(source, "flat in float cfi_FadeFactor;", "colour.rgb *= cfi_FadeFactor;");
		assertTrue(patched.indexOf("#extension") < patched.indexOf("flat in float"));
		assertTrue(patched.indexOf(": require\n") < patched.indexOf("flat in float"));
	}

}
